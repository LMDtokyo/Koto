//! Криптография для одного локального аккаунта.
//!
//! Состояние Signal Protocol хранится в `Arc<KotoCrypto>` внутри `tauri::State`:
//! - `crypto_init_session` создаёт его при login/restore (по seed + reg_id).
//! - `crypto_ensure_peer_session` лениво подгружает prekey bundle peer'а с
//!   gateway и устанавливает X3DH+Kyber-сессию.
//! - `crypto_encrypt` / `crypto_decrypt` шифруют/расшифровывают сообщения.
//!
//! ВАЖНО: prekeys регенерируются при каждом init и публикуются заново через
//! существующий register/restore flow. Persistent prekey-store — отдельный
//! пост-MVP шаг (требует SQLite в Tauri).

use std::sync::Arc;

use base64::{engine::general_purpose::STANDARD as B64, Engine as _};
use koto_crypto::{
    generate_registration_bundle_from_seed, KotoCrypto, PreKeyBundleInput, RegistrationBundle,
};
use serde::{Deserialize, Serialize};
use tokio::sync::RwLock;

use crate::config::load_app_config;
use crate::koto_errors::{
    api_http_error, api_json_error, api_transport_error, map_reqwest, ApiContext, KotoApiError,
};

struct ActiveSession {
    account_id: String,
    crypto: Arc<KotoCrypto>,
    /// Peers, для которых уже отрабатывал process_prekey_bundle в этом
    /// Rust-процессе. На JS-стороне аналогичный Set теряется при Vite-reload,
    /// что приводило к повторному process_prekey_bundle → reset libsignal
    /// session counter у sender'а → "old counter" у receiver'а на decrypt.
    initialized_peers: std::collections::HashSet<String>,
}

#[derive(Default)]
pub struct KotoCryptoState {
    inner: RwLock<Option<ActiveSession>>,
}

impl KotoCryptoState {
    pub fn new() -> Self {
        Self {
            inner: RwLock::new(None),
        }
    }
}

fn http_client() -> Result<reqwest::Client, KotoApiError> {
    reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(30))
        .build()
        .map_err(|e| api_transport_error(map_reqwest(e)))
}

async fn current_session(
    state: &tauri::State<'_, KotoCryptoState>,
) -> Result<Arc<KotoCrypto>, KotoApiError> {
    state
        .inner
        .read()
        .await
        .as_ref()
        .map(|s| s.crypto.clone())
        .ok_or_else(|| KotoApiError {
            status: 0,
            message: "Криптосессия не инициализирована — войдите в аккаунт.".to_string(),
        })
}

async fn init_store_from_bundle(
    crypto: &Arc<KotoCrypto>,
    bundle: RegistrationBundle,
) -> Result<(), KotoApiError> {
    let RegistrationBundle {
        prekeys,
        signed_prekey,
        kyber_prekey,
        ..
    } = bundle;
    let crypto_clone = crypto.clone();
    // koto-crypto использует свой Tokio Runtime (RT.block_on) внутри
    // load_*/encrypt/decrypt/process_prekey_bundle. Если вызвать напрямую
    // из Tauri-команды (которая уже на async runtime) — паника
    // "Cannot start a runtime from within a runtime". spawn_blocking
    // переносит работу на отдельный поток без tokio-контекста.
    tokio::task::spawn_blocking(move || {
        crypto_clone.clone().load_prekeys(prekeys)?;
        crypto_clone
            .clone()
            .load_signed_prekeys(vec![signed_prekey])?;
        crypto_clone
            .clone()
            .load_kyber_prekeys(vec![kyber_prekey])?;
        Ok::<(), koto_crypto::CryptoError>(())
    })
    .await
    .map_err(|e| KotoApiError {
        status: 0,
        message: format!("crypto join error: {e}"),
    })?
    .map_err(crypto_to_api)?;
    Ok(())
}

fn crypto_to_api(e: koto_crypto::CryptoError) -> KotoApiError {
    KotoApiError {
        status: 0,
        message: format!("Ошибка криптографии: {e}"),
    }
}

fn seed_to_api(e: String) -> KotoApiError {
    KotoApiError {
        status: 0,
        message: format!("Не удалось обработать секретную фразу: {e}"),
    }
}

