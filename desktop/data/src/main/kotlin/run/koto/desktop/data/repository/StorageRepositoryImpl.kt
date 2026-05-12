package run.koto.desktop.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import run.koto.desktop.data.local.db.KotoDb
import run.koto.desktop.domain.model.ConversationStorage
import run.koto.desktop.domain.model.StorageInfo
import run.koto.desktop.domain.repository.StorageRepository
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.fileSize
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

/**
 * On-disk storage accounting for the local Koto data dir.
 *
 *   - SQLite size = `koto.db` + `-wal` + `-shm` (the WAL files are non-trivial
 *     while a write transaction is open, so summing all three gives a stable
 *     answer for the user).
 *   - Cache size = recursive sum of `cache/` if it exists.
 *   - Per-conversation byte count = SUM(LENGTH(ciphertext)) grouped by
 *     conversation_id; this is "what the SQLite file holds for that chat",
 *     not the original plaintext size.
 */
class StorageRepositoryImpl(
    private val db          : KotoDb,
    private val appDataDir  : Path,
) : StorageRepository {

    private val log = LoggerFactory.getLogger(StorageRepositoryImpl::class.java)

    override suspend fun snapshot(): StorageInfo = withContext(Dispatchers.IO) {
        val dbBytes    = sumDbBytes()
        val cacheBytes = sumDirBytes(appDataDir.resolve("cache"))
        val rows       = db.kotoDbQueries.selectStorageBreakdown().executeAsList()
        val total      = db.kotoDbQueries.countAllMessages().executeAsOne()
        val perConv = rows.map {
            ConversationStorage(
                conversationId = it.id,
                displayName    = it.display_name,
                peerAccountId  = it.peer_account_id,
                avatarUrl      = it.avatar_url,
                messageCount   = it.message_count,
                bytes          = it.bytes,
            )
        }
        StorageInfo(
            dbBytes           = dbBytes,
            cacheBytes        = cacheBytes,
            totalBytes        = dbBytes + cacheBytes,
            perConversation   = perConv,
            totalMessageCount = total,
        )
    }

    override suspend fun clearMediaCache() {
        withContext(Dispatchers.IO) {
            wipeDir(appDataDir.resolve("cache"))
        }
    }

    override suspend fun clearAllMessages() {
        withContext(Dispatchers.IO) {
            db.kotoDbQueries.wipeAllMessages()
            // Reset the conversation preview text/time so list rows don't keep showing
            // a "[N bytes]" preview for a chat that now has zero messages.
            // VACUUM releases the freed SQLite pages back to the OS.
            db.kotoDbQueries.vacuum()
            wipeDir(appDataDir.resolve("cache"))
        }
    }

    override suspend fun clearConversationMessages(conversationId: String) {
        withContext(Dispatchers.IO) {
            db.kotoDbQueries.deleteMessagesByConv(conversationId)
        }
    }

    private fun sumDbBytes(): Long {
        val dbFile = appDataDir.resolve("koto.db")
        val parts  = listOf(dbFile, appDataDir.resolve("koto.db-wal"), appDataDir.resolve("koto.db-shm"))
        return parts.sumOf { p -> runCatching { if (Files.exists(p)) p.fileSize() else 0L }.getOrElse { 0L } }
    }

    private fun sumDirBytes(dir: Path): Long {
        if (!Files.exists(dir) || !dir.isDirectory()) return 0L
        return runCatching {
            Files.walk(dir).use { stream ->
                stream.filter { it.isRegularFile() }
                    .mapToLong { runCatching { it.fileSize() }.getOrElse { 0L } }
                    .sum()
            }
        }.getOrElse {
            log.warn("sumDirBytes failed dir={}", dir, it)
            0L
        }
    }

    private fun wipeDir(dir: Path) {
        if (!Files.exists(dir)) return
        runCatching {
            Files.walk(dir).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach { p ->
                    runCatching { Files.deleteIfExists(p) }
                }
            }
            Files.createDirectories(dir)
        }.onFailure { log.warn("wipeDir failed dir={}", dir, it) }
    }
}
