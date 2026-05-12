// Koto crypto core — wraps Signal's libsignal-protocol crate and exposes a
// clean, minimal API via uniffi (generates Kotlin + Swift bindings).
//
// Protocol: X3DH (session establishment) + PQXDH (Kyber) + Double Ratchet
// Source:   https://github.com/signalapp/libsignal
//
// Each KotoCrypto instance represents ONE local account.
// It is safe to share across threads (all state is behind Arc<Mutex<…>>).

use std::sync::{Arc, Mutex};
use std::time::SystemTime;

use once_cell::sync::Lazy;
use tokio::runtime::Runtime;

use bip39::{Language, Mnemonic};
use hkdf::Hkdf;
use sha2::Sha256;

use libsignal_protocol::{
    CiphertextMessage, CiphertextMessageType,
    DeviceId,
    GenericSignedPreKey,
    IdentityKeyPair,
    InMemSignalProtocolStore,
    KeyPair,
    KyberPreKeyId, KyberPreKeyRecord,
    PreKeyBundle, PreKeyId, PreKeyRecord,
    PrivateKey,
    ProtocolAddress,
    SignedPreKeyId, SignedPreKeyRecord,
    Timestamp,
    kem,
    message_decrypt, message_encrypt,
    process_prekey_bundle,
};

pub mod error;
pub use error::CryptoError;

// ── Wire up uniffi scaffolding (proc-macro approach — no UDL file) ───────────
uniffi::setup_scaffolding!();

// ── Global Tokio runtime ──────────────────────────────────────────────────────
// libsignal-protocol's store traits are async. We block on them using this
// single shared runtime so the uniffi-exported sync functions work correctly.
static RT: Lazy<Runtime> = Lazy::new(|| {
    Runtime::new().expect("koto-crypto: failed to build Tokio runtime")
});

// Convenience: device-1 constant used everywhere.
fn device1() -> DeviceId {
    DeviceId::new(1).expect("1 is a valid DeviceId")
}

// ── Data types shared with Kotlin/Swift via uniffi ───────────────────────────

/// Keys generated once at account creation. Upload the public parts to the
/// Koto key server (PUT /v1/keys); store the full bundle locally.
#[derive(uniffi::Record)]
pub struct RegistrationBundle {
    /// Serialised IdentityKeyPair bytes (store in Android Keystore / iOS Secure Enclave).
    pub identity_key_pair: Vec<u8>,
    /// Public identity key — this IS the user's account address on the network.
    pub identity_public_key: Vec<u8>,
    /// Random 31-bit registration ID (sent to server, used in PreKeyBundle).
    pub registration_id: u32,
    /// The one current signed prekey (rotate every ~2 weeks).
    pub signed_prekey: SignedPreKeyData,
    /// Batch of one-time prekeys (100 keys; top up when server runs low).
    pub prekeys: Vec<PreKeyData>,
    /// One Kyber1024 prekey (PQXDH post-quantum ratchet). Rotate with signed prekey.
    pub kyber_prekey: KyberPreKeyData,
}

#[derive(uniffi::Record)]
pub struct PreKeyData {
    pub id: u32,
    pub public_key: Vec<u8>,
    /// Private key bytes — store securely, never send to server.
    pub private_key: Vec<u8>,
}

#[derive(uniffi::Record)]
pub struct SignedPreKeyData {
    pub id: u32,
    pub public_key: Vec<u8>,
    pub signature: Vec<u8>,        // Ed25519 signature over the public key
    pub private_key: Vec<u8>,      // Store securely
}

#[derive(uniffi::Record)]
pub struct KyberPreKeyData {
    pub id: u32,
    pub public_key: Vec<u8>,       // Kyber1024 public key bytes — upload to server
    pub signature: Vec<u8>,        // Ed25519 signature — upload to server
    /// Full serialised KyberPreKeyRecord bytes — persist locally, pass to load_kyber_prekeys.
    pub serialized: Vec<u8>,
}

/// Bob's published keys (fetched from GET /v1/keys/{accountId}).
#[derive(uniffi::Record)]
pub struct PreKeyBundleInput {
    pub registration_id: u32,
    pub device_id: u32,
    pub identity_key: Vec<u8>,
    pub signed_prekey_id: u32,
    pub signed_prekey_public: Vec<u8>,
    pub signed_prekey_sig: Vec<u8>,
    /// Optional one-time prekey (server picks one per session establishment).
    pub prekey_id: Option<u32>,
    pub prekey_public: Option<Vec<u8>>,
    /// Kyber1024 prekey (PQXDH).
    pub kyber_prekey_id: u32,
    pub kyber_prekey_public: Vec<u8>,
    pub kyber_prekey_sig: Vec<u8>,
}

