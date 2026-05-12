package run.koto.desktop.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import run.koto.desktop.crypto.CryptoStore
import run.koto.desktop.crypto.security.LocalAead
import run.koto.desktop.data.local.db.KotoDb

/**
 * All *private* key material is AES-GCM sealed before it touches SQLite. Public-key
 * parts (signed prekey public, signatures) are stored as-is — they're published to the
 * server anyway, so encrypting them locally is theatre.
 *
 * The encrypted columns still share the BLOB type: we wrap plaintext with [LocalAead.encrypt]
 * before write and unwrap on read. If decryption fails (master key rotated, DB corrupted,
 * migration from a plaintext db) the row is treated as absent — the app will ask the user
 * to re-register and the store repopulates cleanly.
 */
class SqlCryptoStore(
    private val db   : KotoDb,
    private val aead : LocalAead,
) : CryptoStore {

    private val log = LoggerFactory.getLogger(SqlCryptoStore::class.java)

    override suspend fun loadIdentity(): CryptoStore.Identity? = withContext(Dispatchers.IO) {
        val row = db.kotoDbQueries.getIdentity().executeAsOneOrNull() ?: return@withContext null
        val keyPair = tryDecrypt(row.identity_key_pair) ?: run {
            log.warn("identity key decrypt failed — wiping row")
            db.kotoDbQueries.clearCrypto()
            return@withContext null
        }
        CryptoStore.Identity(identityKeyPair = keyPair, registrationId = row.registration_id)
    }

    override suspend fun saveIdentity(row: CryptoStore.Identity) {
        withContext(Dispatchers.IO) {
            db.kotoDbQueries.upsertIdentity(aead.encrypt(row.identityKeyPair), row.registrationId)
        }
    }

    override suspend fun loadSignedPrekey(): CryptoStore.SignedPrekey? = withContext(Dispatchers.IO) {
        val row = db.kotoDbQueries.getSignedPrekey().executeAsOneOrNull() ?: return@withContext null
        val priv = tryDecrypt(row.private_key) ?: run {
            log.warn("signed-prekey private decrypt failed — wiping row")
            db.kotoDbQueries.clearCryptoSignedPrekeys()
            return@withContext null
        }
        CryptoStore.SignedPrekey(
            id         = row.id,
            publicKey  = row.public_key,
            privateKey = priv,
            signature  = row.signature,
        )
    }

    override suspend fun saveSignedPrekey(row: CryptoStore.SignedPrekey) {
        withContext(Dispatchers.IO) {
            db.kotoDbQueries.upsertSignedPrekey(
                id          = row.id,
                public_key  = row.publicKey,
                private_key = aead.encrypt(row.privateKey),
                signature   = row.signature,
            )
        }
    }

    override suspend fun loadKyberPrekey(): CryptoStore.KyberPrekey? = withContext(Dispatchers.IO) {
        val row = db.kotoDbQueries.getKyberPrekey().executeAsOneOrNull() ?: return@withContext null
        val serialized = tryDecrypt(row.serialized) ?: run {
            log.warn("kyber-prekey decrypt failed — wiping row")
            db.kotoDbQueries.clearCryptoKyberPrekeys()
            return@withContext null
        }
        CryptoStore.KyberPrekey(id = row.id, serialized = serialized)
    }

    override suspend fun saveKyberPrekey(row: CryptoStore.KyberPrekey) {
        withContext(Dispatchers.IO) {
            db.kotoDbQueries.upsertKyberPrekey(row.id, aead.encrypt(row.serialized))
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            db.kotoDbQueries.clearCrypto()
            db.kotoDbQueries.clearCryptoSignedPrekeys()
            db.kotoDbQueries.clearCryptoKyberPrekeys()
        }
    }

    private fun tryDecrypt(bytes: ByteArray): ByteArray? =
        runCatching { aead.decrypt(bytes) }.getOrNull()
}
