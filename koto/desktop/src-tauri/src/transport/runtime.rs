//! Auto-fallback runtime: probes endpoints out of the manifest in order, picks
//! the first one that answers a health-check, and remembers the winner so
//! the next launch retries it first. No multi-hop/Reality logic yet — this
//! layer only orchestrates which endpoint goes first; the actual tunnels are
//! plug-replaced as `Reality` / `Tor` arrive.

use std::sync::Arc;

use serde::{Deserialize, Serialize};
use tokio::sync::Mutex;

use super::{
    direct::DirectTunnel,
    manifest::{ManifestError, SignedManifest, TransportManifest},
    reality::RealityTunnel,
    tor::TorTunnel,
    TransportEndpoint, TransportError, TransportKind, TransportSelection, Tunnel,
};

/// Persisted "last working transport" — a single hint we try first on next
/// fallback to avoid re-probing the entire list every boot.
#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct LastGood {
    pub kind: Option<TransportKind>,
    pub label: String,
    pub picked_at_unix: i64,
}

/// In-process cache of the last successful pick + текущий активный manifest.
/// Tauri-side keeps single instance in `tauri::State`, so every command goes
/// through it. На старте manifest = bundled_default; при удачном
/// `refresh_from_url` он подменяется на проверенный signed-update.
pub struct TransportRuntime {
    last_good: Arc<Mutex<LastGood>>,
    manifest: Arc<Mutex<TransportManifest>>,
}

impl Default for TransportRuntime {
    fn default() -> Self {
        Self {
            last_good: Arc::new(Mutex::new(LastGood::default())),
            manifest: Arc::new(Mutex::new(TransportManifest::bundled_default())),
        }
    }
}

#[derive(Debug, thiserror::Error)]
pub enum RefreshError {
    #[error("network error: {0}")]
    Network(String),
    #[error("manifest verification: {0}")]
    Verification(#[from] ManifestError),
    #[error("manifest version {got} is older than current {current}")]
    Stale { got: u32, current: u32 },
}

impl TransportRuntime {
    pub fn new() -> Self {
        Self::default()
    }

    /// Текущий manifest (bundled либо последний удачный signed-update).
    pub async fn current_manifest(&self) -> TransportManifest {
        self.manifest.lock().await.clone()
    }

    /// Snapshot of the last working endpoint, if any. UI uses it to render
    /// "current connection: <label>".
    pub async fn last_good(&self) -> LastGood {
        self.last_good.lock().await.clone()
    }

    /// Подтянуть signed-update с указанного URL, проверить подпись, при успехе
    /// и более высокой версии — заменить in-memory manifest.
    pub async fn refresh_from_url(
        &self,
        url: &str,
        public_key_hex: &str,
    ) -> Result<TransportManifest, RefreshError> {
        let client = reqwest::Client::builder()
            .timeout(std::time::Duration::from_secs(10))
            .build()
            .map_err(|e| RefreshError::Network(e.to_string()))?;
        let resp = client
            .get(url)
            .send()
            .await
            .map_err(|e| RefreshError::Network(e.to_string()))?;
        if !resp.status().is_success() {
            return Err(RefreshError::Network(format!(
                "HTTP {}",
                resp.status().as_u16()
            )));
        }
        let signed: SignedManifest = resp
            .json()
            .await
            .map_err(|e| RefreshError::Network(e.to_string()))?;
        let verified = signed.verify(public_key_hex)?.clone();

        let mut current = self.manifest.lock().await;
        if verified.version <= current.version && current.version != 0 {
            return Err(RefreshError::Stale {
                got: verified.version,
                current: current.version,
            });
        }
        *current = verified.clone();
        Ok(verified)
    }

    /// Replace the last-good hint. Caller is `pick_active` after a successful
    /// probe — outsiders shouldn't be touching this.
    async fn remember(&self, ep: &TransportEndpoint) {
        let mut guard = self.last_good.lock().await;
        *guard = LastGood {
            kind: Some(ep.kind),
            label: ep.label.clone(),
            picked_at_unix: now_unix(),
        };
    }

    /// Run the auto-fallback cascade. Strategy:
    /// - `Pinned(kind)` → only that one endpoint, error if it fails.
    /// - `Auto` → try last-good first (if any), then the rest in `order`,
    ///   stopping at the first that probes Ok.
    pub async fn pick_active(
        &self,
        manifest: &TransportManifest,
        selection: TransportSelection,
    ) -> Result<TransportEndpoint, TransportError> {
        let mut endpoints = manifest.endpoints.clone();
        endpoints.sort_by_key(|e| e.order);

        match selection {
            TransportSelection::Pinned(kind) => {
                let ep = endpoints
                    .into_iter()
                    .find(|e| e.kind == kind)
                    .ok_or_else(|| {
                        TransportError::Unreachable(format!("no endpoint for {kind:?}"))
                    })?;
                probe_endpoint(&ep).await?;
                self.remember(&ep).await;
                Ok(ep)
            }
            TransportSelection::Auto => {
                let last = self.last_good.lock().await.kind;
                let ordered = reorder_with_hint(endpoints, last);
                let mut last_err: Option<TransportError> = None;
                for ep in ordered {
                    match probe_endpoint(&ep).await {
                        Ok(()) => {
                            self.remember(&ep).await;
                            return Ok(ep);
                        }
                        Err(e) => {
                            last_err = Some(e);
                            continue;
                        }
                    }
                }
                Err(last_err
                    .unwrap_or_else(|| TransportError::Unreachable("no endpoints".into())))
            }
        }
    }
}

fn reorder_with_hint(
    mut endpoints: Vec<TransportEndpoint>,
    hint: Option<TransportKind>,
) -> Vec<TransportEndpoint> {
    let Some(kind) = hint else { return endpoints };
    if let Some(idx) = endpoints.iter().position(|e| e.kind == kind) {
        let preferred = endpoints.remove(idx);
        let mut out = Vec::with_capacity(endpoints.len() + 1);
        out.push(preferred);
        out.extend(endpoints);
        return out;
    }
    endpoints
}

async fn probe_endpoint(ep: &TransportEndpoint) -> Result<(), TransportError> {
    match ep.kind {
        TransportKind::Direct | TransportKind::Cloudflare => {
            DirectTunnel::new(ep.clone()).probe().await
        }
        TransportKind::Reality => {
            // Sidecar поднимается только на время probe'а; child убивается на
            // drop, поэтому в production здесь нужно завести «long-running
            // tunnel» (см. TODO ниже) — но для health-check этого хватает.
            let tunnel = RealityTunnel::start(ep.clone()).await?;
            tunnel.probe().await
        }
        TransportKind::Tor => {
            let tunnel = TorTunnel::start(ep.clone()).await?;
            tunnel.probe().await
        }
        // Hysteria2 пока не реализован — оставляем NotImplemented, cascade
        // пропустит и пойдёт дальше.
        TransportKind::Hysteria2 => Err(TransportError::NotImplemented(ep.kind)),
    }
}

fn now_unix() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_secs() as i64)
        .unwrap_or(0)
}