// ── KotoCrypto ────────────────────────────────────────────────────────────────

/// The main crypto context for one Koto account.
///
/// Create once per account; persist `identity_key_pair_bytes` and
/// `registration_id` in secure storage, reload on each app launch.
#[derive(uniffi::Object)]
pub struct KotoCrypto {
    store: Mutex<InMemSignalProtocolStore>,
    local_address: ProtocolAddress,
}

#[uniffi::export]
impl KotoCrypto {
    /// Create a new KotoCrypto from a previously generated identity key pair.
    ///
    /// `identity_key_pair_bytes`: bytes returned by `generate_registration_bundle()`.
    /// `registration_id`: the u32 from `RegistrationBundle`.
    /// `account_id`: the user's hex public key (their address on the network).
    #[uniffi::constructor]
    pub fn new(
        identity_key_pair_bytes: Vec<u8>,
        registration_id: u32,
        account_id: String,
    ) -> Result<Arc<Self>, CryptoError> {
        let identity_key_pair =
            IdentityKeyPair::try_from(identity_key_pair_bytes.as_slice())
                .map_err(|e: libsignal_protocol::SignalProtocolError| {
                    CryptoError::KeyGeneration { reason: e.to_string() }
                })?;

        let store = InMemSignalProtocolStore::new(identity_key_pair, registration_id)
            .map_err(|e| CryptoError::Internal { reason: e.to_string() })?;

        let local_address = ProtocolAddress::new(account_id, device1());

        Ok(Arc::new(Self {
            store: Mutex::new(store),
            local_address,
        }))
    }

    // ── Session establishment (X3DH + PQXDH) ─────────────────────────────────

    /// Process Bob's PreKeyBundle to establish an X3DH + Kyber session.
    /// Call this ONCE before the first encrypted message to a new contact.
    pub fn process_prekey_bundle(
        self: Arc<Self>,
        their_account_id: String,
        bundle: PreKeyBundleInput,
    ) -> Result<(), CryptoError> {
        let device_id = DeviceId::try_from(bundle.device_id)
            .unwrap_or_else(|_| device1());
        let remote_address = ProtocolAddress::new(their_account_id, device_id);

        let identity_key = libsignal_protocol::IdentityKey::decode(&bundle.identity_key)
            .map_err(|e| CryptoError::InvalidBundle { reason: e.to_string() })?;

        let signed_prekey_public = libsignal_protocol::PublicKey::deserialize(&bundle.signed_prekey_public)
            .map_err(|e| CryptoError::InvalidBundle { reason: e.to_string() })?;

        let kyber_prekey_public = kem::PublicKey::deserialize(&bundle.kyber_prekey_public)
            .map_err(|e| CryptoError::InvalidBundle { reason: e.to_string() })?;

        let prekey = match (bundle.prekey_id, bundle.prekey_public) {
            (Some(id), Some(pub_bytes)) => {
                let pk = libsignal_protocol::PublicKey::deserialize(&pub_bytes)
                    .map_err(|e| CryptoError::InvalidBundle { reason: e.to_string() })?;
                Some((PreKeyId::from(id), pk))
            }
            _ => None,
        };

        let pb = PreKeyBundle::new(
            bundle.registration_id,
            device_id,
            prekey,
            SignedPreKeyId::from(bundle.signed_prekey_id),
            signed_prekey_public,
            bundle.signed_prekey_sig,
            KyberPreKeyId::from(bundle.kyber_prekey_id),
            kyber_prekey_public,
            bundle.kyber_prekey_sig,
            identity_key,
        ).map_err(|e| CryptoError::InvalidBundle { reason: e.to_string() })?;

        let mut store = self.store.lock().unwrap();
        let InMemSignalProtocolStore {
            ref mut session_store,
            ref mut identity_store,
            ..
        } = *store;

        RT.block_on(async {
            process_prekey_bundle(
                &remote_address,
                session_store,
                identity_store,
                &pb,
                SystemTime::now(),
                &mut rand::rng(),
            )
            .await
        })
        .map_err(CryptoError::from)
    }

    // ── Encryption ────────────────────────────────────────────────────────────

