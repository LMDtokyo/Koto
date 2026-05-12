//! Koto Tauri shell — WebView UI + native Rust (crypto, DB, HTTP к gateway).

mod auth;
mod chat_api;
mod config;
mod crypto;
mod koto_errors;
mod koto_ws;
mod profile;
mod sessions;
mod transport;

use config::{load_app_config, AppConfigDto};

/// Smoke-test: same `generate_registration_bundle` path as Kotlin desktop.
#[tauri::command]
fn crypto_registration_smoke() -> Result<String, String> {
    let bundle = koto_crypto::generate_registration_bundle(1).map_err(|e| e.to_string())?;
    Ok(hex::encode(bundle.identity_public_key))
}

/// Конфиг backend URL — зеркало desktop `AppConfig.fromEnvironment()`.
#[tauri::command]
fn get_app_config() -> AppConfigDto {
    load_app_config()
}

/// `GET /health` на REST gateway (как `services/gateway` `r.Get("/health", ...)`).
#[tauri::command]
async fn gateway_health() -> Result<String, String> {
    let cfg = load_app_config();
    let url = format!("{}/health", cfg.rest_base_url.trim_end_matches('/'));
    let client = reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(8))
        .build()
        .map_err(koto_errors::map_reqwest)?;

    let response = client
        .get(&url)
        .send()
        .await
        .map_err(koto_errors::map_reqwest)?;
    let status = response.status();
    let body = response.text().await.unwrap_or_default();
    if !status.is_success() {
        return Err(koto_errors::humanize_http(
            koto_errors::ApiContext::GatewayHealth,
            status.as_u16(),
            body.trim(),
        ));
    }
    Ok(body.trim().to_string())
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    // Включаем log → stderr, чтобы видеть libsignal::session_cipher::log::error
    // при ошибке расшифровки (там прячется реальная причина — InvalidPreKeyId,
    // BadKeyType, DecryptionError и т.д.). Уровень debug по умолчанию через
    // RUST_LOG; можно переопределить env-переменной.
    let _ = env_logger::Builder::from_env(
        env_logger::Env::default().default_filter_or("info,libsignal_protocol=debug"),
    )
    .try_init();

    let mut builder = tauri::Builder::default();

    // Single-instance plugin отключён для dev/QA, чтобы можно было запускать
    // два окна одновременно с разными XDG_DATA_HOME (тестирование переписки
    // между двумя аккаунтами). Включить обратно перед prod-сборкой.
    // #[cfg(desktop)]
    // {
    //     builder = builder.plugin(tauri_plugin_single_instance::init(|app, _argv, _cwd| {
    //         use tauri::Manager;
    //         if let Some(w) = app.get_webview_window("main") {
    //             let _ = w.unminimize();
    //             let _ = w.show();
    //             let _ = w.set_focus();
    //         }
    //     }));
    // }
    let _ = &mut builder;

    builder
        .manage(koto_ws::KotoWsControl::default())
        .manage(crypto::KotoCryptoState::new())
        .manage(transport::runtime::TransportRuntime::new())
        .plugin(tauri_plugin_opener::init())
        .invoke_handler(tauri::generate_handler![
            crypto_registration_smoke,
            get_app_config,
            gateway_health,
            auth::auth_register_new,
            auth::auth_generate_mnemonic,
            auth::auth_preview_account_id,
            auth::auth_register_quiz_choices,
            auth::auth_register_finish,
            auth::auth_provision_from_seed,
            auth::auth_refresh_tokens,
            auth::user_contact_request_send,
            auth::user_contact_request_incoming,
            auth::user_contact_request_accept,
            auth::user_contact_request_reject,
            auth::user_can_message_peer,
            auth::user_friend_relation,
            auth::user_friends_overview,
            chat_api::list_conversations,
            chat_api::search_users,
            chat_api::search_conversations,
            chat_api::fetch_peer_presence,
            chat_api::list_conversation_messages,
            chat_api::search_conversation_messages_meta,
            chat_api::send_conversation_message,
            chat_api::create_direct_conversation,
            chat_api::edit_conversation_message,
            chat_api::delete_conversation_message,
            chat_api::toggle_message_reaction,
            chat_api::list_message_reactions,
            profile::get_my_profile,
            profile::update_my_profile,
            profile::request_media_upload_url,
            profile::upload_to_presigned,
            profile::get_media_download_url,
            profile::user_username_available,
            profile::user_set_username,
            sessions::auth_list_sessions,
            sessions::auth_revoke_session,
            transport::commands::transport_list,
            transport::commands::transport_probe,
            transport::commands::transport_select_active,
            transport::commands::transport_last_good,
            transport::commands::transport_refresh_manifest,
            transport::doh::doh_resolve,
            crypto::crypto_init_session,
            crypto::crypto_clear_session,
            crypto::crypto_ensure_peer_session,
            crypto::crypto_encrypt,
            crypto::crypto_decrypt,
            crypto::crypto_is_ready,
            koto_ws::koto_ws_start,
            koto_ws::koto_ws_stop,
            koto_ws::koto_ws_ack_token,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
