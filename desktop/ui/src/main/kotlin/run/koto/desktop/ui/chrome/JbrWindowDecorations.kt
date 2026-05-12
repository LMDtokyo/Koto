package run.koto.desktop.ui.chrome

import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import java.awt.Window
import java.util.WeakHashMap

// Reflective wrapper around JBR's com.jetbrains.WindowDecorations.
// Degrades cleanly on non-JBR JVMs — Class.forName throws, we return false.
// Ref: https://github.com/JetBrains/JetBrainsRuntimeApi
object JbrWindowDecorations {

    val isAvailable: Boolean by lazy { jbrWindowDecorations() != null }

    /** JBR custom title bar instance per window — avoids re-attaching on every density tick during resize. */
    private val customTitleBarByWindow = WeakHashMap<Window, Any>()

    fun tryAttach(window: ComposeWindow, titleBarHeight: Dp, density: Density): Boolean = try {
        val decorations = jbrWindowDecorations() ?: return false
        val titleBar = decorations.javaClass.getMethod("createCustomTitleBar").invoke(decorations)
            ?: return false
        titleBar.javaClass.getMethod("setHeight", Float::class.javaPrimitiveType)
            .invoke(titleBar, with(density) { titleBarHeight.toPx() })
        decorations.javaClass
            .getMethod("setCustomTitleBar", java.awt.Window::class.java, titleBar.javaClass)
            .invoke(decorations, window, titleBar)
        customTitleBarByWindow[window] = titleBar
        true
    } catch (_: Throwable) {
        false
    }

    fun updateHeight(window: ComposeWindow, titleBarHeight: Dp, density: Density) {
        val tb = customTitleBarByWindow[window]
        if (tb != null) {
            runCatching {
                tb.javaClass.getMethod("setHeight", Float::class.javaPrimitiveType)
                    .invoke(tb, with(density) { titleBarHeight.toPx() })
            }
        } else {
            runCatching { tryAttach(window, titleBarHeight, density) }
        }
    }

    private fun jbrWindowDecorations(): Any? = try {
        Class.forName("com.jetbrains.JBR")
            .getMethod("getWindowDecorations")
            .invoke(null)
    } catch (_: Throwable) {
        null
    }
}
