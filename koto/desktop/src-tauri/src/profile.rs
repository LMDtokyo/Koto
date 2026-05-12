//! Profile + media REST через gateway.
//! Эндпоинты:
//!   GET  /v1/users/me                    — текущий профиль
//!   PUT  /v1/users/me                    — обновление display_name/avatar_url/banner_url/bio
//!   POST /v1/media/upload-url            — пресайнед PUT URL
//!   GET  /v1/media/{file_id}             — пресайнед GET URL (для просмотра аватара/баннера)

use serde::{Deserialize, Serialize};

use crate::config::load_app_config;
use crate::koto_errors::{
    api_http_error, api_json_error, api_transport_error, map_reqwest, ApiContext, KotoApiError,
};

fn http_client() -> Result<reqwest::Client, KotoApiError> {
    reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(30))
        .build()
        .map_err(|e| api_transport_error(map_reqwest(e)))
}

#[derive(Debug, Deserialize, Serialize, Clone, Default)]
#[serde(rename_all = "snake_case")]
pub struct ProfileDto {
    pub account_id: String,
    #[serde(default)]
    pub display_name: String,
    #[serde(default)]
    pub avatar_url: String,
    #[serde(default)]
    pub banner_url: String,
    #[serde(default)]
    pub bio: String,
    #[serde(default)]
    pub username: String,
}

#[derive(Debug, Serialize)]
struct UpdateProfileBody {
    display_name: String,
    avatar_url: String,
    banner_url: String,
    bio: String,
}

/// GET /v1/users/me
#[tauri::command]
pub async fn get_my_profile(access_token: String) -> Result<ProfileDto, KotoApiError> {
    let cfg = load_app_config();
    let url = format!("{}/v1/users/me", cfg.rest_base_url.trim_end_matches('/'));
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
            ApiContext::UserProfile,
            status.as_u16(),
            text.trim(),
        ));
    }
    serde_json::from_str(&text).map_err(|e| api_json_error(ApiContext::UserProfile, &e))
}

/// PUT /v1/users/me
#[tauri::command]
pub async fn update_my_profile(
    access_token: String,
    display_name: Option<String>,
    avatar_url: Option<String>,
    banner_url: Option<String>,
    bio: Option<String>,
) -> Result<ProfileDto, KotoApiError> {
    let cfg = load_app_config();
    let url = format!("{}/v1/users/me", cfg.rest_base_url.trim_end_matches('/'));
    let body = UpdateProfileBody {
        display_name: display_name.unwrap_or_default(),
        avatar_url: avatar_url.unwrap_or_default(),
        banner_url: banner_url.unwrap_or_default(),
        bio: bio.unwrap_or_default(),
    };

    let client = http_client()?;
    let response = client
        .put(&url)
        .header("Authorization", format!("Bearer {}", access_token.trim()))
        .header("Content-Type", "application/json")
        .json(&body)
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

#[derive(Debug, Deserialize, Serialize, Clone)]
#[serde(rename_all = "snake_case")]
pub struct UploadUrlDto {
    pub file_id: String,
    pub upload_url: String,
}

#[derive(Debug, Serialize)]
struct UploadRequestBody {
    content_type: String,
    size_bytes: i64,
    is_public: bool,
}

/// POST /v1/media/upload-url
#[tauri::command]
pub async fn request_media_upload_url(
    access_token: String,
    content_type: String,
    size_bytes: i64,
    is_public: bool,
) -> Result<UploadUrlDto, KotoApiError> {
    let cfg = load_app_config();
    let url = format!(
        "{}/v1/media/upload-url",
        cfg.rest_base_url.trim_end_matches('/')
    );
    let body = UploadRequestBody {
        content_type,
        size_bytes,
        is_public,
    };
    let client = http_client()?;
    let response = client
        .post(&url)
        .header("Authorization", format!("Bearer {}", access_token.trim()))
        .header("Content-Type", "application/json")
        .json(&body)
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

/// PUT presigned URL — Rust качает байты из payload и шлёт прямо в MinIO.
/// Используем base64 для перекидывания бинаря через IPC.
#[tauri::command]
pub async fn upload_to_presigned(
    upload_url: String,
    content_type: String,
    body_base64: String,
) -> Result<(), KotoApiError> {
    use base64::{engine::general_purpose::STANDARD as B64, Engine as _};
    let bytes = B64
        .decode(body_base64.as_bytes())
        .map_err(|e| KotoApiError {
            status: 0,
            message: format!("Не удалось декодировать файл: {e}"),
        })?;
    let client = reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(120))
        .build()
        .map_err(|e| api_transport_error(map_reqwest(e)))?;
    let response = client
        .put(&upload_url)
        .header("Content-Type", content_type)
        .body(bytes)
        .send()
        .await
        .map_err(|e| api_transport_error(map_reqwest(e)))?;
    let status = response.status();
    if !status.is_success() {
        let text = response.text().await.unwrap_or_default();
        return Err(api_http_error(
            ApiContext::UserProfile,
            status.as_u16(),
            text.trim(),
        ));
    }
    Ok(())
}

#[derive(Debug, Deserialize, Serialize, Clone)]
#[serde(rename_all = "snake_case")]
pub struct MediaUrlDto {
    pub file_id: String,
    pub download_url: String,
    #[serde(default)]
    pub content_type: String,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
#[serde(rename_all = "snake_case")]
pub struct UsernameAvailableDto {
    pub available: bool,
    #[serde(default)]
    pub reason: String,
}

/// GET /v1/users/username-available/{username}
#[tauri::command]
pub async fn user_username_available(
    access_token: String,
    username: String,
) -> Result<UsernameAvailableDto, KotoApiError> {
    let cfg = load_app_config();
    let url = format!(
        "{}/v1/users/username-available/{}",
        cfg.rest_base_url.trim_end_matches('/'),
        username.trim()
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
            ApiContext::UserProfile,
            status.as_u16(),
            text.trim(),
        ));
    }
    serde_json::from_str(&text).map_err(|e| api_json_error(ApiContext::UserProfile, &e))
}

#[derive(Debug, Serialize)]
struct UsernameSetBody {
    username: String,
}

/// PUT /v1/users/me/username
#[tauri::command]
pub async fn user_set_username(
    access_token: String,
    username: String,
) -> Result<ProfileDto, KotoApiError> {
    let cfg = load_app_config();
    let url = format!("{}/v1/users/me/username", cfg.rest_base_url.trim_end_matches('/'));
    let body = UsernameSetBody {
        username: username.trim().to_string(),
    };
    let client = http_client()?;
    let response = client
        .put(&url)
        .header("Authorization", format!("Bearer {}", access_token.trim()))
        .header("Content-Type", "application/json")
        .json(&body)
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

/// GET /v1/media/{file_id}
#[tauri::command]
pub async fn get_media_download_url(
    access_token: String,
    file_id: String,
) -> Result<MediaUrlDto, KotoApiError> {
    let cfg = load_app_config();
    let url = format!(
        "{}/v1/media/{}",
        cfg.rest_base_url.trim_end_matches('/'),
        file_id.trim()
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
            ApiContext::UserProfile,
            status.as_u16(),
            text.trim(),
        ));
    }
    serde_json::from_str(&text).map_err(|e| api_json_error(ApiContext::UserProfile, &e))
}
