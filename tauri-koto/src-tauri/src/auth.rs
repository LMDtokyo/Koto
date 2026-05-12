//! Регистрация и восстановление через gateway: POST /v1/auth/register|restore (hex-ключи),
//! затем PUT /v1/keys (base64 PQXDH), как в desktop `AuthRepositoryImpl`.

use base64::{engine::general_purpose::STANDARD, Engine as _};
use bip39::{Language, Mnemonic};
use koto_crypto::{
    generate_registration_bundle_from_seed, identity_public_key_from_seed, RegistrationBundle,
};
use rand::seq::SliceRandom;
use rand::RngCore;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::collections::HashSet;
use std::sync::OnceLock;
use tokio::sync::{broadcast, Mutex};

use crate::config::load_app_config;
use crate::koto_errors::{
    api_http_error, api_json_error, api_transport_error, humanize_http, humanize_json_decode,
    humanize_seed_crypto, map_reqwest, ApiContext, KotoApiError,
};

#[derive(Debug)]
enum PostRegisterFailure {
    /// Сервер: аккаунт уже есть — клиент повторит через /restore.
    AccountExists,
    Shown(String),
}

fn strip_djb(v: &[u8]) -> &[u8] {
    if v.len() == 33 && v.first() == Some(&0x05) {
        &v[1..]
    } else {
        v
    }
}

fn secure_registration_id() -> u32 {
    let mut rng = rand::thread_rng();
    rng.next_u32() & 0x7FFF_FFFF
}

fn device_headers() -> (String, String, String) {
    let platform = if cfg!(target_os = "linux") {
        "Linux"
    } else if cfg!(target_os = "windows") {
        "Windows"
    } else if cfg!(target_os = "macos") {
        "macOS"
    } else {
        "Desktop"
    };
    let hostname = std::env::var("HOSTNAME")
        .or_else(|_| std::env::var("COMPUTERNAME"))
        .ok()
        .map(|s| s.trim().to_string())
        .filter(|s| !s.is_empty() && !s.eq_ignore_ascii_case("localhost"));
    let name = hostname
        .map(|h| format!("Koto Desktop ({h})"))
        .unwrap_or_else(|| format!("Koto Desktop ({platform})"));
    let app_version = env!("CARGO_PKG_VERSION").to_string();
    (name, platform.to_string(), app_version)
}

fn http_client() -> Result<reqwest::Client, String> {
    reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(30))
        .build()
        .map_err(map_reqwest)
}