    /// Encrypt `plaintext` for `their_account_id`.
    ///
    /// Returns raw ciphertext bytes (includes message type byte so `decrypt`
    /// can choose the right decode path — PreKeySignalMessage vs SignalMessage).
    pub fn encrypt(
        self: Arc<Self>,
        their_account_id: String,
        plaintext: Vec<u8>,
    ) -> Result<Vec<u8>, CryptoError> {
        let remote_address = ProtocolAddress::new(their_account_id, device1());
        let local_address = self.local_address.clone();
        let mut store = self.store.lock().unwrap();
        let InMemSignalProtocolStore {
            ref mut session_store,
            ref mut identity_store,
            ..
        } = *store;

        let ciphertext = RT.block_on(async {
            message_encrypt(
                &plaintext,
                &remote_address,
                &local_address,
                session_store,
                identity_store,
                SystemTime::now(),
                &mut rand::rng(),
            ).await
        }).map_err(CryptoError::from)?;

        // Prefix one byte for the message type so `decrypt` knows which
        // variant to deserialise: 2 = SignalMessage, 3 = PreKeySignalMessage.
        let type_byte = ciphertext.message_type() as u8;
        let mut out = vec![type_byte];
        out.extend_from_slice(ciphertext.serialize());
        Ok(out)
    }

    // ── Decryption ────────────────────────────────────────────────────────────

    /// Decrypt a message received from `their_account_id`.
    ///
    /// Handles both SignalMessage (ongoing ratchet) and PreKeySignalMessage
    /// (first message in a new session — runs X3DH + Kyber automatically).
    ///
    /// For PreKeySignalMessage to succeed, the relevant PreKey, SignedPreKey,
    /// and KyberPreKey must have been loaded via the load_* methods.
    pub fn decrypt(
        self: Arc<Self>,
        their_account_id: String,
        ciphertext: Vec<u8>,
    ) -> Result<Vec<u8>, CryptoError> {
        if ciphertext.is_empty() {
            return Err(CryptoError::DecryptionFailed { reason: "empty ciphertext".into() });
        }

        let type_byte = ciphertext[0];
        let body = &ciphertext[1..];
        let remote_address = ProtocolAddress::new(their_account_id, device1());
        let local_address = self.local_address.clone();

        let msg = match type_byte {
            t if t == CiphertextMessageType::Whisper as u8 => {
                CiphertextMessage::SignalMessage(
                    libsignal_protocol::SignalMessage::try_from(body)
                        .map_err(|e| CryptoError::DecryptionFailed { reason: e.to_string() })?,
                )
            }
            t if t == CiphertextMessageType::PreKey as u8 => {
                CiphertextMessage::PreKeySignalMessage(
                    libsignal_protocol::PreKeySignalMessage::try_from(body)
                        .map_err(|e| CryptoError::DecryptionFailed { reason: e.to_string() })?,
                )
            }
            _ => return Err(CryptoError::DecryptionFailed {
                reason: format!("unknown message type byte: {}", type_byte),
            }),
        };

        let mut store = self.store.lock().unwrap();
        let InMemSignalProtocolStore {
            ref mut session_store,
            ref mut identity_store,
            ref mut pre_key_store,
            ref signed_pre_key_store,
            ref mut kyber_pre_key_store,
            ..
        } = *store;

        RT.block_on(async {
            message_decrypt(
                &msg,
                &remote_address,
                &local_address,
                session_store,
                identity_store,
                pre_key_store,
                signed_pre_key_store,
                kyber_pre_key_store,
                &mut rand::rng(),
            ).await
        }).map_err(CryptoError::from)
    }

    // ── PreKey management ────────────────────────────────────────────────────

    /// Load one-time prekeys into the store (call on startup from local DB).
    pub fn load_prekeys(
        self: Arc<Self>,
        prekeys: Vec<PreKeyData>,
    ) -> Result<(), CryptoError> {
        let mut store = self.store.lock().unwrap();
        for pk in prekeys {
            let kp = KeyPair::from_public_and_private(&pk.public_key, &pk.private_key)
                .map_err(|e| CryptoError::KeyGeneration { reason: e.to_string() })?;
            let record = PreKeyRecord::new(PreKeyId::from(pk.id), &kp);
            RT.block_on(async {
                use libsignal_protocol::PreKeyStore;
                store.pre_key_store.save_pre_key(PreKeyId::from(pk.id), &record).await
            }).map_err(|e| CryptoError::Internal { reason: e.to_string() })?;
        }
        Ok(())
    }

