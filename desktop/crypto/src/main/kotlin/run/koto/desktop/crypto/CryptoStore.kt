package run.koto.desktop.crypto

/**
 * Persistence contract for Signal Protocol key material. Implemented by :data
 * (SQLDelight-backed) and injected into [KotoCryptoProvider] so `:crypto` does
 * not take a hard dependency on any storage engine.
 *
 * Rationale: identity + signed prekey + Kyber prekey must be restored on every
 * app start — the Rust `InMemSignalProtocolStore` does not persist itself.
 * One-time prekeys (100 generated at registration) are also consumed via X3DH;
 * persisting unconsumed OTPKs is a Phase 05.1 follow-up.
 */
interface CryptoStore {

    data class Identity(
        val identityKeyPair : ByteArray,
        val registrationId  : Long,
    )

    data class SignedPrekey(
        val id         : Long,
        val publicKey  : ByteArray,
        val privateKey : ByteArray,
        val signature  : ByteArray,
    )

    data class KyberPrekey(
        val id         : Long,
        val serialized : ByteArray,
    )

    suspend fun loadIdentity(): Identity?
    suspend fun saveIdentity(row: Identity)

    suspend fun loadSignedPrekey(): SignedPrekey?
    suspend fun saveSignedPrekey(row: SignedPrekey)

    suspend fun loadKyberPrekey(): KyberPrekey?
    suspend fun saveKyberPrekey(row: KyberPrekey)

    suspend fun clear()
}