/// Инициализирует криптосессию для текущего аккаунта.
///
/// Сегодня вызывается при auth_register_finish / auth_provision_from_seed
/// (на стороне TS), а также при холодном старте, если seed_phrase сохранён
/// локально — тогда prekeys генерируются заново и публикуются повторно.
#[tauri::command]
pub async fn crypto_init_session(
    state: tauri::State<'_, KotoCryptoState>,
    seed_phrase: Vec<String>,
    registration_id: u32,
    account_id: String,
    access_token: Option<String>,
) -> Result<(), KotoApiError> {
    // Идемпотентность: если для этого account_id сессия уже поднята,
    // не регенерируем prekeys — иначе на каждом page-reload (Vite HMR
    // дёргает initCryptoSession) ключи бы менялись и предыдущие сообщения
    // нельзя было бы расшифровать. Tauri-процесс держит KotoCrypto живым
    // между webview-reload'ами, поэтому проверка имеет смысл.
    {
        let guard = state.inner.read().await;
        if let Some(existing) = guard.as_ref() {
            if existing.account_id == account_id {
                eprintln!(
                    "[crypto] init_session SKIP — already initialized for {}",
                    &account_id[..16.min(account_id.len())]
                );
                return Ok(());
            }
        }
    }
    eprintln!(
        "[crypto] init_session — NEW for {}",
        &account_id[..16.min(account_id.len())]
    );

    let bundle = tokio::task::spawn_blocking(move || {
        generate_registration_bundle_from_seed(seed_phrase, registration_id)
    })
    .await
    .map_err(|e| KotoApiError {
        status: 0,
        message: format!("crypto join error: {e}"),
    })?
    .map_err(|e| seed_to_api(e.to_string()))?;
    let identity_bytes = bundle.identity_key_pair.clone();
    let reg_id = bundle.registration_id;
    let account_id_clone = account_id.clone();
    let crypto = KotoCrypto::new(identity_bytes, reg_id, account_id).map_err(crypto_to_api)?;

    // Загружаем локально И публикуем на сервер ОДНУ И ТУ ЖЕ bundle. Иначе:
    // - server имеет bundle A (uploaded в /v1/auth/register)
    // - local имеет bundle B (свежесгенерированный тут)
    // - sender фетчит A → encrypt с prekey-IDs из A
    // - recipient decrypt с store B → prekey-IDs не находятся → decrypt fails
    // Публикация перезаписывает server-bundle, выравнивая обе стороны.
    let upload_body = build_prekey_upload_body(&bundle);
    init_store_from_bundle(&crypto, bundle).await?;
    *state.inner.write().await = Some(ActiveSession {
        account_id: account_id_clone,
        crypto,
        initialized_peers: std::collections::HashSet::new(),
    });

    if let Some(token) = access_token {
        if !token.trim().is_empty() {
            // Best-effort: если upload провалился, локальная сессия остаётся
            // рабочей; следующая отправка просто покажет ошибку до перезалива.
            match upload_prekey_bundle(&token, upload_body).await {
                Ok(()) => eprintln!("[crypto] prekey-bundle uploaded to /v1/keys"),
                Err(e) => eprintln!("[crypto] prekey-bundle upload FAILED: {}", e.message),
            }
        } else {
            eprintln!("[crypto] no accessToken — skipping prekey upload");
        }
    } else {
        eprintln!("[crypto] no accessToken — skipping prekey upload");
    }
    Ok(())
}

fn build_prekey_upload_body(bundle: &RegistrationBundle) -> serde_json::Value {
    serde_json::json!({
        "identity_key": B64.encode(&bundle.identity_public_key),
        "registration_id": bundle.registration_id,
        "signed_prekey": {
            "id": bundle.signed_prekey.id,
            "public_key": B64.encode(&bundle.signed_prekey.public_key),
            "signature": B64.encode(&bundle.signed_prekey.signature),
        },
        "kyber_prekey": {
            "id": bundle.kyber_prekey.id,
            "public_key": B64.encode(&bundle.kyber_prekey.public_key),
            "signature": B64.encode(&bundle.kyber_prekey.signature),
        },
        "one_time_prekeys": bundle.prekeys.iter().map(|p| {
            serde_json::json!({
                "id": p.id,
                "public_key": B64.encode(&p.public_key),
            })
        }).collect::<Vec<_>>(),
    })
}