    /// Load signed prekeys into the store (call on startup from local DB).
    pub fn load_signed_prekeys(
        self: Arc<Self>,
        signed_prekeys: Vec<SignedPreKeyData>,
    ) -> Result<(), CryptoError> {
        let mut store = self.store.lock().unwrap();
        for spk in signed_prekeys {
            let kp = KeyPair::from_public_and_private(&spk.public_key, &spk.private_key)
                .map_err(|e| CryptoError::KeyGeneration { reason: e.to_string() })?;
            let record = SignedPreKeyRecord::new(
                SignedPreKeyId::from(spk.id),
                Timestamp::from_epoch_millis(0),
                &kp,
                &spk.signature,
            );
            RT.block_on(async {
                use libsignal_protocol::SignedPreKeyStore;
                store.signed_pre_key_store.save_signed_pre_key(SignedPreKeyId::from(spk.id), &record).await
            }).map_err(|e| CryptoError::Internal { reason: e.to_string() })?;
        }
        Ok(())
    }

    /// Load kyber prekeys into the store (call on startup from local DB).
    pub fn load_kyber_prekeys(
        self: Arc<Self>,
        kyber_prekeys: Vec<KyberPreKeyData>,
    ) -> Result<(), CryptoError> {
        let mut store = self.store.lock().unwrap();
        for kpk in kyber_prekeys {
            let record = KyberPreKeyRecord::deserialize(&kpk.serialized)
                .map_err(|e| CryptoError::Internal { reason: e.to_string() })?;
            RT.block_on(async {
                use libsignal_protocol::KyberPreKeyStore;
                store.kyber_pre_key_store.save_kyber_pre_key(KyberPreKeyId::from(kpk.id), &record).await
            }).map_err(|e| CryptoError::Internal { reason: e.to_string() })?;
        }
        Ok(())
    }

    // ── Public key accessors ──────────────────────────────────────────────────

    /// Returns the local account's public identity key bytes.
    pub fn public_identity_key(self: Arc<Self>) -> Vec<u8> {
        let store = self.store.lock().unwrap();
        RT.block_on(async {
            use libsignal_protocol::IdentityKeyStore;
            store.identity_store.get_identity_key_pair().await
        })
        .map(|kp: IdentityKeyPair| kp.identity_key().serialize().to_vec())
        .unwrap_or_default()
    }
}

// ── Free functions (no KotoCrypto instance needed) ────────────────────────────

/// HKDF info string for deriving the Curve25519 identity scalar from a BIP39
/// seed. Versioned so future key types (backup, device) can use the same
/// mnemonic without correlation.
const IDENTITY_INFO: &[u8] = b"koto/identity-key/v1";

/// Generate a fresh registration bundle for a new account.
///
/// Identity key is freshly random — the user has no recovery phrase. Use
/// [`generate_registration_bundle_from_seed`] when the caller wants a
/// deterministic identity that can be re-derived from a BIP39 mnemonic.
#[uniffi::export]
pub fn generate_registration_bundle(registration_id: u32) -> Result<RegistrationBundle, CryptoError> {
    let identity_key_pair = IdentityKeyPair::generate(&mut rand::rng());
    build_registration_bundle(identity_key_pair, registration_id)
}

/// Generate a registration bundle whose identity key is deterministically
/// derived from a BIP39 mnemonic. Same phrase ⇒ same identity public key ⇒
/// same backend `account_id` (since the server hex-encodes the identity key).
///
/// Prekeys (signed, Kyber, one-time) stay random per call — deriving them
/// from the seed too would break forward secrecy on multi-device recovery.
#[uniffi::export]
pub fn generate_registration_bundle_from_seed(
    seed_phrase: Vec<String>,
    registration_id: u32,
) -> Result<RegistrationBundle, CryptoError> {
    let identity_key_pair = identity_key_pair_from_seed(&seed_phrase)?;
    build_registration_bundle(identity_key_pair, registration_id)
}

/// Re-derive only the identity public key from a BIP39 mnemonic, without
/// materialising prekeys. Used by the desktop UI to preview the resolved
/// `account_id` before triggering a full registration / restore call.
#[uniffi::export]
pub fn identity_public_key_from_seed(seed_phrase: Vec<String>) -> Result<Vec<u8>, CryptoError> {
    let pair = identity_key_pair_from_seed(&seed_phrase)?;
    Ok(pair.identity_key().serialize().to_vec())
}

