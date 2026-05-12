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

#[derive(Default)]
pub struct KotoCryptoState {
    inner: RwLock<Option<Arc<KotoCrypto>>>,
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
        .clone()
        .ok_or_else(|| KotoApiError {
            status: 0,
            message: "Криптосессия не инициализирована — войдите в аккаунт.".to_string(),
        })
}

fn init_store_from_bundle(
    crypto: &Arc<KotoCrypto>,
    bundle: RegistrationBundle,
) -> Result<(), KotoApiError> {
    let RegistrationBundle {
        prekeys,
        signed_prekey,
        kyber_prekey,
        ..
    } = bundle;
    crypto
        .clone()
        .load_prekeys(prekeys)
        .map_err(crypto_to_api)?;
    crypto
        .clone()
        .load_signed_prekeys(vec![signed_prekey])
        .map_err(crypto_to_api)?;
    crypto
        .clone()
        .load_kyber_prekeys(vec![kyber_prekey])
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
) -> Result<(), KotoApiError> {
    let bundle = generate_registration_bundle_from_seed(seed_phrase, registration_id)
        .map_err(|e| seed_to_api(e.to_string()))?;
    let identity_bytes = bundle.identity_key_pair.clone();
    let reg_id = bundle.registration_id;
    let crypto = KotoCrypto::new(identity_bytes, reg_id, account_id).map_err(crypto_to_api)?;
    init_store_from_bundle(&crypto, bundle)?;
    *state.inner.write().await = Some(crypto);
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
struct PrekeyBundleResponse {
    #[serde(default)]
    registration_id: u32,
    #[serde(default)]
    device_id: u32,
    identity_key: String,
    signed_prekey_id: u32,
    signed_prekey_public: String,
    signed_prekey_sig: String,
    #[serde(default)]
    one_time_prekey_id: Option<u32>,
    #[serde(default)]
    one_time_prekey_public: Option<String>,
    kyber_prekey_id: u32,
    kyber_prekey_public: String,
    kyber_prekey_sig: String,
}

fn b64_decode(s: &str, field: &str) -> Result<Vec<u8>, KotoApiError> {
    B64.decode(s).map_err(|e| KotoApiError {
        status: 0,
        message: format!("Ошибка декодирования {field}: {e}"),
    })
}

/// Тянет prekey bundle peer'а с gateway, разбирает и устанавливает сессию.
///
/// Идемпотентно: повторный вызов с тем же peer'ом просто пере-инициализирует
/// сессию (libsignal обработает корректно).
#[tauri::command]
pub async fn crypto_ensure_peer_session(
    state: tauri::State<'_, KotoCryptoState>,
    access_token: String,
    peer_id: String,
) -> Result<(), KotoApiError> {
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

    let prekey_id = body.one_time_prekey_id;
    let prekey_pub = match body.one_time_prekey_public.as_ref() {
        Some(s) if !s.is_empty() => Some(b64_decode(s, "one_time_prekey_public")?),
        _ => None,
    };

    let bundle = PreKeyBundleInput {
        registration_id: body.registration_id,
        device_id: if body.device_id == 0 { 1 } else { body.device_id },
        identity_key: b64_decode(&body.identity_key, "identity_key")?,
        signed_prekey_id: body.signed_prekey_id,
        signed_prekey_public: b64_decode(&body.signed_prekey_public, "signed_prekey_public")?,
        signed_prekey_sig: b64_decode(&body.signed_prekey_sig, "signed_prekey_sig")?,
        prekey_id,
        prekey_public: prekey_pub,
        kyber_prekey_id: body.kyber_prekey_id,
        kyber_prekey_public: b64_decode(&body.kyber_prekey_public, "kyber_prekey_public")?,
        kyber_prekey_sig: b64_decode(&body.kyber_prekey_sig, "kyber_prekey_sig")?,
    };

    crypto
        .clone()
        .process_prekey_bundle(peer_id, bundle)
        .map_err(crypto_to_api)?;
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
    let bytes = crypto
        .encrypt(peer_id, plaintext.into_bytes())
        .map_err(crypto_to_api)?;
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
    let plain = crypto
        .decrypt(peer_id, bytes)
        .map_err(crypto_to_api)?;
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
