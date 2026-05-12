//! Chat REST через gateway — зеркало desktop `ChatApi`.

use std::collections::HashMap;

use serde::{Deserialize, Serialize};

use crate::config::load_app_config;
use crate::koto_errors::{
    api_http_error, api_json_error, api_transport_error, map_reqwest, ApiContext, KotoApiError,
};

fn http_client() -> Result<reqwest::Client, KotoApiError> {
    reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(45))
        .build()
        .map_err(|e| api_transport_error(map_reqwest(e)))
}

#[derive(Debug, Deserialize, Serialize, Clone, Default)]
#[serde(rename_all = "snake_case")]
pub struct LastMessageRow {
    pub id: String,
    #[serde(default)]
    pub ciphertext: String,
    pub sender_id: String,
    pub sent_at: i64,
    #[serde(default)]
    pub delivered: bool,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
#[serde(rename_all = "snake_case")]
pub struct ConversationRow {
    pub id: String,
    #[serde(rename = "type")]
    pub conv_type: u8,
    #[serde(default)]
    pub name: String,
    pub display_name: String,
    pub peer_id: String,
    #[serde(default)]
    pub member_ids: Vec<String>,
    #[serde(default)]
    pub last_message: Option<LastMessageRow>,
    #[serde(default)]
    pub unread_count: i32,
    #[serde(default)]
    pub online: bool,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
#[serde(rename_all = "snake_case")]
pub struct HistoryMessageRow {
    pub id: String,
    /// Base64 на wire (как в Kotlin `HistoryMessageDto`).
    pub ciphertext: String,
    pub sender_id: String,
    pub sent_at: i64,
    #[serde(default)]
    pub delivered: bool,
    #[serde(default)]
    pub reply_to: Option<String>,
    #[serde(default)]
    pub edited_at: i64,
    #[serde(default)]
    pub forwarded_from: Option<String>,
    #[serde(default)]
    pub pinned: bool,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
#[serde(rename_all = "snake_case")]
pub struct ProfileRow {
    pub account_id: String,
    #[serde(default)]
    pub display_name: String,
    #[serde(default)]
    pub avatar_url: String,
    #[serde(default)]
    pub username: String,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
#[serde(rename_all = "snake_case")]
pub struct SearchEnvelope<T> {
    pub items: Vec<T>,
    #[serde(default)]
    pub next_cursor: String,
    #[serde(default)]
    pub has_more: bool,
}

/// GET /v1/conversations
#[tauri::command]
pub async fn list_conversations(
    access_token: String,
) -> Result<Vec<ConversationRow>, KotoApiError> {
    let cfg = load_app_config();
    let url = format!(
        "{}/v1/conversations",
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
            ApiContext::Conversations,
            status.as_u16(),
            text.trim(),
        ));
    }
    serde_json::from_str(&text).map_err(|e| api_json_error(ApiContext::Conversations, &e))
}

/// GET /v1/users/search
#[tauri::command]
pub async fn search_users(
    access_token: String,
    query: String,
    limit: Option<u32>,
    cursor: Option<String>,
) -> Result<SearchEnvelope<ProfileRow>, KotoApiError> {
    let cfg = load_app_config();
    let base = cfg.rest_base_url.trim_end_matches('/');
    let q = query.trim();
    let lim = limit.unwrap_or(20).clamp(1, 50);
    let mut url = format!("{}/v1/users/search?q={}&limit={}", base, q, lim);
    if let Some(c) = cursor.as_ref().map(|s| s.trim()).filter(|s| !s.is_empty()) {
        url.push_str("&cursor=");
        url.push_str(c);
    }
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
            ApiContext::SearchUsers,
            status.as_u16(),
            text.trim(),
        ));
    }
    serde_json::from_str(&text).map_err(|e| api_json_error(ApiContext::SearchUsers, &e))
}

/// GET /v1/conversations/search
#[tauri::command]
pub async fn search_conversations(
    access_token: String,
    query: String,
    limit: Option<u32>,
    cursor: Option<String>,
) -> Result<SearchEnvelope<ConversationRow>, KotoApiError> {
    let cfg = load_app_config();
    let base = cfg.rest_base_url.trim_end_matches('/');
    let q = query.trim();
    let lim = limit.unwrap_or(20).clamp(1, 50);
    let mut url = format!("{}/v1/conversations/search?q={}&limit={}", base, q, lim);
    if let Some(c) = cursor.as_ref().map(|s| s.trim()).filter(|s| !s.is_empty()) {
        url.push_str("&cursor=");
        url.push_str(c);
    }
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
            ApiContext::SearchConversations,
            status.as_u16(),
            text.trim(),
        ));
    }
    serde_json::from_str(&text).map_err(|e| api_json_error(ApiContext::SearchConversations, &e))
}

#[derive(Debug, Deserialize)]
struct PresenceEnvelope {
    peers: HashMap<String, bool>,
}

