/// All errors exposed through the uniffi FFI boundary.
#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum CryptoError {
    #[error("session not initialised for this address — call process_prekey_bundle first")]
    NoSession,

    #[error("decryption failed: {reason}")]
    DecryptionFailed { reason: String },

    #[error("invalid prekey bundle: {reason}")]
    InvalidBundle { reason: String },

    #[error("key generation failed: {reason}")]
    KeyGeneration { reason: String },

    #[error("serialisation error: {reason}")]
    Serialisation { reason: String },

    #[error("internal crypto error: {reason}")]
    Internal { reason: String },
}

impl From<libsignal_protocol::SignalProtocolError> for CryptoError {
    fn from(e: libsignal_protocol::SignalProtocolError) -> Self {
        use libsignal_protocol::SignalProtocolError::*;
        match e {
            NoSenderKeyState { .. } | SessionNotFound(_) => CryptoError::NoSession,
            _ => CryptoError::DecryptionFailed { reason: e.to_string() },
        }
    }
}
