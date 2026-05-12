package run.koto.data.repository

import android.util.Base64
import run.koto.crypto.CryptoManager
import run.koto.crypto.KeyManager
import run.koto.crypto.IdentityKeys
import run.koto.crypto.toHex
import run.koto.data.prefs.AccountPrefs
import run.koto.data.remote.api.AuthApi
import run.koto.data.remote.api.PrekeyCertDto
import run.koto.data.remote.api.RegisterRequest
import run.koto.data.remote.api.UploadPrekeysRequest
import run.koto.data.remote.api.UserApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authApi      : AuthApi,
    private val userApi      : UserApi,
    private val accountPrefs : AccountPrefs,
    private val keyManager   : KeyManager,
    private val cryptoManager: CryptoManager,
) {

    // ── Register a new anonymous account ────────────────────────────────────

    suspend fun register(keys: IdentityKeys): Result<String> = runCatching {
        // Sign the public key with the private key as proof-of-possession
        val sig = keyManager.sign(keys.privateKey, keys.publicKey)

        val response = authApi.register(
            RegisterRequest(
                identity_key        = keys.publicKey.toHex(),
                signed_pre_key      = keys.publicKey.toHex(),
                signed_pre_key_sig  = sig.toHex(),
                signed_pre_key_id   = 1,
            )
        )

        // Wrap private key with Android Keystore before persisting
        val wrappedPriv = keyManager.wrapPrivateKey(keys.privateKey)

        accountPrefs.saveAccount(
            accountId    = response.account_id,
            accessToken  = response.access_token,
            refreshToken = response.refresh_token,
            publicKey    = keys.publicKey.toHex(),
            wrappedPriv  = wrappedPriv.toHex(),
        )

        val accountId = response.account_id

        // Generate Signal Protocol keys and upload public parts to the key server.
        // Non-fatal: messaging will fail gracefully if this step errors.
        runCatching {
            val bundle = cryptoManager.generateAndSaveKeys(accountId)
            userApi.uploadPrekeys(UploadPrekeysRequest(
                identity_key    = Base64.encodeToString(bundle.identityPublicKey, Base64.NO_WRAP),
                registration_id = bundle.registrationId.toInt(),
                signed_prekey   = PrekeyCertDto(
                    id         = bundle.signedPrekey.id.toInt(),
                    public_key = Base64.encodeToString(bundle.signedPrekey.publicKey, Base64.NO_WRAP),
                    signature  = Base64.encodeToString(bundle.signedPrekey.signature, Base64.NO_WRAP),
                ),
                kyber_prekey    = PrekeyCertDto(
                    id         = bundle.kyberPrekey.id.toInt(),
                    public_key = Base64.encodeToString(bundle.kyberPrekey.publicKey, Base64.NO_WRAP),
                    signature  = Base64.encodeToString(bundle.kyberPrekey.signature, Base64.NO_WRAP),
                ),
            ))
        }

        accountId
    }

    // ── Check if account already registered locally ──────────────────────────

    suspend fun isRegistered(): Boolean = accountPrefs.getAccountId() != null

    suspend fun getAccountId(): String? = accountPrefs.getAccountId()

    suspend fun getDisplayName(): String = accountPrefs.getDisplayName() ?: ""
}
