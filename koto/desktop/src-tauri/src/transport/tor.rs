//! Tor sidecar — запускаем системный `tor` бинарь, открываем SOCKS5-порт,
//! проксируем reqwest. Альтернатива arti-client (чистый Rust, но 5+ минут
//! компиляции и 50+ MB к binary size) — пока остаётся в TODO.
//!
//! Гейтвей `koto.run` адресуется через свой onion-адрес, который записан
//! в bridge-list manifest. UI видит обычный URL вида `http://<hash>.onion`.

use async_trait::async_trait;
use serde::{Deserialize, Serialize};
use std::time::Duration;
use tokio::process::Child;

use super::{
    sidecar::{proxy_client, spawn_sidecar, wait_for_port},
    Tunnel, TransportEndpoint, TransportError,
};

/// Конфиг Tor для конкретного endpoint'а. Onion-адрес записан в
/// `endpoint.rest_base_url` (например, `http://abc...onion`); здесь живут
/// тонкие настройки самого `tor` процесса.
#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct TorConfig {
    /// Дополнительные `Bridge` строки (`obfs4 …`) для обхода блокировки
    /// самого Tor — нужно когда даже Tor-handshake режут DPI'ем.
    #[serde(default)]
    pub bridges: Vec<String>,
    /// Имя transport-плагина (`obfs4`, `meek_lite`, `snowflake`); если пусто —
    /// клиент идёт «голым» Tor (подходит, если страна не блокирует Tor).
    #[serde(default)]
    pub pluggable_transport: String,
    /// Путь к бинарю транспорта (e.g. `/usr/bin/obfs4proxy`).
    #[serde(default)]
    pub pluggable_transport_path: String,
}

pub struct TorTunnel {
    endpoint: TransportEndpoint,
    socks_port: u16,
    _child: Child,
}

impl TorTunnel {
    pub async fn start(endpoint: TransportEndpoint) -> Result<Self, TransportError> {
        let cfg = endpoint.tor.clone().unwrap_or_default();
        let socks_port = pick_local_port().await?;
        // tor умеет читать конфиг из stdin через `-f -`, но проще передать
        // ключевые опции аргументами — они короче чем torrc.
        let socks_arg = socks_port.to_string();
        let mut args: Vec<String> = vec![
            "--SocksPort".into(),
            socks_arg,
            "--ClientUseIPv6".into(),
            "1".into(),
            "--AvoidDiskWrites".into(),
            "1".into(),
        ];
        if !cfg.pluggable_transport.is_empty() && !cfg.pluggable_transport_path.is_empty() {
            args.push("--ClientTransportPlugin".into());
            args.push(format!(
                "{} exec {}",
                cfg.pluggable_transport, cfg.pluggable_transport_path
            ));
        }
        for bridge in &cfg.bridges {
            args.push("--Bridge".into());
            args.push(bridge.clone());
        }
        if !cfg.bridges.is_empty() {
            args.push("--UseBridges".into());
            args.push("1".into());
        }
        let args_ref: Vec<&str> = args.iter().map(String::as_str).collect();
        let child = spawn_sidecar("tor", &args_ref, None).await?;
        // tor поднимает SOCKS-порт за 1-2 секунды, но bootstrap самого
        // circuit'а — до 30. Нам сейчас достаточно SOCKS-готовности; реальное
        // ожидание circuit'а делает первая HTTP-проба.
        wait_for_port(socks_port, Duration::from_secs(15)).await?;
        Ok(Self {
            endpoint,
            socks_port,
            _child: child,
        })
    }
}

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
impl Tunnel for TorTunnel {
    fn endpoint(&self) -> &TransportEndpoint {
        &self.endpoint
    }

    async fn probe(&self) -> Result<(), TransportError> {
        let url = format!(
            "{}/health",
            self.endpoint.rest_base_url.trim_end_matches('/')
        );
        let client = proxy_client(self.socks_port)?;
        // Tor circuit может строиться до 30 секунд — для probe этого достаточно,
        // но reqwest в proxy_client уже жёстко ограничен 5с. Если первая
        // проба провалилась — auto-fallback пойдёт дальше; повтор ляжет на
        // следующее переключение.
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
