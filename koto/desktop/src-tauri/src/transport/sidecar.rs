//! Sidecar pattern: запуск внешнего бинаря (xray, tor) как child-процесса,
//! ожидание готовности SOCKS5-порта, проксирование reqwest через него.
//!
//! Тяжёлые stealth-протоколы (VLESS+Reality, onion routing) реализованы в
//! проверенных проектах — нет смысла переписывать их в Rust ещё раз. Мы
//! оркеструем эти процессы и говорим им через stdin/argv что делать.
//!
//! Поиск бинаря: PATH → bundled `resources/bin/<name>` (Tauri sidecar) →
//! явная путь из конфига (поле `binary_path`).

use std::path::PathBuf;
use std::process::Stdio;
use std::time::Duration;

use tokio::io::AsyncWriteExt;
use tokio::net::TcpStream;
use tokio::process::{Child, Command};

use super::TransportError;

/// Найти бинарь по имени. Стратегия: PATH (`which`) → bundled путь от Tauri.
/// Tauri sidecar для Linux обычно это `<resource_dir>/<binary_name>`, но
/// этот код callable из любого контекста, поэтому мы просто возвращаем
/// первое имя из PATH; bundled-путь резолвится callsite'ом если нужно.
pub fn find_binary(name: &str) -> Option<PathBuf> {
    if let Ok(path) = which::which(name) {
        return Some(path);
    }
    None
}

/// Запустить sidecar с произвольными аргументами и опциональным stdin payload
/// (для xray мы пишем туда JSON-конфиг, чтобы не плодить temp-файлов).
pub async fn spawn_sidecar(
    binary: &str,
    args: &[&str],
    stdin_payload: Option<&[u8]>,
) -> Result<Child, TransportError> {
    let path = find_binary(binary)
        .ok_or_else(|| TransportError::SidecarMissing(binary.into()))?;
    let mut cmd = Command::new(path);
    cmd.args(args)
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .kill_on_drop(true);
    if stdin_payload.is_some() {
        cmd.stdin(Stdio::piped());
    } else {
        cmd.stdin(Stdio::null());
    }
    let mut child = cmd
        .spawn()
        .map_err(|e| TransportError::SidecarFailed(format!("{binary}: {e}")))?;
    if let (Some(payload), Some(mut stdin)) = (stdin_payload, child.stdin.take()) {
        stdin
            .write_all(payload)
            .await
            .map_err(|e| TransportError::SidecarFailed(format!("write stdin: {e}")))?;
        stdin
            .shutdown()
            .await
            .map_err(|e| TransportError::SidecarFailed(format!("close stdin: {e}")))?;
    }
    Ok(child)
}

/// Опросить TCP-порт на 127.0.0.1 пока он не примет соединение или не выйдет
/// таймаут. Используется чтобы дождаться "SOCKS5 готов" после spawn'а.
pub async fn wait_for_port(port: u16, timeout: Duration) -> Result<(), TransportError> {
    let deadline = tokio::time::Instant::now() + timeout;
    loop {
        if TcpStream::connect(("127.0.0.1", port)).await.is_ok() {
            return Ok(());
        }
        if tokio::time::Instant::now() >= deadline {
            return Err(TransportError::Timeout);
        }
        tokio::time::sleep(Duration::from_millis(150)).await;
    }
}

/// Построить reqwest-клиент, который ходит через локальный SOCKS5-прокси
/// (поднятый sidecar'ом). 5-секундный timeout — для health-probe.
pub fn proxy_client(port: u16) -> Result<reqwest::Client, TransportError> {
    let proxy = reqwest::Proxy::all(format!("socks5h://127.0.0.1:{port}"))
        .map_err(|e| TransportError::SidecarFailed(format!("proxy: {e}")))?;
    reqwest::Client::builder()
        .proxy(proxy)
        .timeout(Duration::from_secs(5))
        .build()
        .map_err(|e| TransportError::SidecarFailed(format!("client: {e}")))
}