fn b64(bytes: &[u8]) -> String {
    STANDARD.encode(bytes)
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "snake_case")]
struct TokenPairRaw {
    account_id: String,
    #[serde(default)]
    session_id: String,
    access_token: String,
    refresh_token: String,
    expires_at: i64,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct TokenPairDto {
    pub account_id: String,
    pub session_id: String,
    pub access_token: String,
    pub refresh_token: String,
    pub expires_at: i64,
    /// Идентификатор регистрации, который мы отправили на сервер при register/restore.
    /// Нужен фронту, чтобы инициализировать криптосессию (в паре с seed-фразой).
    /// Для refresh-token — 0 (фронт обновляет крипто только при полном входе).
    #[serde(default)]
    pub registration_id: u32,
}

impl From<TokenPairRaw> for TokenPairDto {
    fn from(r: TokenPairRaw) -> Self {
        Self {
            account_id: r.account_id,
            session_id: r.session_id,
            access_token: r.access_token,
            refresh_token: r.refresh_token,
            expires_at: r.expires_at,
            registration_id: 0,
        }
    }
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AuthBootstrapDto {
    pub tokens: TokenPairDto,
    pub mnemonic: Vec<String>,
}

fn register_json_body(bundle: &RegistrationBundle) -> serde_json::Value {
    let otpk: Vec<String> = bundle
        .prekeys
        .iter()
        .map(|p| hex::encode(strip_djb(&p.public_key)))
        .collect();
    serde_json::json!({
        "identity_key": hex::encode(strip_djb(&bundle.identity_public_key)),
        "signed_pre_key": hex::encode(strip_djb(&bundle.signed_prekey.public_key)),
        "signed_pre_key_sig": hex::encode(&bundle.signed_prekey.signature),
        "signed_pre_key_id": bundle.signed_prekey.id as i32,
        "one_time_pre_keys": otpk,
    })
}

fn upload_keys_json(bundle: &RegistrationBundle) -> serde_json::Value {
    let otps: Vec<serde_json::Value> = bundle
        .prekeys
        .iter()
        .map(|p| {
            serde_json::json!({
                "id": p.id,
                "public_key": b64(&p.public_key),
            })
        })
        .collect();
    serde_json::json!({
        "identity_key": b64(&bundle.identity_public_key),
        "registration_id": bundle.registration_id,
        "signed_prekey": {
            "id": bundle.signed_prekey.id,
            "public_key": b64(&bundle.signed_prekey.public_key),
            "signature": b64(&bundle.signed_prekey.signature),
        },
        "kyber_prekey": {
            "id": bundle.kyber_prekey.id,
            "public_key": b64(&bundle.kyber_prekey.public_key),
            "signature": b64(&bundle.kyber_prekey.signature),
        },
        "one_time_prekeys": otps,
    })
}

async fn post_register_or_restore(
    client: &reqwest::Client,
    rest_base: &str,
    body: serde_json::Value,
    restore: bool,
) -> Result<TokenPairDto, PostRegisterFailure> {
    let path = if restore {
        "/v1/auth/restore"
    } else {
        "/v1/auth/register"
    };
    let url = format!("{}{}", rest_base.trim_end_matches('/'), path);
    let (dname, platform, app_ver) = device_headers();
    let response = client
        .post(&url)
        .header("Content-Type", "application/json")
        .header("X-Device-Name", dname)
        .header("X-Platform", platform)
        .header("X-App-Version", app_ver)
        .json(&body)
        .send()
        .await
        .map_err(|e| PostRegisterFailure::Shown(map_reqwest(e)))?;
    let status = response.status();
    let code = status.as_u16();
    let text = response.text().await.unwrap_or_default();
    let body_trim = text.trim();
    if !status.is_success() {
        if !restore && code == 409 && register_account_conflict_body(body_trim) {
            return Err(PostRegisterFailure::AccountExists);
        }
        let ctx = if restore {
            ApiContext::AuthRestore
        } else {
            ApiContext::AuthRegister
        };
        return Err(PostRegisterFailure::Shown(humanize_http(
            ctx, code, body_trim,
        )));
    }
    let ctx_parse = if restore {
        ApiContext::AuthRestore
    } else {
        ApiContext::AuthRegister
    };
    let raw: TokenPairRaw = serde_json::from_str(&text)
        .map_err(|e| PostRegisterFailure::Shown(humanize_json_decode(ctx_parse, &e)))?;
    Ok(raw.into())
}

fn register_account_conflict_body(body: &str) -> bool {
    let l = body.to_ascii_lowercase();
    l.contains("already exists")
}

async fn put_update_display_name(
    client: &reqwest::Client,
    rest_base: &str,
    access_token: &str,
    display_name: &str,
) -> Result<(), String> {
    let url = format!("{}/v1/users/me", rest_base.trim_end_matches('/'));
    let body = serde_json::json!({
        "display_name": display_name.trim(),
        "avatar_url": "",
        "banner_url": "",
        "bio": "",
    });
    let response = client
        .put(&url)
        .header("Content-Type", "application/json")
        .header("Authorization", format!("Bearer {}", access_token.trim()))
        .json(&body)
        .send()
        .await
        .map_err(map_reqwest)?;
    let status = response.status();
    if status.is_success() {
        return Ok(());
    }
    let text = response.text().await.unwrap_or_default();
    Err(humanize_http(
        ApiContext::UserProfile,
        status.as_u16(),
        text.trim(),
    ))
}

async fn put_upload_keys(
    client: &reqwest::Client,
    rest_base: &str,
    access_token: &str,
    bundle: &RegistrationBundle,
) -> Result<(), String> {
    let url = format!("{}/v1/keys", rest_base.trim_end_matches('/'));
    let body = upload_keys_json(bundle);
    let response = client
        .put(&url)
        .header("Content-Type", "application/json")
        .header("Authorization", format!("Bearer {}", access_token.trim()))
        .json(&body)
        .send()
        .await
        .map_err(map_reqwest)?;
    let status = response.status();
    if status.is_success() {
        return Ok(());
    }
    let text = response.text().await.unwrap_or_default();
    Err(humanize_http(
        ApiContext::UploadKeys,
        status.as_u16(),
        text.trim(),
    ))
}

async fn provision_account(
    seed_phrase: Vec<String>,
    restore: bool,
) -> Result<TokenPairDto, String> {
    let cfg = load_app_config();
    let reg_id = secure_registration_id();
    let bundle = generate_registration_bundle_from_seed(seed_phrase, reg_id)
        .map_err(|e| humanize_seed_crypto(&e.to_string()))?;
    let register_body = register_json_body(&bundle);
    let client = http_client()?;
    // Register и Restore принимают один и тот же JSON. Register проверяет
    // signed_pre_key_sig против identity_key из запроса; Restore — против
    // identity_key из БД (см. services/auth/internal/app/service.go). Без
    // приватного ключа идентичности подпись не сгенерировать, поэтому повтор
    // на /restore после 409 «account already exists» не даёт чужому доступу:
    // это тот же криптографический путь, что явное «У меня уже есть аккаунт».
    let mut tokens = if restore {
        post_register_or_restore(&client, &cfg.rest_base_url, register_body, true)
            .await
            .map_err(|f| match f {
                PostRegisterFailure::AccountExists => {
                    "Не удалось завершить вход: неожиданный ответ сервера.".to_string()
                }
                PostRegisterFailure::Shown(s) => s,
            })?
    } else {
        match post_register_or_restore(&client, &cfg.rest_base_url, register_body.clone(), false).await
        {
            Ok(t) => t,
            Err(PostRegisterFailure::AccountExists) => post_register_or_restore(
                &client,
                &cfg.rest_base_url,
                register_body,
                true,
            )
            .await
            .map_err(|f| match f {
                PostRegisterFailure::AccountExists => {
                    "Не удалось войти: сервер дважды вернул конфликт. Попробуйте «У меня уже есть аккаунт»."
                        .to_string()
                }
                PostRegisterFailure::Shown(s) => s,
            })?,
            Err(PostRegisterFailure::Shown(s)) => return Err(s),
        }
    };
    put_upload_keys(&client, &cfg.rest_base_url, &tokens.access_token, &bundle).await?;
    tokens.registration_id = reg_id;
    Ok(tokens)
}

/// 12 слов BIP39 только для UI (как `pickSeed()` в desktop) — без запросов к серверу.
#[tauri::command]
pub fn auth_generate_mnemonic() -> Result<Vec<String>, String> {
    let mut entropy = [0u8; 16];
    rand::thread_rng().fill_bytes(&mut entropy);
    let mnemonic = Mnemonic::from_entropy_in(Language::English, &entropy)
        .map_err(|e| humanize_seed_crypto(&e.to_string()))?;
    Ok(mnemonic
        .to_string()
        .split_whitespace()
        .map(String::from)
        .collect())
}

/// Hex `account_id` (тело identity без 0x05), как `AuthRepository.previewKotoId`.
#[tauri::command]
pub fn auth_preview_account_id(seed_phrase: Vec<String>) -> Result<String, String> {
    let bytes = identity_public_key_from_seed(seed_phrase)
        .map_err(|e| humanize_seed_crypto(&e.to_string()))?;
    Ok(hex::encode(strip_djb(&bytes)))
}

/// Три «левых» слова + целевое, в случайном порядке (как `ConfirmSeedStep` в desktop).
#[tauri::command]
pub fn auth_register_quiz_choices(
    seed_phrase: Vec<String>,
    round: u32,
) -> Result<Vec<String>, String> {
    const POSITIONS: [usize; 3] = [2, 6, 10];
    if seed_phrase.len() != 12 {
        return Err("ожидается 12 слов BIP39".into());
    }
    let r = (round.min(2)) as usize;
    let target_idx = POSITIONS[r];
    let target = seed_phrase
        .get(target_idx)
        .ok_or_else(|| "неверная позиция".to_string())?
        .clone();

    let seed_set: HashSet<String> = seed_phrase.iter().cloned().collect();
    let mut rng = rand::thread_rng();
    let mut fakes: Vec<String> = Vec::new();
    let mut guard = 0u32;
    while fakes.len() < 3 {
        guard += 1;
        if guard > 10_000 {
            return Err("не удалось подобрать слова для квиза".into());
        }
        let mut e = [0u8; 16];
        rng.fill_bytes(&mut e);
        let Ok(m) = Mnemonic::from_entropy_in(Language::English, &e) else {
            continue;
        };
        for w in m.to_string().split_whitespace().map(String::from) {
            if fakes.len() >= 3 {
                break;
            }
            if w != target && !seed_set.contains(&w) && !fakes.contains(&w) {
                fakes.push(w);
            }
        }
    }

    let mut choices = vec![fakes[0].clone(), fakes[1].clone(), fakes[2].clone(), target];
    choices.shuffle(&mut rng);
    Ok(choices)
}

/// Регистрация после прохождения UI (как `AuthRepository.register` + `updateProfile`).
#[tauri::command]
pub async fn auth_register_finish(
    seed_phrase: Vec<String>,
    display_name: String,
) -> Result<TokenPairDto, String> {
    let tokens = provision_account(seed_phrase, false).await?;
    let name = display_name.trim();
    if !name.is_empty() {
        let cfg = load_app_config();
        let client = http_client()?;
        let _ =
            put_update_display_name(&client, &cfg.rest_base_url, &tokens.access_token, name).await;
    }
    Ok(tokens)
}

/// Случайная BIP39-фраза (12 слов) + регистрация на сервере; вернуть токены и фразу для бэкапа.
#[tauri::command]
pub async fn auth_register_new() -> Result<AuthBootstrapDto, String> {
    let mut entropy = [0u8; 16];
    rand::thread_rng().fill_bytes(&mut entropy);
    let mnemonic = Mnemonic::from_entropy_in(Language::English, &entropy)
        .map_err(|e| humanize_seed_crypto(&e.to_string()))?;
    let words: Vec<String> = mnemonic
        .to_string()
        .split_whitespace()
        .map(String::from)
        .collect();
    let tokens = provision_account(words.clone(), false).await?;
    Ok(AuthBootstrapDto {
        tokens,
        mnemonic: words,
    })
}

/// Регистрация нового аккаунта или восстановление существующего по одной и той же фразе.
#[tauri::command]
pub async fn auth_provision_from_seed(
    seed_phrase: Vec<String>,
    restore: bool,
) -> Result<TokenPairDto, String> {
    provision_account(seed_phrase, restore).await
}

type RefreshBroadcast = broadcast::Sender<Result<TokenPairDto, KotoApiError>>;

fn refresh_flight_map() -> &'static Mutex<HashMap<String, RefreshBroadcast>> {
    static MAP: OnceLock<Mutex<HashMap<String, RefreshBroadcast>>> = OnceLock::new();
    MAP.get_or_init(|| Mutex::new(HashMap::new()))
}

async fn refresh_tokens_http(refresh_token: &str) -> Result<TokenPairDto, KotoApiError> {
    let cfg = load_app_config();
    let url = format!(
        "{}/v1/auth/token/refresh",
        cfg.rest_base_url.trim_end_matches('/')
    );
    let client = reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(30))
        .build()
        .map_err(|e| api_transport_error(map_reqwest(e)))?;
    let (dname, platform, app_ver) = device_headers();
    let body = serde_json::json!({ "refresh_token": refresh_token.trim() });
    let response = client
        .post(&url)
        .header("Content-Type", "application/json")
        .header("X-Koto-No-Auth", "1")
        .header("X-Device-Name", dname)
        .header("X-Platform", platform)
        .header("X-App-Version", app_ver)
        .json(&body)
        .send()
        .await
        .map_err(|e| api_transport_error(map_reqwest(e)))?;
    let status = response.status();
    let text = response.text().await.unwrap_or_default();
    if !status.is_success() {
        return Err(api_http_error(
            ApiContext::AuthRefresh,
            status.as_u16(),
            text.trim(),
        ));
    }
    let raw: TokenPairRaw =
        serde_json::from_str(&text).map_err(|e| api_json_error(ApiContext::AuthRefresh, &e))?;
    Ok(raw.into())
}

