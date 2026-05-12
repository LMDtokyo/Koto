package run.koto.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "koto_account")

@Singleton
class AccountPrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val KEY_ACCOUNT_ID      = stringPreferencesKey("account_id")
    private val KEY_ACCESS_TOKEN    = stringPreferencesKey("access_token")
    private val KEY_REFRESH_TOKEN   = stringPreferencesKey("refresh_token")
    private val KEY_DISPLAY_NAME    = stringPreferencesKey("display_name")
    private val KEY_WRAPPED_PRIV    = stringPreferencesKey("wrapped_priv_key")  // hex
    private val KEY_PUBLIC_KEY      = stringPreferencesKey("public_key")        // hex
    private val KEY_AVATAR_FILE_ID  = stringPreferencesKey("avatar_file_id")    // media service file id
    private val KEY_IDENTITY_KP     = byteArrayPreferencesKey("identity_key_pair") // Signal identity key pair bytes
    private val KEY_CRYPTO_REG_ID   = longPreferencesKey("crypto_registration_id") // Signal registration ID

    // Signal signed prekey (needed to decrypt incoming PreKeySignalMessages)
    private val KEY_SPK_ID          = longPreferencesKey("signed_prekey_id")
    private val KEY_SPK_PUBLIC      = byteArrayPreferencesKey("signed_prekey_public")
    private val KEY_SPK_PRIVATE     = byteArrayPreferencesKey("signed_prekey_private")
    private val KEY_SPK_SIG         = byteArrayPreferencesKey("signed_prekey_sig")

    // Kyber prekey (PQXDH post-quantum ratchet)
    private val KEY_KYBER_ID        = longPreferencesKey("kyber_prekey_id")
    private val KEY_KYBER_SERIAL    = byteArrayPreferencesKey("kyber_prekey_serialized")

    // ── Read ──────────────────────────────────────────────────────────────────

    suspend fun getAccountId(): String?     = get(KEY_ACCOUNT_ID)
    suspend fun getAccessToken(): String?   = get(KEY_ACCESS_TOKEN)
    suspend fun getRefreshToken(): String?  = get(KEY_REFRESH_TOKEN)
    suspend fun getDisplayName(): String?   = get(KEY_DISPLAY_NAME)
    suspend fun getWrappedPrivKey(): String? = get(KEY_WRAPPED_PRIV)
    suspend fun getPublicKey(): String?     = get(KEY_PUBLIC_KEY)
    suspend fun getAvatarFileId(): String?  = get(KEY_AVATAR_FILE_ID)
    suspend fun getIdentityKeyPair(): ByteArray? =
        context.dataStore.data.map { it[KEY_IDENTITY_KP] }.firstOrNull()
    suspend fun getCryptoRegistrationId(): Long? =
        context.dataStore.data.map { it[KEY_CRYPTO_REG_ID] }.firstOrNull()

    fun isRegisteredFlow() = context.dataStore.data.map { prefs ->
        prefs[KEY_ACCOUNT_ID] != null && prefs[KEY_ACCESS_TOKEN] != null
    }

    /** Reactive stream of the current access token — emits on every rotation. */
    fun accessTokenFlow() = context.dataStore.data.map { prefs -> prefs[KEY_ACCESS_TOKEN] }

    // ── Write ─────────────────────────────────────────────────────────────────

    suspend fun saveAccount(
        accountId    : String,
        accessToken  : String,
        refreshToken : String,
        publicKey    : String,
        wrappedPriv  : String,
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACCOUNT_ID]   = accountId
            prefs[KEY_ACCESS_TOKEN] = accessToken
            prefs[KEY_REFRESH_TOKEN]= refreshToken
            prefs[KEY_PUBLIC_KEY]   = publicKey
            prefs[KEY_WRAPPED_PRIV] = wrappedPriv
        }
    }

    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN]  = accessToken
            prefs[KEY_REFRESH_TOKEN] = refreshToken
        }
    }

    suspend fun saveDisplayName(name: String) {
        context.dataStore.edit { it[KEY_DISPLAY_NAME] = name }
    }

    suspend fun saveAvatarFileId(fileId: String) {
        context.dataStore.edit { it[KEY_AVATAR_FILE_ID] = fileId }
    }

    suspend fun saveIdentityKeyPair(bytes: ByteArray) {
        context.dataStore.edit { it[KEY_IDENTITY_KP] = bytes }
    }

    suspend fun saveCryptoRegistrationId(id: Long) {
        context.dataStore.edit { it[KEY_CRYPTO_REG_ID] = id }
    }

    suspend fun saveSignedPrekey(id: Long, public_key: ByteArray, private_key: ByteArray, signature: ByteArray) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SPK_ID]      = id
            prefs[KEY_SPK_PUBLIC]  = public_key
            prefs[KEY_SPK_PRIVATE] = private_key
            prefs[KEY_SPK_SIG]     = signature
        }
    }

    suspend fun getSignedPrekeyId(): Long?      = context.dataStore.data.map { it[KEY_SPK_ID] }.firstOrNull()
    suspend fun getSignedPrekeyPublic(): ByteArray? = context.dataStore.data.map { it[KEY_SPK_PUBLIC] }.firstOrNull()
    suspend fun getSignedPrekeyPrivate(): ByteArray? = context.dataStore.data.map { it[KEY_SPK_PRIVATE] }.firstOrNull()
    suspend fun getSignedPrekeySig(): ByteArray? = context.dataStore.data.map { it[KEY_SPK_SIG] }.firstOrNull()

    suspend fun saveKyberPrekey(id: Long, serialized: ByteArray) {
        context.dataStore.edit { prefs ->
            prefs[KEY_KYBER_ID]     = id
            prefs[KEY_KYBER_SERIAL] = serialized
        }
    }

    suspend fun getKyberPrekeyId(): Long?         = context.dataStore.data.map { it[KEY_KYBER_ID] }.firstOrNull()
    suspend fun getKyberPrekeySerialised(): ByteArray? = context.dataStore.data.map { it[KEY_KYBER_SERIAL] }.firstOrNull()

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    /**
     * Wipe only the session credentials (account ID + JWT + refresh token).
     * Leaves Signal Protocol keys in place so re-login on the same device
     * can reuse the same identity if the user logs back in with a matching
     * recovery flow; the onboarding path in MainActivity re-registers if
     * no account_id is present.
     */
    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_ACCOUNT_ID)
            prefs.remove(KEY_ACCESS_TOKEN)
            prefs.remove(KEY_REFRESH_TOKEN)
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private suspend fun get(key: Preferences.Key<String>): String? =
        context.dataStore.data.map { it[key] }.firstOrNull()
}