/// POST /v1/presence — шлюз: есть ли у peer_id активное WS-подключение.
#[tauri::command]
pub async fn fetch_peer_presence(
    access_token: String,
    peer_ids: Vec<String>,
) -> Result<HashMap<String, bool>, KotoApiError> {
    let cfg = load_app_config();
    let url = format!("{}/v1/presence", cfg.rest_base_url.trim_end_matches('/'));
    let body = serde_json::json!({ "peer_ids": peer_ids });
    let client = http_client()?;
    let response = client
        .post(&url)
        .header("Content-Type", "application/json")
        .header("Authorization", format!("Bearer {}", access_token.trim()))
        .json(&body)
        .send()
        .await
        .map_err(|e| api_transport_error(map_reqwest(e)))?;
    let status = response.status();
    let text = response.text().await.unwrap_or_default();
    if !status.is_success() {
        return Err(api_http_error(
            ApiContext::Presence,
            status.as_u16(),
            text.trim(),
        ));
    }
    let env: PresenceEnvelope =
        serde_json::from_str(&text).map_err(|e| api_json_error(ApiContext::Presence, &e))?;
    Ok(env.peers)
}

/// GET /v1/conversations/{id}/messages — newest-first (как Scylla `ORDER BY msg_id DESC`).
#[tauri::command]
pub async fn list_conversation_messages(
    access_token: String,
    conversation_id: String,
    cursor: Option<String>,
    limit: u32,
) -> Result<Vec<HistoryMessageRow>, KotoApiError> {
    let cfg = load_app_config();
    let base = cfg.rest_base_url.trim_end_matches('/');
    let lim = limit.clamp(1, 100);
    let mut url = format!(
        "{}/v1/conversations/{}/messages?limit={}",
        base, conversation_id, lim
    );
    if let Some(c) = cursor.as_ref().map(|s| s.trim()).filter(|s| !s.is_empty()) {
        url.push_str("&cursor=");
        url.push_str(c);
    }
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
            ApiContext::Messages,
            status.as_u16(),
            text.trim(),
        ));
    }
    serde_json::from_str(&text).map_err(|e| api_json_error(ApiContext::Messages, &e))
}

/// GET /v1/conversations/{id}/messages/search
#[tauri::command]
pub async fn search_conversation_messages_meta(
    access_token: String,
    conversation_id: String,
    sender_id: Option<String>,
    from_ts: Option<i64>,
    to_ts: Option<i64>,
    message_type: Option<u8>,
    cursor: Option<String>,
    limit: Option<u32>,
) -> Result<SearchEnvelope<HistoryMessageRow>, KotoApiError> {
    let cfg = load_app_config();
    let base = cfg.rest_base_url.trim_end_matches('/');
    let lim = limit.unwrap_or(50).clamp(1, 100);
    let mut url = format!(
        "{}/v1/conversations/{}/messages/search?limit={}",
        base, conversation_id, lim
    );
    if let Some(v) = sender_id
        .as_ref()
        .map(|s| s.trim())
        .filter(|s| !s.is_empty())
    {
        url.push_str("&sender_id=");
        url.push_str(v);
    }
    if let Some(v) = from_ts {
        url.push_str("&from=");
        url.push_str(&v.to_string());
    }
    if let Some(v) = to_ts {
        url.push_str("&to=");
        url.push_str(&v.to_string());
    }
    if let Some(v) = message_type {
        url.push_str("&type=");
        url.push_str(&v.to_string());
    }
    if let Some(c) = cursor.as_ref().map(|s| s.trim()).filter(|s| !s.is_empty()) {
        url.push_str("&cursor=");
        url.push_str(c);
    }

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
            ApiContext::SearchMessagesMeta,
            status.as_u16(),
            text.trim(),
        ));
    }
    serde_json::from_str(&text).map_err(|e| api_json_error(ApiContext::SearchMessagesMeta, &e))
}

#[derive(Debug, Deserialize, Serialize)]
pub struct SendMessageResponseDto {
    pub id: String,
    pub sent_at: i64,
}

#[derive(Debug, Deserialize, Serialize)]
pub struct EditMessageResponseDto {
    pub edited_at: i64,
}

/// PATCH /v1/conversations/{conv_id}/messages/{msg_id}
#[tauri::command]
pub async fn edit_conversation_message(
    access_token: String,
    conversation_id: String,
    message_id: String,
    ciphertext_base64: String,
) -> Result<EditMessageResponseDto, KotoApiError> {
    let cfg = load_app_config();
    let url = format!(
        "{}/v1/conversations/{}/messages/{}",
        cfg.rest_base_url.trim_end_matches('/'),
        conversation_id,
        message_id,
    );
    let body = serde_json::json!({ "ciphertext": ciphertext_base64.trim() });
    let client = http_client()?;
    let response = client
        .patch(&url)
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
            ApiContext::SendMessage,
            status.as_u16(),
            text.trim(),
        ));
    }
    serde_json::from_str(&text).map_err(|e| api_json_error(ApiContext::SendMessage, &e))
}

