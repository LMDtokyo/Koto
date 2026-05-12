//! Sessions REST: список и завершение сеансов через gateway → auth-service.

use serde::{Deserialize, Serialize};

use crate::config::load_app_config;
use crate::koto_errors::{
    api_http_error, api_json_error, api_transport_error, map_reqwest, ApiContext, KotoApiError,
};

fn http_client() -> Result<reqwest::Client, KotoApiError> {
    reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(20))
        .build()
        .map_err(|e| api_transport_error(map_reqwest(e)))
}

#[derive(Debug, Deserialize, Serialize, Clone)]
#[serde(rename_all = "snake_case")]
pub struct SessionRow {
    pub id: String,
    #[serde(default)]
    pub device_name: String,
    #[serde(default)]
    pub platform: String,
    #[serde(default)]
    pub app_version: String,
    #[serde(default)]
    pub client_ip: String,
    #[serde(default)]
    pub created_at: String,
    #[serde(default)]
    pub last_seen_at: String,
}

#[derive(Debug, Deserialize)]
struct SessionsEnvelope {
    #[serde(default)]
    sessions: Vec<SessionRow>,
}

/// GET /v1/auth/sessions — бэк отдаёт `{ "sessions": [...] }`.
#[tauri::command]
pub async fn auth_list_sessions(access_token: String) -> Result<Vec<SessionRow>, KotoApiError> {
    let cfg = load_app_config();
    let url = format!(
        "{}/v1/auth/sessions",
        cfg.rest_base_url.trim_end_matches('/')
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
            ApiContext::AuthRefresh,
            status.as_u16(),
            text.trim(),
        ));
    }
    let env: SessionsEnvelope =
        serde_json::from_str(&text).map_err(|e| api_json_error(ApiContext::AuthRefresh, &e))?;
    Ok(env.sessions)
}

/// DELETE /v1/auth/sessions/{session_id}
#[tauri::command]
pub async fn auth_revoke_session(
    access_token: String,
    session_id: String,
) -> Result<(), KotoApiError> {
    let cfg = load_app_config();
    let url = format!(
        "{}/v1/auth/sessions/{}",
        cfg.rest_base_url.trim_end_matches('/'),
        session_id.trim()
    );
    let client = http_client()?;
    let response = client
        .delete(&url)
        .header("Authorization", format!("Bearer {}", access_token.trim()))
        .send()
        .await
        .map_err(|e| api_transport_error(map_reqwest(e)))?;
    let status = response.status();
    if !status.is_success() {
        let text = response.text().await.unwrap_or_default();
        return Err(api_http_error(
            ApiContext::AuthRefresh,
            status.as_u16(),
            text.trim(),
        ));
    }
    Ok(())
}
