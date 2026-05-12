package run.koto.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

data class PersistedWindowState(
    val x          : Int?,
    val y          : Int?,
    val width      : Int,
    val height     : Int,
    val maximized  : Boolean,
)

class WindowStateStore(private val path: Path) {

    fun load(): PersistedWindowState? = runCatching {
        if (!Files.exists(path)) return null
        val props = Properties().apply { Files.newInputStream(path).use { load(it) } }
        PersistedWindowState(
            x         = props.getProperty("x")?.toIntOrNull(),
            y         = props.getProperty("y")?.toIntOrNull(),
            width     = props.getProperty("width")?.toIntOrNull()  ?: return null,
            height    = props.getProperty("height")?.toIntOrNull() ?: return null,
            maximized = props.getProperty("maximized")?.toBoolean() ?: false,
        )
    }.getOrNull()

    fun save(state: PersistedWindowState) {
        runCatching {
            Files.createDirectories(path.parent)
            val props = Properties().apply {
                state.x?.let { setProperty("x", it.toString()) }
                state.y?.let { setProperty("y", it.toString()) }
                setProperty("width",     state.width.toString())
                setProperty("height",    state.height.toString())
                setProperty("maximized", state.maximized.toString())
            }
            Files.newOutputStream(path).use { props.store(it, "Koto window state") }
        }
    }
}
