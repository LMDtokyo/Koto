//! Серверные URL и порты — те же переменные, что у desktop `AppConfig.fromEnvironment()`
//! (`desktop/app/.../AppModule.kt`).

use serde::Serialize;

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AppConfigDto {
    pub base_host: String,
    pub base_tls: bool,
    pub rest_port: u16,
    pub ws_port: u16,
    /// База REST gateway, например `http://127.0.0.1:8080`
    pub rest_base_url: String,
    /// База WebSocket gateway, например `ws://127.0.0.1:9080`
    pub ws_base_url: String,
}

fn env_trim(key: &str) -> Option<String> {
    std::env::var(key)
        .ok()
        .map(|s| s.trim().to_string())
        .filter(|s| !s.is_empty())
}

fn parse_bool(s: &str) -> bool {
    matches!(s.to_lowercase().as_str(), "1" | "true" | "yes")
}

/// Читает `KOTO_HOST`, `KOTO_TLS`, `KOTO_REST_PORT`, `KOTO_WS_PORT` — как в Compose desktop.
pub fn load_app_config() -> AppConfigDto {
    let base_host = env_trim("KOTO_HOST").unwrap_or_else(|| "127.0.0.1".to_string());
    let base_tls = env_trim("KOTO_TLS")
        .map(|s| parse_bool(&s))
        .unwrap_or(false);
    // По умолчанию 8081 — как проброс хоста в docker-compose (`8081:8080` у gateway).
    // Android debug тоже смотрит на 8081, если на хосте :8080 занят (см. `android/app/build.gradle.kts`).
    let rest_port = env_trim("KOTO_REST_PORT")
        .and_then(|s| s.parse().ok())
        .unwrap_or(if base_tls { 443 } else { 8081 });
    let ws_port = env_trim("KOTO_WS_PORT")
        .and_then(|s| s.parse().ok())
        .unwrap_or(if base_tls { 9443 } else { 9080 });

    let rest_scheme = if base_tls { "https" } else { "http" };
    let ws_scheme = if base_tls { "wss" } else { "ws" };
    let rest_base_url = format!("{rest_scheme}://{base_host}:{rest_port}");
    let ws_base_url = format!("{ws_scheme}://{base_host}:{ws_port}");

    AppConfigDto {
        base_host,
        base_tls,
        rest_port,
        ws_port,
        rest_base_url,
        ws_base_url,
    }
}
