package run.koto.desktop.domain.repository

import kotlinx.coroutines.flow.Flow
import run.koto.desktop.domain.model.Account
import run.koto.desktop.domain.model.LinkedDevice
import run.koto.desktop.domain.model.Session

interface AuthRepository {
    val session: Flow<Session?>

    /**
     * Create a new account. The identity key is deterministically derived from
     * [seedPhrase] via BIP39 + HKDF-SHA256, so the same phrase always resolves
     * to the same `account_id`. The phrase itself is shown to the user once
     * (in the Register UI) and never persisted by the client.
     */
    suspend fun register(displayName: String, seedPhrase: List<String>): Result<Account>

    /**
     * Restore an existing account from its seed phrase on a new device.
     * Re-derives the same identity key and resumes the session if the backend
     * recognises the resulting `account_id`.
     */
    suspend fun restore(seedPhrase: List<String>): Result<Account>

    /**
     * Resolve the `account_id` (hex of the 32-byte identity public key) that
     * a given seed phrase will produce, without persisting or registering
     * anything. Used by the UI to show the user their resolved Koto ID before
     * they commit. Returns failure for invalid mnemonics.
     */
    suspend fun previewKotoId(seedPhrase: List<String>): Result<String>

    suspend fun refresh(): Result<Session>
    suspend fun signOut()
    suspend fun currentAccountId(): String?

    /**
     * List every active device the current account is signed in on.
     * The caller marks "this device" by comparing each entry's id to
     * [Session.sessionId] from the active session.
     */
    suspend fun listDevices(): Result<List<LinkedDevice>>

    /** Sign out a single linked device by its session id. */
    suspend fun revokeDevice(sessionId: String): Result<Unit>
}