/// Single-flight по `refresh_token`: параллельные вызовы делят один HTTP-запрос и результат.
async fn refresh_tokens_coalesced(refresh_token: &str) -> Result<TokenPairDto, KotoApiError> {
    let key = refresh_token.trim().to_string();
    if key.is_empty() {
        return Err(api_transport_error("Пустой refresh token."));
    }
    let mut map = refresh_flight_map().lock().await;
    if let Some(tx) = map.get(&key) {
        let mut rx = tx.subscribe();
        drop(map);
        return match rx.recv().await {
            Ok(r) => r,
            Err(_) => Err(api_transport_error(
                "Не удалось дождаться обновления сессии.",
            )),
        };
    }
    let (tx, _) = broadcast::channel(8);
    map.insert(key.clone(), tx.clone());
    drop(map);

    let result = refresh_tokens_http(&key).await;
    let _ = tx.send(result.clone());
    refresh_flight_map().lock().await.remove(&key);
    result
}

/// POST /v1/auth/token/refresh — без Bearer, с `X-Koto-No-Auth` (как desktop `AuthApi.refresh`).
#[tauri::command]
pub async fn auth_refresh_tokens(refresh_token: String) -> Result<TokenPairDto, KotoApiError> {
    refresh_tokens_coalesced(&refresh_token).await
}

