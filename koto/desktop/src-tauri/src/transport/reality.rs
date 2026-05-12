//! VLESS+Reality через xray-core sidecar. Reality маскирует наш TLS-handshake
//! под легитимный сайт (server_name) — DPI видит обычное https-соединение
//! к, например, www.cloudflare.com, а под капотом — VLESS-туннель к нашему
//! bridge-серверу.
//!
//! Архитектура:
//! 1. Подгружаем `RealityConfig` из manifest endpoint.
//! 2. Генерим xray config.json (VLESS outbound + SOCKS inbound на random port).
//! 3. Spawn'им `xray run -c -` (config через stdin), ждём порт.
//! 4. Probe: GET <gateway>/health через SOCKS5-прокси.
//!
//! Бинарь xray ищется в PATH; в production он будет bundle'иться через
//! Tauri sidecar (см. tauri.conf.json → externalBin).

use async_trait::async_trait;
use serde::{Deserialize, Serialize};
use serde_json::json;
use std::time::Duration;
use tokio::process::Child;

use super::{
    sidecar::{proxy_client, spawn_sidecar, wait_for_port},
    Tunnel, TransportEndpoint, TransportError,
};

/// Конфиг VLESS+Reality из bridge-list manifest. Все поля задаёт оператор
/// bridge-сервера; клиент только воспроизводит handshake.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RealityConfig {
    /// Хост/IP bridge'а (тот же, что выходит наружу как «cloudflare»).
    pub server_address: String,
    /// Порт VLESS, обычно 443.
    pub server_port: u16,
    /// VLESS user UUID.
    pub user_id: String,
    /// SNI/server_name — какой сайт мимикрируем (e.g. www.cloudflare.com).
    pub server_name: String,
    /// Reality public key (генерируется на сервере через `xray x25519`).
    pub public_key: String,
    /// Reality short id (8-байтовый hex).
    #[serde(default)]
    pub short_id: String,
    /// TLS fingerprint (chrome/firefox/edge — мимикрируем браузер).
    #[serde(default = "default_fingerprint")]
    pub fingerprint: String,
    /// VLESS flow control.
    #[serde(default = "default_flow")]
    pub flow: String,
}

fn default_fingerprint() -> String {
    "chrome".into()
}
fn default_flow() -> String {
    "xtls-rprx-vision".into()
}

/// Готовая xray-config + локальный SOCKS-порт, которые мы передаём в spawn.
fn build_xray_config(cfg: &RealityConfig, socks_port: u16) -> serde_json::Value {
    json!({
        "log": { "loglevel": "warning" },
        "inbounds": [{
            "tag": "socks-in",
            "port": socks_port,
            "listen": "127.0.0.1",
            "protocol": "socks",
            "settings": { "udp": true, "auth": "noauth" }
        }],
        "outbounds": [{
            "tag": "vless-reality",
            "protocol": "vless",
            "settings": {
                "vnext": [{
                    "address": cfg.server_address,
                    "port": cfg.server_port,
                    "users": [{
                        "id": cfg.user_id,
                        "encryption": "none",
                        "flow": cfg.flow,
                    }]
                }]
            },
            "streamSettings": {
                "network": "tcp",
                "security": "reality",
                "realitySettings": {
                    "serverName": cfg.server_name,
                    "fingerprint": cfg.fingerprint,
                    "publicKey": cfg.public_key,
                    "shortId": cfg.short_id,
                }
            }
        }]
    })
}

/// VLESS+Reality tunnel поверх xray-core. Child-процесс убивается на drop
/// благодаря `kill_on_drop` в `spawn_sidecar`.
pub struct RealityTunnel {
    endpoint: TransportEndpoint,
    socks_port: u16,
    _child: Child,
}

impl RealityTunnel {
    /// Спавнит xray-core и ждёт пока SOCKS5-порт станет доступен.
    pub async fn start(endpoint: TransportEndpoint) -> Result<Self, TransportError> {
        let cfg = endpoint
            .reality
            .as_ref()
            .ok_or(TransportError::ConfigMissing(super::TransportKind::Reality))?
            .clone();
        // Random ephemeral port; тестировщику не важно какой именно.
        let socks_port = pick_local_port().await?;
        let payload = build_xray_config(&cfg, socks_port).to_string();
        let child = spawn_sidecar("xray", &["run", "-c", "-"], Some(payload.as_bytes())).await?;
        wait_for_port(socks_port, Duration::from_secs(8)).await?;
        Ok(Self {
            endpoint,
            socks_port,
            _child: child,
        })
    }
}

/// Зарезервировать свободный TCP-порт через bind на 127.0.0.1:0.
async fn pick_local_port() -> Result<u16, TransportError> {
    let listener = tokio::net::TcpListener::bind("127.0.0.1:0")
        .await
        .map_err(|e| TransportError::SidecarFailed(format!("bind: {e}")))?;
    let port = listener
        .local_addr()
        .map_err(|e| TransportError::SidecarFailed(format!("local_addr: {e}")))?
        .port();
    drop(listener);
    Ok(port)
}

#[async_trait]
impl Tunnel for RealityTunnel {
    fn endpoint(&self) -> &TransportEndpoint {
        &self.endpoint
    }

    async fn probe(&self) -> Result<(), TransportError> {
        let url = format!(
            "{}/health",
            self.endpoint.rest_base_url.trim_end_matches('/')
        );
        let client = proxy_client(self.socks_port)?;
        let response = client
            .get(&url)
            .send()
            .await
            .map_err(|e| {
                if e.is_timeout() {
                    TransportError::Timeout
                } else {
                    TransportError::Unreachable(e.to_string())
                }
            })?;
        if !response.status().is_success() {
            return Err(TransportError::Unreachable(format!(
                "HTTP {}",
                response.status().as_u16()
            )));
        }
        Ok(())
    }
}
