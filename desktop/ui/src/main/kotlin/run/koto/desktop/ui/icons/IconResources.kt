package run.koto.desktop.ui.icons

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.unit.Density

/**
 * File-based SVG icon pipeline — "Telegram-style" workflow.
 *
 * ## Workflow
 *
 * 1. Drop an SVG into `desktop/ui/src/main/resources/icons/<name>.svg`.
 *    Name in snake_case, no spaces, ASCII only.
 * 2. Reference in Compose as [svgIcon]:
 *    ```
 *    Icon(
 *        painter            = svgIcon("send_outlined"),
 *        contentDescription = null,
 *        tint               = KotoTheme.colors.accent,
 *    )
 *    ```
 * 3. That's it. No codegen, no rebuild scripts — the SVG is loaded from
 *    classpath on first use and cached for the life of the JVM.
 *
 * ## Authoring rules for tintable icons
 *
 * Compose's [androidx.compose.material3.Icon] `tint` parameter applies a
 * color filter over opaque pixels. For this to produce a clean monochrome
 * icon, SVGs MUST be authored as monochrome:
 *   - single color (black is conventional; it gets replaced by the tint)
 *   - no multi-fill gradients
 *   - strokes should set `stroke="currentColor"` or a single solid color
 *
 * Multi-color brand icons (e.g. colored "IC" tiles from Settings) should NOT
 * use `Icon(... tint = ...)` — render them via `Image(painter = svgIcon(...))`
 * instead, which preserves the source colors.
 *
 * ## Viewport / sizing
 *
 * SVGs keep their internal `viewBox` proportions. Size them at the call site:
 *   ```
 *   Icon(painter = svgIcon("send"), ..., modifier = Modifier.size(22.dp))
 *   ```
 *
 * ## Why not [KotoIcons]?
 *
 * - [KotoIcons] holds hand-built `ImageVector`s for the hot-path glyph set
 *   (30+ line icons used all over the UI). Zero-IO, fastest decode, no
 *   classpath lookup, ideal for small simple paths.
 * - [svgIcon] is for anything you'd rather design in Figma/Illustrator and
 *   iterate as a file — custom brand marks, complex logos, category glyphs,
 *   bot icons, emoji-like illustrations. Drop a file, see a result.
 */
@Composable
fun svgIcon(name: String): Painter {
    val density = LocalDensity.current
    return remember(name, density) { loadSvg(name, density) }
}

/**
 * Non-composable accessor for places that need a [Painter] outside a
 * composition (preview tools, snapshot tests). Callers must supply a
 * [Density] manually.
 */
fun svgIcon(name: String, density: Density): Painter = loadSvg(name, density)

// ── Internals ──────────────────────────────────────────────────────────────

private fun loadSvg(name: String, density: Density): Painter {
    val path   = "/icons/$name.svg"
    val stream = IconResources::class.java.getResourceAsStream(path)
        ?: error("SVG icon not found on classpath: $path — drop a file in desktop/ui/src/main/resources/icons/")
    return stream.use { loadSvgPainter(it, density) }
}

/** Marker object — gives us a stable class whose classloader we use for
 *  resource lookups. Keeps the API surface to `svgIcon(...)` only. */
private object IconResources
