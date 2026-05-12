package run.koto.desktop

import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import javax.swing.JRootPane

/**
 * Desktop paint/render bootstrap — must run at the very start of [main], before any
 * Skiko/Swing/Compose class loads (system properties are read once by native peers).
 *
 * References:
 * - White flashes on resize (dark UI): JetBrains/compose-multiplatform#3420 — AWT clears
 *   newly exposed pixels before Skia repaints; match window background + reduce GL tear.
 * - Swing resize flicker: `sun.awt.noerasebackground` / JDK-6558510 (X11 erase background).
 * - Skiko: [SkikoProperties] `skiko.buffering`, `skiko.rendering.linux.waitForFrameVsyncOnRedrawImmediately`,
 *   `skiko.renderApi` / `SKIKO_RENDER_API` (GraphicsApi names).
 */
object DesktopRendering {

    fun applyBeforeSkikoOrSwingLoads() {
        System.setProperty("sun.awt.noerasebackground", "true")

        val os = System.getProperty("os.name").lowercase()
        if (os.contains("win")) {
            System.setProperty("sun.java2d.noddraw", "true")
            System.setProperty("sun.java2d.d3d", "false")
            System.setProperty("sun.java2d.opengl", "false")
        }

        if (System.getProperty("skiko.buffering") == null) {
            System.setProperty("skiko.buffering", "TRIPLE")
        }

        val fromEnv = !System.getenv("SKIKO_RENDER_API").isNullOrBlank()
        val fromProp = !System.getProperty("skiko.renderApi").isNullOrBlank()
        if (!fromEnv && !fromProp) {
            when {
                os.contains("linux") || os.contains("nix") -> {
                    System.setProperty("skiko.rendering.linux.waitForFrameVsyncOnRedrawImmediately", "true")
                    val preferGpu = System.getProperty("koto.gpu")?.toBoolean() == true
                        || System.getenv("KOTO_PREFER_GPU") == "1"
                    if (!preferGpu) {
                        System.setProperty("skiko.renderApi", "SOFTWARE_FAST")
                    }
                }
                else -> Unit
            }
        }
    }

    /** Keep Swing layers behind Skia tinted — mitigates white bands on resize (CMP #3420). */
    fun syncAwtWindowBackground(window: ComposeWindow, surface: Color) {
        val c = surface.toAwtRgb()
        window.background = c
        val cp = window.contentPane
        cp.background = c
        if (cp is javax.swing.JComponent) {
            cp.isOpaque = true
        }
        window.rootPane.let { rp: JRootPane ->
            rp.background = c
            rp.isOpaque = true
        }
        window.layeredPane?.let { lp ->
            lp.background = c
            if (lp is javax.swing.JComponent) lp.isOpaque = true
        }
        window.glassPane?.let { gp ->
            gp.background = c
            if (gp is javax.swing.JComponent) {
                gp.isOpaque = false
            }
        }
    }

    private fun Color.toAwtRgb(): java.awt.Color {
        val s = convert(ColorSpaces.Srgb)
        val r = (s.red * 255f).toInt().coerceIn(0, 255)
        val g = (s.green * 255f).toInt().coerceIn(0, 255)
        val b = (s.blue * 255f).toInt().coerceIn(0, 255)
        val a = (s.alpha * 255f).toInt().coerceIn(0, 255)
        return java.awt.Color(r, g, b, a)
    }
}