#[tauri::command]
pub async fn user_contact_request_send(
    access_token: String,
    peer_id: String,
) -> Result<(), KotoApiError> {
    let cfg = load_app_config();
    let url = format!(
        "{}/v1/contacts/requests",
        cfg.rest_base_url.trim_end_matches('/')
    );
    let client = reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(30))
        .build()
        .map_err(|e| api_transport_error(map_reqwest(e)))?;
    let response = client
        .post(&url)
        .header("Content-Type", "application/json")
        .header("Authorization", format!("Bearer {}", access_token.trim()))
        .json(&serde_json::json!({ "peer_id": peer_id.trim() }))
        .send()
        .await
        .map_err(|e| api_transport_error(map_reqwest(e)))?;
    let status = response.status();
    if status.is_success() {
        return Ok(());
    }
    let text = response.text().await.unwrap_or_default();
    Err(api_http_error(
        ApiContext::UserProfile,
        status.as_u16(),
        text.trim(),
    ))
}

/// GET /v1/contacts/friends/overview — друзья и заявки с подгрузкой профилей.
#[tauri::command]
pub async fn user_friends_overview(access_token: String) -> Result<serde_json::Value, KotoApiError> {
    let cfg = load_app_config();
    let url = format!(
        "{}/v1/contacts/friends/overview",
        cfg.rest_base_url.trim_end_matches('/')
    );
    let client = reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(30))
        .build()
        .map_err(|e| api_transport_error(map_reqwest(e)))?;
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
            ApiContext::UserProfile,
            status.as_u16(),
            text.trim(),
        ));
    }
    serde_json::from_str(&text).map_err(|e| api_json_error(ApiContext::UserProfile, &e))
}