/// PUT /v1/keys — публикация prekey-bundle поверх существующей.
async fn upload_prekey_bundle(
    access_token: &str,
    body: serde_json::Value,
) -> Result<(), KotoApiError> {
    let cfg = load_app_config();
    let url = format!("{}/v1/keys", cfg.rest_base_url.trim_end_matches('/'));
    let client = http_client()?;

    let response = client
        .put(&url)
        .header("Authorization", format!("Bearer {}", access_token.trim()))
        .json(&body)
        .send()
        .await
        .map_err(|e| api_transport_error(map_reqwest(e)))?;
    let status = response.status();
    let text = response.text().await.unwrap_or_default();
    if !status.is_success() {
        return Err(api_http_error(
            ApiContext::UploadKeys,
            status.as_u16(),
            text.trim(),
        ));
    }
    Ok(())
}

/// Сбрасывает криптосессию (вызывается при logout).
#[tauri::command]
pub async fn crypto_clear_session(
    state: tauri::State<'_, KotoCryptoState>,
) -> Result<(), KotoApiError> {
    *state.inner.write().await = None;
    Ok(())
}

#[derive(Debug, Deserialize)]
struct SignedPrekeyDto {
    id: u32,
    public_key: String,
    signature: String,
}

#[derive(Debug, Deserialize)]
struct KyberPrekeyDto {
    id: u32,
    public_key: String,
    signature: String,
}

#[derive(Debug, Deserialize)]
struct OneTimePrekeyDto {
    id: u32,
    public_key: String,
}

/// Совпадает с ответом user-service `/v1/keys/{id}` ([user/main.go:378]).
#[derive(Debug, Deserialize)]
struct PrekeyBundleResponse {
    #[serde(default)]
    registration_id: u32,
    #[serde(default)]
    device_id: u32,
    identity_key: String,
    signed_prekey: SignedPrekeyDto,
    kyber_prekey: KyberPrekeyDto,
    #[serde(default)]
    one_time_prekey: Option<OneTimePrekeyDto>,
}

fn b64_decode(s: &str, field: &str) -> Result<Vec<u8>, KotoApiError> {
    B64.decode(s).map_err(|e| KotoApiError {
        status: 0,
        message: format!("Ошибка декодирования {field}: {e}"),
    })
}

