//! Tauri commands для UI: получение списка транспортов и probe текущего.

use crate::koto_errors::KotoApiError;

use super::{
    direct::DirectTunnel,
    manifest::{UPDATES_PUBLIC_KEY_HEX, UPDATES_URL},
    runtime::{LastGood, TransportRuntime},
    Tunnel, TransportEndpoint, TransportKind, TransportSelection,
};

/// Возвращает список транспортов из текущего активного manifest'а
/// (bundled default либо подтянутый signed-update).
#[tauri::command]
pub async fn transport_list(
    runtime: tauri::State<'_, TransportRuntime>,
) -> Result<Vec<TransportEndpoint>, KotoApiError> {
    Ok(runtime.current_manifest().await.endpoints)
}

/// Проверка одного endpoint'а (юзер кликнул «Test connection»).
#[tauri::command]
pub async fn transport_probe(endpoint: TransportEndpoint) -> Result<(), KotoApiError> {
    let tunnel = DirectTunnel::new(endpoint);
    tunnel.probe().await.map_err(|e| KotoApiError {
        status: 0,
        message: format!("Транспорт недоступен: {e}"),
    })
}

/// Запустить auto-fallback и вернуть выбранный endpoint. Mode:
/// - `"auto"` — пробовать всё подряд, начиная с last-good;
/// - `"<kind>"` (`"direct"`, `"cloudflare"`, `"reality"`, `"hysteria2"`, `"tor"`)
///   — pinned режим: проверяем только этот канал.
#[tauri::command]
pub async fn transport_select_active(
    mode: String,
    runtime: tauri::State<'_, TransportRuntime>,
) -> Result<TransportEndpoint, KotoApiError> {
    let manifest = runtime.current_manifest().await;
    let selection = parse_mode(&mode)?;
    runtime
        .pick_active(&manifest, selection)
        .await
        .map_err(|e| KotoApiError {
            status: 0,
            message: format!("Auto-fallback failed: {e}"),
        })
}

/// Ответ команды `transport_refresh_manifest` — UI рендерит «версия N от ...».
#[derive(serde::Serialize)]
pub struct RefreshManifestResult {
    pub version: u32,
    pub generated_at: i64,
    pub endpoint_count: usize,
}

/// Стянуть signed bridge-list с updates.koto.run (URL/ключ хардкоженные;
/// подпись Ed25519 — см. manifest.rs). При несовпадении подписи или старшей
/// существующей версии — возвращает ошибку и НЕ заменяет manifest.
#[tauri::command]
pub async fn transport_refresh_manifest(
    runtime: tauri::State<'_, TransportRuntime>,
) -> Result<RefreshManifestResult, KotoApiError> {
    let manifest = runtime
        .refresh_from_url(UPDATES_URL, UPDATES_PUBLIC_KEY_HEX)
        .await
        .map_err(|e| KotoApiError {
            status: 0,
            message: format!("Manifest update failed: {e}"),
        })?;
    Ok(RefreshManifestResult {
        version: manifest.version,
        generated_at: manifest.generated_at,
        endpoint_count: manifest.endpoints.len(),
    })
}

/// Текущий запомненный last-good транспорт (UI показывает его в шапке Settings).
#[tauri::command]
pub async fn transport_last_good(
    runtime: tauri::State<'_, TransportRuntime>,
) -> Result<LastGood, KotoApiError> {
    Ok(runtime.last_good().await)
}

fn parse_mode(mode: &str) -> Result<TransportSelection, KotoApiError> {
    match mode {
        "auto" => Ok(TransportSelection::Auto),
        "direct" => Ok(TransportSelection::Pinned(TransportKind::Direct)),
        "cloudflare" => Ok(TransportSelection::Pinned(TransportKind::Cloudflare)),
        "reality" => Ok(TransportSelection::Pinned(TransportKind::Reality)),
        "hysteria2" => Ok(TransportSelection::Pinned(TransportKind::Hysteria2)),
        "tor" => Ok(TransportSelection::Pinned(TransportKind::Tor)),
        other => Err(KotoApiError {
            status: 0,
            message: format!("Unknown transport mode: {other}"),
        }),
    }
}
