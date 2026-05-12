//! Bridge-list manifest. Загружается из bundled-default + опционально
//! обновляется с https://updates.koto.run/bridges.json (signed Ed25519).
//!
//! Hard-coded fallback: всегда есть как минимум `direct` на koto.run, чтобы
//! при первом запуске даже без сети до updates-сервера было от чего стартовать.
//!
//! Формат signed-update:
//! ```json
//! {
//!   "manifest":   { "version":N, "generated_at":..., "endpoints":[...] },
//!   "signature":  "<hex 64-byte ed25519 sig>",
//!   "key_id":     "primary"
//! }
//! ```
//! Подпись считается над каноническим JSON-сериализацией поля `manifest`
//! (serde_json::to_vec — детерминированный порядок ключей через структуры).

use ed25519_dalek::{Signature, Verifier, VerifyingKey};
use serde::{Deserialize, Serialize};

use super::{TransportEndpoint, TransportKind};

/// Hard-coded публичный ключ updates.koto.run (Ed25519, 32 байта).
/// TODO: заменить на реальный ключ перед prod-релизом. Пока — placeholder
/// (нули → любая подпись будет невалидна, но это безопасный default).
pub const UPDATES_PUBLIC_KEY_HEX: &str =
    "0000000000000000000000000000000000000000000000000000000000000000";

/// URL канала обновлений. Можно переопределить через env при сборке.
pub const UPDATES_URL: &str = "https://updates.koto.run/bridges.json";

/// Манифест транспортов. Версионирован, подписан Ed25519.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TransportManifest {
    pub version: u32,
    pub generated_at: i64,
    pub endpoints: Vec<TransportEndpoint>,
}

/// Конверт со signed-update: внутренний манифест + подпись над его JSON.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SignedManifest {
    pub manifest: TransportManifest,
    /// hex-encoded 64-byte Ed25519 signature над `serde_json::to_vec(&manifest)`.
    pub signature: String,
    /// ID ключа (на будущее под key-rotation).
    #[serde(default)]
    pub key_id: String,
}

#[derive(Debug, thiserror::Error)]
pub enum ManifestError {
    #[error("invalid public key: {0}")]
    InvalidPublicKey(String),
    #[error("invalid signature encoding: {0}")]
    InvalidSignatureEncoding(String),
    #[error("signature verification failed")]
    SignatureMismatch,
    #[error("manifest serialization failed: {0}")]
    Serialize(String),
}

impl TransportManifest {
    /// Hard-coded fallback на случай отсутствия обновлений.
    pub fn bundled_default() -> Self {
        Self {
            version: 1,
            generated_at: 0,
            endpoints: vec![
                TransportEndpoint {
                    kind: TransportKind::Direct,
                    rest_base_url: "https://api.koto.run".into(),
                    ws_base_url: "wss://ws.koto.run/ws".into(),
                    order: 0,
                    label: "Прямое подключение".into(),
                    reality: None,
                    tor: None,
                },
                // Cloudflare-edge зеркало того же бэка (TODO: настроить Worker).
                TransportEndpoint {
                    kind: TransportKind::Cloudflare,
                    rest_base_url: "https://edge.koto-cdn.net".into(),
                    ws_base_url: "wss://edge-ws.koto-cdn.net/ws".into(),
                    order: 1,
                    label: "Через Cloudflare".into(),
                    reality: None,
                    tor: None,
                },
                // Bridges (заглушка — будут добавляться через signed-update).
            ],
        }
    }
}

impl SignedManifest {
    /// Проверка подписи против хардкоженного публичного ключа Ed25519.
    /// Возвращает ссылку на манифест, если подпись валидна.
    pub fn verify(&self, public_key_hex: &str) -> Result<&TransportManifest, ManifestError> {
        let pk_bytes = hex::decode(public_key_hex)
            .map_err(|e| ManifestError::InvalidPublicKey(e.to_string()))?;
        if pk_bytes.len() != 32 {
            return Err(ManifestError::InvalidPublicKey(format!(
                "expected 32 bytes, got {}",
                pk_bytes.len()
            )));
        }
        let pk_array: [u8; 32] = pk_bytes
            .try_into()
            .map_err(|_| ManifestError::InvalidPublicKey("length conversion".into()))?;
        let verifying_key = VerifyingKey::from_bytes(&pk_array)
            .map_err(|e| ManifestError::InvalidPublicKey(e.to_string()))?;

        let sig_bytes = hex::decode(&self.signature)
            .map_err(|e| ManifestError::InvalidSignatureEncoding(e.to_string()))?;
        if sig_bytes.len() != 64 {
            return Err(ManifestError::InvalidSignatureEncoding(format!(
                "expected 64 bytes, got {}",
                sig_bytes.len()
            )));
        }
        let sig_array: [u8; 64] = sig_bytes
            .try_into()
            .map_err(|_| ManifestError::InvalidSignatureEncoding("length conversion".into()))?;
        let signature = Signature::from_bytes(&sig_array);

        let payload = serde_json::to_vec(&self.manifest)
            .map_err(|e| ManifestError::Serialize(e.to_string()))?;

        verifying_key
            .verify(&payload, &signature)
            .map_err(|_| ManifestError::SignatureMismatch)?;
        Ok(&self.manifest)
    }
}
