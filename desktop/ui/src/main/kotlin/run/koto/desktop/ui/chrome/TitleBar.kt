package run.koto.desktop.ui.chrome

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.useResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowState
import org.jetbrains.skia.Image as SkiaImage
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.ColorFilter
import run.koto.desktop.ui.theme.KotoTheme

/**
 * Custom window title bar — Telegram-Desktop-style.
 *
 * Layout (top edge of the window, 36 dp tall):
 *   [🐈 Koto ......................... drag area ........................] [–] [▢] [×]
 *
 *   - The logo + label on the left and all the empty space up to the buttons
 *     is a [WindowDraggableArea], so the user can grab anywhere on the empty
 *     strip to move the window.
 *   - The three chrome buttons are OUTSIDE the drag area (otherwise dragging
 *     them would steal clicks from the OS). They are 46 × 36 px like Windows
 *     convention, with red hover tint on the close button.
 *   - Host window must be created with `undecorated = true, resizable = true`
 *     so the OS doesn't paint its own title bar over this one. Resize from
 *     edges still works on Win/macOS.
 */
@Composable
fun FrameWindowScope.KotoTitleBar(
    windowState       : WindowState,
    isMaximized       : Boolean,
    onToggleMaximize  : () -> Unit,
    onClose           : () -> Unit,
    modifier          : Modifier = Modifier,
) {
    val colors = KotoTheme.colors

    val kotoLogo = remember {
        val bytes = useResource("koto-cat.png") { it.readBytes() }
        BitmapPainter(SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap())
    }

    // Single column root: stable measure pass on maximize/resize (avoids sibling reflow gaps).
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(colors.background),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ── Draggable strip: logo + title + empty space ────────────────────
        WindowDraggableArea(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            Row(
                modifier              = Modifier
                    .fillMaxHeight()
                    // Double-click anywhere on the drag strip toggles maximize,
                    // matching Windows convention. detectTapGestures captures
                    // the double-tap before the drag handler sees it.
                    .pointerInput(onToggleMaximize) {
                        detectTapGestures(onDoubleTap = { onToggleMaximize() })
                    }
                    .padding(horizontal = 12.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Mini logo tile — 18 dp cat on accent tile. Matches brand
                // without competing with the in-content logo.
                Box(
                    modifier          = Modifier
                        .size(22.dp)
                        .clip(KotoTheme.shapes.iconTile)
                        .background(colors.accentGradient),
                    contentAlignment  = Alignment.Center,
                ) {
                    Image(
                        painter            = kotoLogo,
                        contentDescription = null,
                        modifier           = Modifier.size(18.dp),
                    )
                }
                Text(
                    text       = "Koto",
                    style      = KotoTheme.typography.labelLarge,
                    color      = colors.textSecondary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // ── Window chrome buttons ───────────────────────────────────────────
        ChromeButton(
            icon      = TitleBarIcons.Minimize,
            onClick   = { windowState.isMinimized = true },
        )
        ChromeButton(
            icon      = if (isMaximized) TitleBarIcons.Restore else TitleBarIcons.Maximize,
            onClick   = onToggleMaximize,
        )
        ChromeButton(
            icon          = TitleBarIcons.Close,
            onClick       = onClose,
            hoverBgDanger = true,
        )
    }

    // Hairline separator so the bar reads as a distinct region, not as a
    // floating stripe on top of the sidebar.
    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(colors.separator))
    }
}

// ─── Chrome button ──────────────────────────────────────────────────────────

@Composable
private fun ChromeButton(
    icon          : ImageVector,
    onClick       : () -> Unit,
    hoverBgDanger : Boolean = false,
) {
    val colors        = KotoTheme.colors
    val interaction   = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    // Default tint is bright (text color at 80 % alpha) so the icon actually
    // reads against the dark title-bar background. Hovered: full white on red
    // for close, full text color + hover-surface for min/max.
    val tint: Color = when {
        hovered && hoverBgDanger -> Color.White
        hovered                  -> colors.text
        else                     -> colors.text.copy(alpha = 0.72f)
    }

    // Pre-compute hover background so the modifier chain below doesn't need
    // to branch on `hovered` inside a lambda — simpler recomposition path.
    val hoverBg: Color = when {
        hoverBgDanger  -> Color(0xFFE81123)                // Windows close-red
        colors.isLight -> Color.Black.copy(alpha = 0.10f)
        else           -> Color.White.copy(alpha = 0.12f)
    }

    // Modifier order: size → hoverable → clickable → background. Hoverable
    // precedes clickable so their shared InteractionSource gets both hover
    // and press events; background goes last so the hover fill sits above
    // the default area but behind the glyph.
    Box(
        modifier          = Modifier
            .size(width = 46.dp, height = 40.dp)
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication        = null,
                onClick           = onClick,
            )
            .background(if (hovered) hoverBg else Color.Transparent),
        contentAlignment  = Alignment.Center,
    ) {
        // Render via Image + explicit ColorFilter.tint instead of Material3's
        // Icon — the latter depends on LocalContentColor, which can end up
        // as Color.Unspecified in an undecorated window before Material takes
        // over. With Image we drive the tint ourselves, so the glyph is
        // guaranteed to appear.
        Image(
            imageVector        = icon,
            contentDescription = null,
            colorFilter        = ColorFilter.tint(tint),
            modifier           = Modifier.size(14.dp),
        )
    }
}

// ─── Chrome-specific icons (minimal 12×12 glyphs) ───────────────────────────

private object TitleBarIcons {
    val Minimize: ImageVector by lazy {
        vec12(1.6f) { moveTo(2f, 6.5f); lineTo(10f, 6.5f) }
    }
    val Maximize: ImageVector by lazy {
        vec12(1.4f) {
            moveTo(2f, 2f); lineTo(10f, 2f); lineTo(10f, 10f); lineTo(2f, 10f); close()
        }
    }
    val Restore: ImageVector by lazy {
        vec12(1.4f) {
            // back square corner
            moveTo(4f, 2f); lineTo(10f, 2f); lineTo(10f, 8f)
            // front square
            moveTo(2f, 4f); lineTo(8f, 4f); lineTo(8f, 10f); lineTo(2f, 10f); close()
        }
    }
    val Close: ImageVector by lazy {
        vec12(1.7f) {
            moveTo(2.5f, 2.5f); lineTo(9.5f, 9.5f)
            moveTo(9.5f, 2.5f); lineTo(2.5f, 9.5f)
        }
    }
}

private fun vec12(stroke: Float, path: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name          = "ChromeGlyph",
        defaultWidth  = 12.dp,
        defaultHeight = 12.dp,
        viewportWidth = 12f,
        viewportHeight= 12f,
    ).path(
        // Concrete color so the VectorPainter has something opaque to paint.
        // The caller-side `ColorFilter.tint(...)` on [Image] then replaces it
        // with the current tint via SrcIn blend.
        stroke          = SolidColor(Color.White),
        strokeLineWidth = stroke,
        strokeLineCap   = StrokeCap.Round,
        strokeLineJoin  = StrokeJoin.Round,
        pathBuilder     = path,
    ).build()