fn identity_key_pair_from_seed(seed_phrase: &[String]) -> Result<IdentityKeyPair, CryptoError> {
    // Reconstruct + parse strictly as English. `parse_in` validates checksum.
    let phrase = seed_phrase.join(" ");
    let mnemonic = Mnemonic::parse_in(Language::English, &phrase)
        .map_err(|e| CryptoError::KeyGeneration { reason: format!("invalid mnemonic: {e}") })?;

    // BIP39 PBKDF2-HMAC-SHA512: phrase + "" passphrase → 64-byte seed.
    let bip39_seed = mnemonic.to_seed("");

    // HKDF-SHA256 with versioned info → 32-byte Curve25519 scalar input.
    // PrivateKey::deserialize clamps internally per RFC 7748, so any 32 bytes
    // map to a valid scalar.
    let mut scalar = [0u8; 32];
    Hkdf::<Sha256>::new(None, &bip39_seed)
        .expand(IDENTITY_INFO, &mut scalar)
        .map_err(|e| CryptoError::KeyGeneration { reason: e.to_string() })?;

    let private_key = PrivateKey::deserialize(&scalar)
        .map_err(|e| CryptoError::KeyGeneration { reason: e.to_string() })?;

    IdentityKeyPair::try_from(private_key)
        .map_err(|e| CryptoError::KeyGeneration { reason: e.to_string() })
}

fn build_registration_bundle(
    identity_key_pair: IdentityKeyPair,
    registration_id: u32,
) -> Result<RegistrationBundle, CryptoError> {
    let mut rng = rand::rng();

    let spk_kp = KeyPair::generate(&mut rng);
    let spk_sig = identity_key_pair
        .private_key()
        .calculate_signature(&spk_kp.public_key.serialize(), &mut rng)
        .map_err(|e| CryptoError::KeyGeneration { reason: e.to_string() })?;

    let signed_prekey = SignedPreKeyData {
        id:          1,
        public_key:  spk_kp.public_key.serialize().to_vec(),
        signature:   spk_sig.to_vec(),
        private_key: spk_kp.private_key.serialize().to_vec(),
    };

    let kyber_record = KyberPreKeyRecord::generate(
        kem::KeyType::Kyber1024,
        KyberPreKeyId::from(1u32),
        identity_key_pair.private_key(),
    ).map_err(|e| CryptoError::KeyGeneration { reason: e.to_string() })?;

    let kyber_prekey = KyberPreKeyData {
        id:          1,
        public_key:  kyber_record.get_storage().public_key.clone(),
        signature:   kyber_record.get_storage().signature.clone(),
        serialized:  kyber_record.serialize()
            .map_err(|e| CryptoError::Internal { reason: e.to_string() })?,
    };

    let prekeys: Vec<PreKeyData> = (1u32..=100)
        .map(|id| {
            let kp = KeyPair::generate(&mut rng);
            PreKeyData {
                id,
                public_key:  kp.public_key.serialize().to_vec(),
                private_key: kp.private_key.serialize().to_vec(),
            }
        })
        .collect();

    Ok(RegistrationBundle {
        identity_key_pair:   identity_key_pair.serialize().to_vec(),
        identity_public_key: identity_key_pair.identity_key().serialize().to_vec(),
        registration_id,
        signed_prekey,
        prekeys,
        kyber_prekey,
    })
}

#[cfg(test)]
mod seed_recovery_tests {
    use super::*;

    fn fresh_phrase() -> Vec<String> {
        // Known-good BIP39 phrase (test vector from the BIP-0039 spec).
        "legal winner thank year wave sausage worth useful legal winner thank yellow"
            .split_whitespace()
            .map(String::from)
            .collect()
    }

    #[test]
    fn same_seed_yields_same_identity_pubkey() {
        let phrase = fresh_phrase();
        let a = generate_registration_bundle_from_seed(phrase.clone(), 1).unwrap();
        let b = generate_registration_bundle_from_seed(phrase, 2).unwrap();
        assert_eq!(a.identity_public_key, b.identity_public_key);
        assert_eq!(a.identity_key_pair, b.identity_key_pair);
        // Prekeys MUST differ — they are freshly random per call.
        assert_ne!(a.signed_prekey.public_key, b.signed_prekey.public_key);
    }

    #[test]
    fn different_seeds_yield_different_identities() {
        let p1 = fresh_phrase();
        let p2 = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
            .split_whitespace()
            .map(String::from)
            .collect::<Vec<_>>();
        let a = generate_registration_bundle_from_seed(p1, 1).unwrap();
        let b = generate_registration_bundle_from_seed(p2, 1).unwrap();
        assert_ne!(a.identity_public_key, b.identity_public_key);
    }

    #[test]
    fn preview_matches_full_bundle() {
        let phrase = fresh_phrase();
        let preview = identity_public_key_from_seed(phrase.clone()).unwrap();
        let bundle  = generate_registration_bundle_from_seed(phrase, 1).unwrap();
        assert_eq!(preview, bundle.identity_public_key);
    }

    #[test]
    fn invalid_phrase_rejected() {
        let bad = vec!["not".into(), "a".into(), "real".into(), "phrase".into()];
        let r = generate_registration_bundle_from_seed(bad, 1);
        assert!(r.is_err());
    }
}