/// Тянет prekey bundle peer'а с gateway, разбирает и устанавливает сессию.
///
/// Идемпотентно ВНУТРИ Rust-процесса: если для peer'а уже сделан
/// process_prekey_bundle (Set initialized_peers), сразу возвращаемся. Это
/// важно потому что повторный process_prekey_bundle сбрасывает счётчик
/// libsignal-сессии у sender'а → receiver получает сообщение со счётчиком 0,
/// а его собственная сессия уже на 1+ → "old counter" → decrypt fails.
#[tauri::command]
pub async fn crypto_ensure_peer_session(
    state: tauri::State<'_, KotoCryptoState>,
    access_token: String,
    peer_id: String,
) -> Result<(), KotoApiError> {
    {
        let guard = state.inner.read().await;
        if let Some(s) = guard.as_ref() {
            if s.initialized_peers.contains(&peer_id) {
                return Ok(());
            }
        }
    }
    let crypto = current_session(&state).await?;
    let cfg = load_app_config();
    let url = format!(
        "{}/v1/keys/{}",
        cfg.rest_base_url.trim_end_matches('/'),
        peer_id.trim()
    );
    let client = http_client()?;
    let response = client
        .get(&url)
        .header("Authorization", format!("Bearer {}", access_token.trim()))
        .send()
        .await
        .map_err(|e| api_transport_error(map_reqwest(e)))?;
    let status = response.status();
    let text = response.text().await.unwrap_or_default();
    if !status.is_success() {
        return Err(api_http_error(
            ApiContext::UploadKeys,
            status.as_u16(),
            text.trim(),
        ));
    }
    let body: PrekeyBundleResponse =
        serde_json::from_str(&text).map_err(|e| api_json_error(ApiContext::UploadKeys, &e))?;

    let (prekey_id, prekey_pub) = match body.one_time_prekey {
        Some(p) if !p.public_key.is_empty() => (
            Some(p.id),
            Some(b64_decode(&p.public_key, "one_time_prekey.public_key")?),
        ),
        _ => (None, None),
    };

    let bundle = PreKeyBundleInput {
        registration_id: body.registration_id,
        device_id: if body.device_id == 0 { 1 } else { body.device_id },
        identity_key: b64_decode(&body.identity_key, "identity_key")?,
        signed_prekey_id: body.signed_prekey.id,
        signed_prekey_public: b64_decode(&body.signed_prekey.public_key, "signed_prekey.public_key")?,
        signed_prekey_sig: b64_decode(&body.signed_prekey.signature, "signed_prekey.signature")?,
        prekey_id,
        prekey_public: prekey_pub,
        kyber_prekey_id: body.kyber_prekey.id,
        kyber_prekey_public: b64_decode(&body.kyber_prekey.public_key, "kyber_prekey.public_key")?,
        kyber_prekey_sig: b64_decode(&body.kyber_prekey.signature, "kyber_prekey.signature")?,
    };
    eprintln!(
        "[crypto] process_prekey_bundle peer={} reg_id={} spk_id={} kpk_id={} otpk_id={:?}",
        &peer_id[..16.min(peer_id.len())],
        bundle.registration_id,
        bundle.signed_prekey_id,
        bundle.kyber_prekey_id,
        bundle.prekey_id
    );

    let crypto_clone = crypto.clone();
    let peer_for_set = peer_id.clone();
    tokio::task::spawn_blocking(move || crypto_clone.process_prekey_bundle(peer_id, bundle))
        .await
        .map_err(|e| KotoApiError {
            status: 0,
            message: format!("crypto join error: {e}"),
        })?
        .map_err(crypto_to_api)?;
    // Запоминаем peer'а — следующий вызов crypto_ensure_peer_session с тем же
    // id будет no-op до перезапуска Rust-процесса.
    if let Some(s) = state.inner.write().await.as_mut() {
        s.initialized_peers.insert(peer_for_set);
    }
    Ok(())
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CryptoEncryptedDto {
    pub ciphertext_base64: String,
}

/// Шифрует UTF-8 текст и возвращает base64 ciphertext (включает type-byte).
#[tauri::command]
pub async fn crypto_encrypt(
    state: tauri::State<'_, KotoCryptoState>,
    peer_id: String,
    plaintext: String,
) -> Result<CryptoEncryptedDto, KotoApiError> {
    let crypto = current_session(&state).await?;
    let peer_for_log = peer_id.clone();
    let bytes =
        tokio::task::spawn_blocking(move || crypto.encrypt(peer_id, plaintext.into_bytes()))
            .await
            .map_err(|e| KotoApiError {
                status: 0,
                message: format!("crypto join error: {e}"),
            })?
            .map_err(crypto_to_api)?;
    eprintln!(
        "[crypto] encrypt → peer={} ciphertext_len={} type_byte={}",
        &peer_for_log[..16.min(peer_for_log.len())],
        bytes.len(),
        bytes.first().copied().unwrap_or(0)
    );
    Ok(CryptoEncryptedDto {
        ciphertext_base64: B64.encode(&bytes),
    })
}

/// Расшифровывает ciphertext (base64) от `peer_id`. Если первый раз для peer'а
/// и это PreKeySignalMessage — X3DH + Kyber отрабатывают автоматически
/// (на наших prekeys, загруженных при init).
#[tauri::command]
pub async fn crypto_decrypt(
    state: tauri::State<'_, KotoCryptoState>,
    peer_id: String,
    ciphertext_base64: String,
) -> Result<String, KotoApiError> {
    let crypto = current_session(&state).await?;
    let bytes = b64_decode(&ciphertext_base64, "ciphertext_base64")?;
    let peer_for_log = peer_id.clone();
    let bytes_len = bytes.len();
    let plain = tokio::task::spawn_blocking(move || crypto.decrypt(peer_id, bytes))
        .await
        .map_err(|e| KotoApiError {
            status: 0,
            message: format!("crypto join error: {e}"),
        })?
        .map_err(|e| {
            eprintln!(
                "[crypto] DECRYPT FAILED from peer {} ({} bytes): {}",
                &peer_for_log[..16.min(peer_for_log.len())],
                bytes_len,
                e
            );
            crypto_to_api(e)
        })?;
    String::from_utf8(plain).map_err(|e| KotoApiError {
        status: 0,
        message: format!("Сообщение не является валидным UTF-8: {e}"),
    })
}

/// Возвращает true, если для текущего аккаунта инициализирована криптосессия.
#[tauri::command]
pub async fn crypto_is_ready(
    state: tauri::State<'_, KotoCryptoState>,
) -> Result<bool, KotoApiError> {
    Ok(state.inner.read().await.is_some())
}