#[tauri::command]
pub async fn user_contact_request_incoming(
    access_token: String,
) -> Result<Vec<serde_json::Value>, KotoApiError> {
    let cfg = load_app_config();
    let url = format!(
        "{}/v1/contacts/requests/incoming",
        cfg.rest_base_url.trim_end_matches('/')
    );
    let client = reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(30))
        .build()
        .map_err(|e| api_transport_error(map_reqwest(e)))?;
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
            ApiContext::UserProfile,
            status.as_u16(),
            text.trim(),
        ));
    }
    serde_json::from_str(&text).map_err(|e| api_json_error(ApiContext::UserProfile, &e))
}

#[tauri::command]
pub async fn user_contact_request_accept(
    access_token: String,
    from_id: String,
) -> Result<(), KotoApiError> {
    let cfg = load_app_config();
    let url = format!(
        "{}/v1/contacts/requests/{}/accept",
        cfg.rest_base_url.trim_end_matches('/'),
        from_id.trim()
    );
    let client = reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(30))
        .build()
        .map_err(|e| api_transport_error(map_reqwest(e)))?;
    let response = client
        .post(&url)
        .header("Authorization", format!("Bearer {}", access_token.trim()))
        .send()
        .await
        .map_err(|e| api_transport_error(map_reqwest(e)))?;
    let status = response.status();
    if status.is_success() {
        return Ok(());
    }
    let text = response.text().await.unwrap_or_default();
    Err(api_http_error(
        ApiContext::UserProfile,
        status.as_u16(),
        text.trim(),
    ))
}

