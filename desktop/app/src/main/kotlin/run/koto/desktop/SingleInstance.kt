package run.koto.desktop

import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Single-instance guard. Tries to acquire an **exclusive** [FileLock] on a lock file
 * in the per-user app-data directory. If the lock is already held by another process
 * ([tryAcquire] returns false) the caller should exit without launching a second
 * window — matches how Telegram Desktop, Signal Desktop and most native apps behave.
 *
 * The OS releases the lock automatically if the process is killed hard (SIGKILL /
 * taskkill /F) so a crashed prior instance doesn't prevent the next launch. The file
 * itself is harmless if left behind.
 *
 * Intentionally NOT wired into a shutdown hook — the JVM releases file locks on
 * process exit regardless of how the process terminates. Adding a shutdown hook
 * would only add a failure mode (hook runs twice, hook throws, etc).
 */
class SingleInstance(private val lockPath: Path) : AutoCloseable {

    private val log = LoggerFactory.getLogger(SingleInstance::class.java)

    @Volatile private var channel : FileChannel? = null
    @Volatile private var lock    : FileLock?    = null

    /**
     * Returns `true` iff this process now holds the lock. After success the caller
     * should start normally; after failure the caller should log and `exitProcess(0)`.
     */
    fun tryAcquire(): Boolean {
        return try {
            Files.createDirectories(lockPath.parent)
            val ch = FileChannel.open(
                lockPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE,
            )
            val l = ch.tryLock() ?: run {
                ch.close()
                log.info("another Koto instance holds {}", lockPath)
                return false
            }
            channel = ch
            lock    = l
            log.info("single-instance lock acquired on {}", lockPath)
            true
        } catch (e: IOException) {
            log.warn("could not acquire single-instance lock; allowing start anyway", e)
            // Better to run without the lock than to block legitimate launches if the
            // filesystem is weird (network drive, permissions quirk).
            true
        }
    }

    override fun close() {
        runCatching { lock?.release() }
        runCatching { channel?.close() }
        lock    = null
        channel = null
    }
}
