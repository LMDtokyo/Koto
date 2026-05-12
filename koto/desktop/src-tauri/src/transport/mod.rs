//! Транспортный слой: единая абстракция над разными способами достучаться до
//! gateway. Конкретные реализации появляются по мере добавления — сейчас
//! только `direct` (прямое HTTPS+WebSocket подключение к koto.run). Дальше:
//! `cloudflare` (через Cloudflare Workers), `reality` (VLESS+Reality bridge),
//! `tor` (через arti-runtime).

use async_trait::async_trait;
use serde::{Deserialize, Serialize};

pub mod commands;
pub mod direct;
pub mod doh;
pub mod manifest;
pub mod reality;
pub mod runtime;
pub mod sidecar;
pub mod tor;

/// Канал, по которому клиент устанавливает связь с gateway.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum TransportKind {
    /// Прямой HTTPS+WS на основной домен. Default.
    Direct,
    /// Через Cloudflare Worker / edge-domain (другой hostname, тот же бэк).
    Cloudflare,
    /// VLESS+Reality bridge (мимикрия под legitimate TLS).
    Reality,
    /// Hysteria2 UDP-bridge (для нестабильных мобильных сетей).
    Hysteria2,
    /// Tor onion service через arti-runtime.
    Tor,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TransportEndpoint {
    pub kind: TransportKind,
    /// REST base URL клиента (без trailing slash).
    pub rest_base_url: String,
    /// WebSocket base URL.
    pub ws_base_url: String,
    /// Порядок попытки в auto-fallback каскаде. 0 = пробуем первым.
    #[serde(default)]
    pub order: u32,
    /// Опциональный label для UI («Прямое», «Bridge EU-1»).
    #[serde(default)]
    pub label: String,
    /// Конфиг VLESS+Reality (только для kind=Reality).
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub reality: Option<reality::RealityConfig>,
    /// Конфиг Tor onion-service (только для kind=Tor).
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub tor: Option<tor::TorConfig>,
}

#[derive(Debug, thiserror::Error)]
pub enum TransportError {
    #[error("transport probe timed out")]
    Timeout,
    #[error("transport unreachable: {0}")]
    Unreachable(String),
    #[error("transport not implemented: {0:?}")]
    NotImplemented(TransportKind),
    #[error("sidecar binary not found: {0}")]
    SidecarMissing(String),
    #[error("sidecar process failed: {0}")]
    SidecarFailed(String),
    #[error("transport config missing for {0:?}")]
    ConfigMissing(TransportKind),
}

/// Реализация одного транспорта. На текущий момент мы используем только
/// REST/WS-вызовы, поэтому Tunnel.probe() — это просто дешёвый health-check.
#[async_trait]
pub trait Tunnel: Send + Sync {
    fn endpoint(&self) -> &TransportEndpoint;

    /// Быстрый health-probe, < 5 секунд. Возвращает Ok если транспорт доступен.
    async fn probe(&self) -> Result<(), TransportError>;
}

/// Текущая активная конфигурация (выставляется через settings TransportSelection).
#[derive(Debug, Clone, Copy, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum TransportSelection {
    /// Авто-каскад: пробуем endpoints в порядке `order`.
    Auto,
    /// Жёстко выбран один транспорт (для отладки или когда юзер в стране с цензурой).
    Pinned(TransportKind),
}

impl Default for TransportSelection {
    fn default() -> Self {
        Self::Auto
    }
}
