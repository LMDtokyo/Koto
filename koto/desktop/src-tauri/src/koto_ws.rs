//! Gateway WebSocket — переподключение с backoff и 401 → refresh (как desktop `KotoWebSocket`).

use std::time::Duration;

use futures_util::StreamExt;
use rand::Rng;
use serde::Serialize;
use tauri::async_runtime::JoinHandle;
use tauri::Emitter;
use tokio::sync::{Mutex, Notify};
use tokio_tungstenite::{
    connect_async,
    tungstenite::{Error as WsError, Message},
};
use url::Url;

use crate::config::load_app_config;

const MAX_BACKOFF_MS: u64 = 30_000;
const REAUTH_WAIT: Duration = Duration::from_secs(90);

#[derive(Default)]
pub struct KotoWsControl {
    task: Mutex<Option<JoinHandle<()>>>,
    access_token: std::sync::Arc<Mutex<String>>,
    reauth_gate: std::sync::Arc<Mutex<Option<std::sync::Arc<Notify>>>>,
}

fn ws_request_url(ws_base: &str, token: &str) -> Result<Url, String> {
    let base = ws_base.trim_end_matches('/');
    let mut u = Url::parse(&format!("{base}/ws")).map_err(|e| e.to_string())?;
    u.query_pairs_mut().append_pair("token", token.trim());
    Ok(u)
}

fn backoff_ms(attempt: u32) -> u64 {
    let capped = attempt.min(5);
    let base = (1_000u64 << capped).min(MAX_BACKOFF_MS);
    let jitter = rand::thread_rng().gen_range(0..base / 2 + 1);
    base / 2 + jitter
}

async fn sleep_backoff(attempt: u32) {
    tokio::time::sleep(Duration::from_millis(backoff_ms(attempt))).await;
}

fn http_status_from_ws_err(e: &WsError) -> Option<u16> {
    match e {
        WsError::Http(r) => Some(r.status().as_u16()),
        _ => None,
    }
}

#[derive(Serialize, Clone)]
#[serde(rename_all = "camelCase")]
struct WsStatusEvent {
    state: &'static str,
    #[serde(skip_serializing_if = "Option::is_none")]
    http_status: Option<u16>,
}

async fn run_ws_loop(
    app: tauri::AppHandle,
    access: std::sync::Arc<Mutex<String>>,
    reauth_gate: std::sync::Arc<Mutex<Option<std::sync::Arc<Notify>>>>,
) {
    let mut attempt: u32 = 0;
    loop {
        let cfg = load_app_config();
        let tok = access.lock().await.clone();
        if tok.is_empty() {
            tokio::time::sleep(Duration::from_secs(2)).await;
            continue;
        }
        let url = match ws_request_url(&cfg.ws_base_url, &tok) {
            Ok(u) => u,
            Err(_) => {
                sleep_backoff(attempt).await;
                attempt = attempt.saturating_add(1);
                continue;
            }
        };
        match connect_async(url.as_str()).await {
            Ok((mut ws, _)) => {
                attempt = 0;
                let _ = app.emit(
                    "koto-ws-status",
                    WsStatusEvent {
                        state: "connected",
                        http_status: None,
                    },
                );
                loop {
                    match ws.next().await {
                        Some(Ok(Message::Text(t))) => {
                            let _ = app.emit("koto-ws-frame", t);
                        }
                        Some(Ok(Message::Close(_))) | None => break,
                        Some(Ok(_)) => {}
                        Some(Err(_)) => break,
                    }
                }
                let _ = app.emit(
                    "koto-ws-status",
                    WsStatusEvent {
                        state: "disconnected",
                        http_status: None,
                    },
                );
            }
            Err(e) => {
                let code = http_status_from_ws_err(&e);
                if code == Some(401) {
                    let n = std::sync::Arc::new(Notify::new());
                    *reauth_gate.lock().await = Some(n.clone());
                    let _ = app.emit(
                        "koto-ws-reauth",
                        serde_json::json!({ "reason": "unauthorized" }),
                    );
                    let _ = tokio::time::timeout(REAUTH_WAIT, n.notified()).await;
                    attempt = 0;
                    continue;
                }
                let _ = app.emit(
                    "koto-ws-status",
                    WsStatusEvent {
                        state: "error",
                        http_status: code,
                    },
                );
                sleep_backoff(attempt).await;
                attempt = attempt.saturating_add(1);
            }
        }
    }
}

async fn koto_ws_stop_inner(state: &KotoWsControl) {
    if let Some(h) = state.task.lock().await.take() {
        h.abort();
        let _ = h.await;
    }
}

/// Запуск фонового цикла WebSocket (один на приложение; предыдущий цикл отменяется).
#[tauri::command]
pub async fn koto_ws_start(
    app: tauri::AppHandle,
    state: tauri::State<'_, KotoWsControl>,
    access_token: String,
) -> Result<(), String> {
    let token = access_token.trim().to_string();
    if token.is_empty() {
        return Err("Нет access token для WebSocket.".into());
    }
    koto_ws_stop_inner(&state).await;
    *state.access_token.lock().await = token;
    let app2 = app.clone();
    let acc = state.access_token.clone();
    let gate = state.reauth_gate.clone();
    let h = tauri::async_runtime::spawn(async move {
        run_ws_loop(app2, acc, gate).await;
    });
    *state.task.lock().await = Some(h);
    Ok(())
}

#[tauri::command]
pub async fn koto_ws_stop(state: tauri::State<'_, KotoWsControl>) -> Result<(), String> {
    koto_ws_stop_inner(&state).await;
    Ok(())
}

/// После refresh на фронтенде — обновить токен и разблокировать цикл, ожидающий после HTTP 401.
#[tauri::command]
pub async fn koto_ws_ack_token(
    state: tauri::State<'_, KotoWsControl>,
    access_token: String,
) -> Result<(), String> {
    let t = access_token.trim().to_string();
    *state.access_token.lock().await = t;
    if let Some(n) = state.reauth_gate.lock().await.take() {
        n.notify_waiters();
    }
    Ok(())
}