/// DELETE /v1/conversations/{conv_id}/messages/{msg_id}
#[tauri::command]
pub async fn delete_conversation_message(
    access_token: String,
    conversation_id: String,
    message_id: String,
) -> Result<(), KotoApiError> {
    let cfg = load_app_config();
    let url = format!(
        "{}/v1/conversations/{}/messages/{}",
        cfg.rest_base_url.trim_end_matches('/'),
        conversation_id,
        message_id,
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
            ApiContext::SendMessage,
            status.as_u16(),
            text.trim(),
        ));
    }
    Ok(())
}

#[derive(Debug, Deserialize, Serialize)]
pub struct ToggleReactionResponseDto {
    pub added: bool,
}

/// POST /v1/conversations/{conv_id}/messages/{msg_id}/reactions/{emoji}
#[tauri::command]
pub async fn toggle_message_reaction(
    access_token: String,
    conversation_id: String,
    message_id: String,
    emoji: String,
) -> Result<ToggleReactionResponseDto, KotoApiError> {
    let cfg = load_app_config();
    let url = format!(
        "{}/v1/conversations/{}/messages/{}/reactions/{}",
        cfg.rest_base_url.trim_end_matches('/'),
        conversation_id,
        message_id,
        urlencoding::encode(&emoji),
    );
    let client = http_client()?;
    let response = client
        .post(&url)
        .header("Authorization", format!("Bearer {}", access_token.trim()))
        .send()
        .await
        .map_err(|e| api_transport_error(map_reqwest(e)))?;
    let status = response.status();
    let text = response.text().await.unwrap_or_default();
    if !status.is_success() {
        return Err(api_http_error(
            ApiContext::SendMessage,
            status.as_u16(),
            text.trim(),
        ));
    }
    serde_json::from_str(&text).map_err(|e| api_json_error(ApiContext::SendMessage, &e))
}

#[derive(Debug, Deserialize, Serialize)]
#[serde(rename_all = "snake_case")]
pub struct CreateConversationResponseDto {
    pub conversation_id: String,
    #[serde(rename = "type")]
    pub conv_type: u8,
    #[serde(default)]
    pub name: String,
    #[serde(default)]
    pub member_ids: Vec<String>,
}

/// POST /v1/conversations/{id}/messages — `ciphertext_base64` как в Kotlin (тело для gateway).
#[tauri::command]
pub async fn send_conversation_message(
    access_token: String,
    conversation_id: String,
    message_type: u8,
    ciphertext_base64: String,
    client_seq: i64,
    reply_to: Option<String>,
) -> Result<SendMessageResponseDto, KotoApiError> {
    let cfg = load_app_config();
    let base = cfg.rest_base_url.trim_end_matches('/');
    let url = format!("{}/v1/conversations/{}/messages", base, conversation_id);
    let body = serde_json::json!({
        "type": message_type,
        "ciphertext": ciphertext_base64.trim(),
        "client_seq": client_seq,
        "reply_to": reply_to.as_ref().map(|s| s.trim()).filter(|s| !s.is_empty()).unwrap_or(""),
        "forwarded_from": "",
    });
    let client = http_client()?;
    let response = client
        .post(&url)
        .header("Content-Type", "application/json")
        .header("Authorization", format!("Bearer {}", access_token.trim()))
        .json(&body)
        .send()
        .await
        .map_err(|e| api_transport_error(map_reqwest(e)))?;
    let status = response.status();
    let text = response.text().await.unwrap_or_default();
    if !status.is_success() {
        return Err(api_http_error(
            ApiContext::SendMessage,
            status.as_u16(),
            text.trim(),
        ));
    }
    serde_json::from_str(&text).map_err(|e| api_json_error(ApiContext::SendMessage, &e))
}

/// POST /v1/conversations — личный чат (type=1), один peer по hex account id.
#[tauri::command]
pub async fn create_direct_conversation(
    access_token: String,
    peer_account_id: String,
) -> Result<CreateConversationResponseDto, KotoApiError> {
    let cfg = load_app_config();
    let url = format!(
        "{}/v1/conversations",
        cfg.rest_base_url.trim_end_matches('/')
    );
    let peer = peer_account_id.trim().to_string();
    if peer.is_empty() {
        return Err(api_transport_error("Укажите Koto ID собеседника (hex)."));
    }
    let body = serde_json::json!({
        "member_ids": [peer],
        "type": 1u8,
        "name": "",
    });
    let client = http_client()?;
    let response = client
        .post(&url)
        .header("Content-Type", "application/json")
        .header("Authorization", format!("Bearer {}", access_token.trim()))
        .json(&body)
        .send()
        .await
        .map_err(|e| api_transport_error(map_reqwest(e)))?;
    let status = response.status();
    let text = response.text().await.unwrap_or_default();
    if !status.is_success() {
        return Err(api_http_error(
            ApiContext::CreateChat,
            status.as_u16(),
            text.trim(),
        ));
    }
    serde_json::from_str(&text).map_err(|e| api_json_error(ApiContext::CreateChat, &e))
}
