package run.koto.desktop.data.local

import org.slf4j.LoggerFactory
import run.koto.desktop.crypto.security.LocalAead
import run.koto.desktop.data.local.db.KotoDb
import run.koto.desktop.domain.model.Session

/**
 * Session tokens are encrypted at rest with the local master key. The SQLite column
 * still stores opaque bytes (hex-encoded because the schema says TEXT), so the
 * encrypted blob survives the TEXT↔BLOB round-trip by being hex-encoded.
 *
 * If the vault is destroyed or the master key rotated, [read] silently drops the row
 * and returns null — the caller re-authenticates and a new row is written.
 */
class SessionStore(
    private val db   : KotoDb,
    private val aead : LocalAead,
) {

    private val log = LoggerFactory.getLogger(SessionStore::class.java)

    fun read(): Session? {
        val row = db.kotoDbQueries.getSession().executeAsOneOrNull() ?: return null
        return try {
            Session(
                accessToken  = aead.decrypt(row.access_token.hexToBytes()).toString(Charsets.UTF_8),
                refreshToken = aead.decrypt(row.refresh_token.hexToBytes()).toString(Charsets.UTF_8),
                accountId    = row.account_id,
                sessionId    = row.session_id,
                expiresAt    = row.expires_at,
            )
        } catch (e: Throwable) {
            log.warn("session decrypt failed, treating as signed-out", e)
            clear()
            null
        }
    }

    fun write(session: Session) {
        db.kotoDbQueries.upsertSession(
            account_id    = session.accountId,
            session_id    = session.sessionId,
            access_token  = aead.encrypt(session.accessToken.toByteArray(Charsets.UTF_8)).toHex(),
            refresh_token = aead.encrypt(session.refreshToken.toByteArray(Charsets.UTF_8)).toHex(),
            expires_at    = session.expiresAt,
        )
    }

    fun clear() {
        db.kotoDbQueries.clearSession()
    }
}

private fun ByteArray.toHex(): String {
    val hex = "0123456789abcdef"
    val out = CharArray(size * 2)
    for (i in indices) {
        val b = this[i].toInt() and 0xFF
        out[i * 2]     = hex[b ushr 4]
        out[i * 2 + 1] = hex[b and 0x0F]
    }
    return String(out)
}

private fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "hex string odd length" }
    val out = ByteArray(length / 2)
    for (i in out.indices) {
        out[i] = ((Character.digit(this[2 * i], 16) shl 4) or
                  Character.digit(this[2 * i + 1], 16)).toByte()
    }
    return out
}