#[tauri::command]
pub async fn user_can_message_peer(
    access_token: String,
    peer_id: String,
) -> Result<serde_json::Value, KotoApiError> {
    let cfg = load_app_config();
    let url = format!(
        "{}/v1/contacts/can-message/{}",
        cfg.rest_base_url.trim_end_matches('/'),
        peer_id.trim()
    );
    let client = reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(30))
        .build()
        .map_err(|e| api_transport_error(map_reqwest(e)))?;
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
            ApiContext::UserProfile,
            status.as_u16(),
            text.trim(),
        ));
    }
    serde_json::from_str(&text).map_err(|e| api_json_error(ApiContext::UserProfile, &e))
}

#[tauri::command]
pub async fn user_friend_relation(
    access_token: String,
    peer_id: String,
) -> Result<serde_json::Value, KotoApiError> {
    let cfg = load_app_config();
    let url = format!(
        "{}/v1/contacts/requests/with/{}",
        cfg.rest_base_url.trim_end_matches('/'),
        peer_id.trim()
    );
    let client = reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(30))
        .build()
        .map_err(|e| api_transport_error(map_reqwest(e)))?;
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
            ApiContext::UserProfile,
            status.as_u16(),
            text.trim(),
        ));
    }
    serde_json::from_str(&text).map_err(|e| api_json_error(ApiContext::UserProfile, &e))
}

#[tauri::command]
pub async fn user_contact_request_reject(
    access_token: String,
    from_id: String,
) -> Result<(), KotoApiError> {
    let cfg = load_app_config();
    let url = format!(
        "{}/v1/contacts/requests/{}/reject",
        cfg.rest_base_url.trim_end_matches('/'),
        from_id.trim()
    );
    let client = reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(30))
        .build()
        .map_err(|e| api_transport_error(map_reqwest(e)))?;
    let response = client
        .post(&url)
        .header("Authorization", format!("Bearer {}", access_token.trim()))
        .send()
        .await
        .map_err(|e| api_transport_error(map_reqwest(e)))?;
    let status = response.status();
    if status.is_success() {
        return Ok(());
    }
    let text = response.text().await.unwrap_or_default();
    Err(api_http_error(
        ApiContext::UserProfile,
        status.as_u16(),
        text.trim(),
    ))
}
