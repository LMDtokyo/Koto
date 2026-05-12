package run.koto.desktop.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import run.koto.desktop.data.local.db.KotoDb
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/**
 * Creates the SQLDelight driver pointing at the per-user app data directory.
 *
 * Schema bootstrap and migration are delegated to [KotoDb.Schema] — the plugin
 * generates the `create()` + `migrate()` statements from the .sq file.
 */
object DatabaseFactory {

    /** Per-user app-data directory for Koto — DB, cache, vault all live under here. */
    fun appDataDir(): Path {
        val dir = when {
            System.getProperty("os.name").startsWith("Windows", ignoreCase = true) ->
                Path.of(System.getenv("APPDATA") ?: System.getProperty("user.home"), "Koto")
            System.getProperty("os.name").contains("Mac", ignoreCase = true) ->
                Path.of(System.getProperty("user.home"), "Library", "Application Support", "Koto")
            else ->
                Path.of(
                    System.getenv("XDG_DATA_HOME") ?: (System.getProperty("user.home") + "/.local/share"),
                    "koto",
                )
        }
        Files.createDirectories(dir)
        return dir
    }

    fun create(): KotoDb {
        val dbFile = ensureDbPath()
        val driver: SqlDriver = JdbcSqliteDriver(
            url        = "jdbc:sqlite:${dbFile.toAbsolutePath()}",
            properties = Properties().apply {
                setProperty("foreign_keys", "true")
                setProperty("journal_mode", "WAL")
            },
        )
        // Every CREATE TABLE uses `IF NOT EXISTS`, so calling Schema.create on every
        // boot is idempotent for existing DBs yet still adds tables introduced in newer
        // app versions without wiping user data. Once the schema hits v1.0 we switch to
        // SQLDelight's formal `.sqm` migration pipeline.
        KotoDb.Schema.create(driver)
        // secure_delete=ON overwrites deleted rows with zeros before releasing pages —
        // forensic recovery from the SQLite file becomes much harder. This is a runtime
        // PRAGMA and must be re-applied on every connection; JDBC holds a single long
        // connection so one call at startup suffices.
        driver.execute(null, "PRAGMA secure_delete = ON;", 0)
        return KotoDb(driver)
    }

    private fun ensureDbPath(): Path = appDataDir().resolve("koto.db")
}
