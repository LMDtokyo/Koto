package run.koto.desktop.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Koto icon set — straight port of `icons.jsx` from the design mockup.
 *
 * All line icons are 24×24 viewport, stroke-based (no fills), with `strokeWidth`
 * matching the SVG source (1.3–2.4 depending on glyph). Callers tint via
 * [androidx.compose.material3.Icon]'s `tint` parameter; glyphs themselves use
 * `SolidColor(Color.Unspecified)` so the tint flows through.
 *
 * Icons are `val`-exported (lazy via `by lazy` so they're built only on first
 * lookup — keeps startup cheap and lets the linker strip unused ones).
 */
object KotoIcons {

    val Back          by lazy { stroke24(2f) { moveTo(15f, 5f); lineToRelative(-7f, 7f); lineToRelative(7f, 7f) } }
    val Chevron       by lazy { strokeN(16f, 16f, 1.6f) { moveTo(6f, 3f); lineToRelative(5f, 5f); lineToRelative(-5f, 5f) } }
    val ChevronRight  by lazy { strokeN(16f, 16f, 1.6f) { moveTo(6f, 3f); lineToRelative(5f, 5f); lineToRelative(-5f, 5f) } }
    val ChevronDown   by lazy { strokeN(16f, 16f, 1.6f) { moveTo(3f, 6f); lineToRelative(5f, 5f); lineToRelative(5f, -5f) } }
    val DotCircle     by lazy { stroke24(1.8f) {
        moveTo(12f, 3f); arcToRelative(9f, 9f, 0f, true, false, 0f, 18f); arcToRelative(9f, 9f, 0f, true, false, 0f, -18f)
        moveTo(12f, 9f); arcToRelative(3f, 3f, 0f, true, false, 0f, 6f); arcToRelative(3f, 3f, 0f, true, false, 0f, -6f)
    } }
    val Bot           by lazy { stroke24(1.7f) {
        moveTo(4f, 7f); horizontalLineToRelative(16f); verticalLineToRelative(12f); horizontalLineToRelative(-16f); close()
        moveTo(12f, 4f); verticalLineToRelative(3f)
        moveTo(9f, 4f); horizontalLineToRelative(6f)
        moveTo(2f, 12f); verticalLineToRelative(4f)
        moveTo(22f, 12f); verticalLineToRelative(4f)
        moveTo(9f, 13f); arcToRelative(1.4f, 1.4f, 0f, true, false, 0f, 0.01f)
        moveTo(15f, 13f); arcToRelative(1.4f, 1.4f, 0f, true, false, 0f, 0.01f)
    } }

    val Search        by lazy { stroke24(1.8f) {
        moveTo(11f, 4f); arcToRelative(7f, 7f, 0f, true, false, 0f, 14f)
        arcToRelative(7f, 7f, 0f, true, false, 0f, -14f)
        moveTo(20f, 20f); lineToRelative(-3.5f, -3.5f)
    } }

    val Plus          by lazy { stroke24(2f) { moveTo(12f, 5f); verticalLineToRelative(14f); moveTo(5f, 12f); horizontalLineToRelative(14f) } }
    val Close         by lazy { stroke24(2f) { moveTo(6f, 6f); lineToRelative(12f, 12f); moveTo(18f, 6f); lineToRelative(-12f, 12f) } }

    val Pencil        by lazy { stroke24(1.8f) {
        moveTo(14.5f, 4.5f); lineToRelative(5f, 5f)
        moveTo(4f, 20f); lineToRelative(4f, -1f); lineTo(19.5f, 7.5f)
        arcToRelative(2.12f, 2.12f, 0f, true, false, -3f, -3f)
        lineTo(5f, 16f); lineToRelative(-1f, 4f); close()
    } }

    val Camera        by lazy { stroke24(1.8f) {
        moveTo(3f, 8.5f); arcToRelative(1.5f, 1.5f, 0f, false, true, 1.5f, -1.5f)
        horizontalLineTo(7f); lineToRelative(1.5f, -2.2f)
        arcToRelative(1f, 1f, 0f, false, true, 0.83f, -0.5f)
        horizontalLineToRelative(5.34f); arcToRelative(1f, 1f, 0f, false, true, 0.83f, 0.5f)
        lineTo(17f, 7f); horizontalLineTo(19.5f)
        arcToRelative(1.5f, 1.5f, 0f, false, true, 1.5f, 1.5f)
        verticalLineTo(18f); arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
        horizontalLineTo(5f); arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
        close()
        moveTo(12f, 9.5f); arcToRelative(3.5f, 3.5f, 0f, true, false, 0f, 7f)
        arcToRelative(3.5f, 3.5f, 0f, true, false, 0f, -7f)
    } }

    val Phone         by lazy { stroke24(1.8f) {
        moveTo(22f, 16.92f); verticalLineToRelative(3f)
        arcToRelative(2f, 2f, 0f, false, true, -2.18f, 2f)
        arcToRelative(19.86f, 19.86f, 0f, false, true, -8.63f, -3.07f)
        arcToRelative(19.5f, 19.5f, 0f, false, true, -6f, -6f)
        arcTo(19.86f, 19.86f, 0f, false, true, 2.12f, 4.18f)
        arcTo(2f, 2f, 0f, false, true, 4.11f, 2f)
        horizontalLineToRelative(3f); arcToRelative(2f, 2f, 0f, false, true, 2f, 1.72f)
        curveToRelative(0.127f, 0.96f, 0.361f, 1.903f, 0.7f, 2.81f)
        arcToRelative(2f, 2f, 0f, false, true, -0.45f, 2.11f)
        lineTo(8.09f, 9.91f)
        arcToRelative(16f, 16f, 0f, false, false, 6f, 6f)
        lineToRelative(1.27f, -1.27f)
        arcToRelative(2f, 2f, 0f, false, true, 2.11f, -0.45f)
        curveToRelative(0.907f, 0.339f, 1.85f, 0.573f, 2.81f, 0.7f)
        arcTo(2f, 2f, 0f, false, true, 22f, 16.92f)
        close()
    } }

    val Video         by lazy { stroke24(1.8f) {
        moveTo(23f, 7f); lineToRelative(-7f, 5f); lineToRelative(7f, 5f); close()
        moveTo(3f, 5f); horizontalLineToRelative(12f); arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
        verticalLineToRelative(10f); arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
        horizontalLineTo(3f); arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
        verticalLineTo(7f); arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
    } }

    val Mic           by lazy { stroke24(1.8f) {
        moveTo(9f, 6f); horizontalLineToRelative(6f); verticalLineToRelative(12f); horizontalLineToRelative(-6f); close()
        moveTo(5f, 11f); arcToRelative(7f, 7f, 0f, false, false, 14f, 0f)
        moveTo(12f, 18f); verticalLineToRelative(3f)
    } }

    val Send          by lazy { strokeN(24f, 24f, 2.4f) {
        moveTo(12f, 19f); verticalLineTo(5f)
        moveTo(6f, 11f); lineToRelative(6f, -6f); lineToRelative(6f, 6f)
    } }

    val Check         by lazy { strokeN(14f, 14f, 1.8f) {
        moveTo(2f, 7.5f); lineToRelative(3f, 3f); lineToRelative(7f, -7f)
    } }

    val DoubleCheck   by lazy { strokeN(16f, 16f, 1.6f) {
        moveTo(1f, 8f); lineToRelative(3f, 3f); lineToRelative(6f, -6f)
        moveTo(6f, 11f); lineToRelative(3f, -3f)
        moveTo(9f, 8f); lineToRelative(6f, -6f)
    } }

    val Clock         by lazy { strokeN(14f, 14f, 1.4f) {
        moveTo(7f, 1.5f); arcToRelative(5.5f, 5.5f, 0f, true, false, 0f, 11f)
        arcToRelative(5.5f, 5.5f, 0f, true, false, 0f, -11f)
        moveTo(7f, 4f); verticalLineToRelative(3f); lineToRelative(2f, 1.5f)
    } }

    val Lock          by lazy { strokeN(14f, 14f, 1.3f) {
        moveTo(2.5f, 6f); horizontalLineToRelative(9f); verticalLineToRelative(6f); horizontalLineToRelative(-9f); close()
        moveTo(4.5f, 6f); verticalLineTo(4.5f); arcToRelative(2.5f, 2.5f, 0f, false, true, 5f, 0f); verticalLineTo(6f)
    } }

    val Timer         by lazy { stroke24(1.6f) {
        moveTo(12f, 5f); arcToRelative(7f, 7f, 0f, true, false, 0f, 14f)
        arcToRelative(7f, 7f, 0f, true, false, 0f, -14f)
        moveTo(12f, 9f); verticalLineToRelative(4f); lineToRelative(2.5f, 2f)
        moveTo(9f, 2f); horizontalLineToRelative(6f)
    } }

    val Bell          by lazy { stroke24(1.8f) {
        moveTo(18f, 8f); arcToRelative(6f, 6f, 0f, false, false, -12f, 0f)
        curveToRelative(0f, 7f, -3f, 9f, -3f, 9f)
        horizontalLineToRelative(18f); curveToRelative(0f, 0f, -3f, -2f, -3f, -9f)
        moveTo(13.73f, 21f); arcToRelative(2f, 2f, 0f, false, true, -3.46f, 0f)
    } }

    val Shield        by lazy { stroke24(1.8f) {
        moveTo(12f, 2f); lineTo(4f, 5f); verticalLineToRelative(6f)
        curveToRelative(0f, 5f, 3.5f, 9f, 8f, 10f)
        curveToRelative(4.5f, -1f, 8f, -5f, 8f, -10f); verticalLineTo(5f); close()
    } }

    val Archive       by lazy { stroke24(1.8f) {
        moveTo(3f, 7f); horizontalLineToRelative(18f); verticalLineToRelative(3f); horizontalLineToRelative(-18f); close()
        moveTo(5f, 10f); verticalLineToRelative(10f); horizontalLineToRelative(14f); verticalLineTo(10f)
        moveTo(10f, 14f); horizontalLineToRelative(4f)
    } }

    val Unarchive     by lazy { stroke24(1.8f) {
        moveTo(3f, 7f); horizontalLineToRelative(18f); verticalLineToRelative(3f); horizontalLineToRelative(-18f); close()
        moveTo(5f, 10f); verticalLineToRelative(10f); horizontalLineToRelative(14f); verticalLineTo(10f)
        moveTo(12f, 18f); verticalLineToRelative(-6f)
        moveTo(9f, 15f); lineToRelative(3f, -3f); lineToRelative(3f, 3f)
    } }

    val Pin           by lazy { stroke24(1.8f) {
        moveTo(12f, 2f); verticalLineToRelative(20f)
        moveTo(8f, 6f); horizontalLineToRelative(8f); verticalLineToRelative(6f); horizontalLineToRelative(-8f); close()
    } }

    val Mute          by lazy { stroke24(1.8f) {
        moveTo(11f, 5f); lineToRelative(-4f, 4f); horizontalLineTo(3f); verticalLineToRelative(6f); horizontalLineToRelative(4f); lineToRelative(4f, 4f); close()
        moveTo(16f, 9f); lineToRelative(6f, 6f)
        moveTo(22f, 9f); lineToRelative(-6f, 6f)
    } }

    val CheckCircle   by lazy { stroke24(1.8f) {
        moveTo(12f, 3f); arcToRelative(9f, 9f, 0f, true, false, 0f, 18f)
        arcToRelative(9f, 9f, 0f, true, false, 0f, -18f)
        moveTo(8f, 12f); lineToRelative(3f, 3f); lineToRelative(5f, -6f)
    } }

    val Dot           by lazy { strokeN(16f, 16f, 0f) { moveTo(8f, 5f); arcToRelative(3f, 3f, 0f, true, false, 0f, 6f); arcToRelative(3f, 3f, 0f, true, false, 0f, -6f) } }
    val Trash         by lazy { stroke24(1.8f) {
        moveTo(3f, 6f); horizontalLineToRelative(18f)
        moveTo(8f, 6f); verticalLineTo(4f); horizontalLineToRelative(8f); verticalLineToRelative(2f)
        moveTo(5f, 6f); verticalLineToRelative(14f); arcToRelative(2f, 2f, 0f, false, false, 2f, 2f)
        horizontalLineToRelative(10f); arcToRelative(2f, 2f, 0f, false, false, 2f, -2f); verticalLineTo(6f)
        moveTo(10f, 11f); verticalLineToRelative(6f)
        moveTo(14f, 11f); verticalLineToRelative(6f)
    } }

    val Copy          by lazy { stroke24(1.8f) {
        moveTo(8f, 4f); horizontalLineToRelative(10f); verticalLineToRelative(12f); horizontalLineToRelative(-10f); close()
        moveTo(4f, 8f); verticalLineToRelative(12f); horizontalLineToRelative(12f)
    } }

    val Qr            by lazy { stroke24(1.5f) {
        moveTo(3f, 3f); horizontalLineToRelative(8f); verticalLineToRelative(8f); horizontalLineToRelative(-8f); close()
        moveTo(13f, 3f); horizontalLineToRelative(8f); verticalLineToRelative(8f); horizontalLineToRelative(-8f); close()
        moveTo(3f, 13f); horizontalLineToRelative(8f); verticalLineToRelative(8f); horizontalLineToRelative(-8f); close()
        moveTo(13f, 13f); horizontalLineToRelative(4f); verticalLineToRelative(4f); horizontalLineToRelative(-4f); close()
        moveTo(19f, 13f); verticalLineToRelative(2f)
        moveTo(13f, 19f); horizontalLineToRelative(2f)
        moveTo(17f, 21f); horizontalLineToRelative(4f); verticalLineToRelative(-4f)
    } }

    val Globe         by lazy { stroke24(1.8f) {
        moveTo(12f, 3f); arcToRelative(9f, 9f, 0f, true, false, 0f, 18f)
        arcToRelative(9f, 9f, 0f, true, false, 0f, -18f)
        moveTo(3f, 12f); horizontalLineToRelative(18f)
        moveTo(12f, 3f); arcToRelative(12f, 9f, 0f, true, false, 0f, 18f)
        arcToRelative(12f, 9f, 0f, true, false, 0f, -18f)
    } }

    val Alert         by lazy { stroke24(1.8f) {
        moveTo(12f, 3f); lineToRelative(-10f, 18f); horizontalLineToRelative(20f); close()
        moveTo(12f, 10f); verticalLineToRelative(4f)
        moveTo(12f, 17f); verticalLineToRelative(0.5f)
    } }

    val User          by lazy { stroke24(1.8f) {
        moveTo(12f, 12f); arcToRelative(4f, 4f, 0f, true, false, 0f, -8f)
        arcToRelative(4f, 4f, 0f, true, false, 0f, 8f)
        moveTo(4f, 20f); arcToRelative(8f, 8f, 0f, false, true, 16f, 0f)
    } }

    val Face          by lazy { stroke24(1.8f) {
        moveTo(4f, 4f); horizontalLineTo(8f)
        moveTo(4f, 20f); horizontalLineTo(8f)
        moveTo(16f, 4f); horizontalLineTo(20f)
        moveTo(16f, 20f); horizontalLineTo(20f)
        moveTo(4f, 4f); verticalLineTo(8f)
        moveTo(4f, 16f); verticalLineTo(20f)
        moveTo(20f, 4f); verticalLineTo(8f)
        moveTo(20f, 16f); verticalLineTo(20f)
        moveTo(9f, 10f); verticalLineToRelative(1f)
        moveTo(15f, 10f); verticalLineToRelative(1f)
        moveTo(9f, 15f); curveToRelative(1f, 1f, 2f, 1f, 3f, 1f); reflectiveCurveToRelative(2f, 0f, 3f, -1f)
    } }

    val Reply         by lazy { stroke24(1.6f) {
        moveTo(10f, 8f); verticalLineTo(5f); lineToRelative(-6f, 6f); lineToRelative(6f, 6f); verticalLineToRelative(-3f)
        curveToRelative(4f, 0f, 7f, 1f, 10f, 5f)
        curveToRelative(-1f, -7f, -5f, -11f, -10f, -11f); close()
    } }

    val Forward       by lazy { stroke24(1.6f) {
        moveTo(14f, 8f); verticalLineTo(5f); lineToRelative(6f, 6f); lineToRelative(-6f, 6f); verticalLineToRelative(-3f)
        curveToRelative(-4f, 0f, -7f, 1f, -10f, 5f)
        curveToRelative(1f, -7f, 5f, -11f, 10f, -11f); close()
    } }

    val Sticker       by lazy { stroke24(1.8f) {
        moveTo(4f, 4f); horizontalLineToRelative(12f); verticalLineToRelative(12f); lineToRelative(-6f, 4f); horizontalLineTo(4f); close()
        moveTo(16f, 16f); horizontalLineToRelative(-4f); verticalLineToRelative(4f)
        moveTo(9f, 10f); arcToRelative(0.5f, 0.5f, 0f, true, false, 0f, 0.01f)
        moveTo(13f, 10f); arcToRelative(0.5f, 0.5f, 0f, true, false, 0f, 0.01f)
    } }

    val Emoji         by lazy { stroke24(1.6f) {
        moveTo(12f, 3f); arcToRelative(9f, 9f, 0f, true, false, 0f, 18f)
        arcToRelative(9f, 9f, 0f, true, false, 0f, -18f)
        moveTo(9f, 10f); arcToRelative(0.5f, 0.5f, 0f, true, false, 0f, 0.01f)
        moveTo(15f, 10f); arcToRelative(0.5f, 0.5f, 0f, true, false, 0f, 0.01f)
        moveTo(8f, 14f); curveToRelative(1f, 2f, 2.5f, 3f, 4f, 3f); reflectiveCurveToRelative(3f, -1f, 4f, -3f)
    } }

    val Gift          by lazy { stroke24(1.8f) {
        moveTo(20f, 12f); verticalLineToRelative(8f); horizontalLineToRelative(-16f); verticalLineToRelative(-8f)
        moveTo(3f, 7f); horizontalLineToRelative(18f); verticalLineToRelative(5f); horizontalLineToRelative(-18f); close()
        moveTo(12f, 20f); verticalLineTo(7f)
        moveTo(8.5f, 7f); curveToRelative(-1.5f, -1.5f, -1.5f, -3.5f, 0f, -3.5f); reflectiveCurveToRelative(3.5f, 1.5f, 3.5f, 3.5f)
        moveTo(15.5f, 7f); curveToRelative(1.5f, -1.5f, 1.5f, -3.5f, 0f, -3.5f); reflectiveCurveToRelative(-3.5f, 1.5f, -3.5f, 3.5f)
    } }

    val Image         by lazy { stroke24(1.8f) {
        moveTo(3f, 5f); horizontalLineToRelative(18f); verticalLineToRelative(14f); horizontalLineToRelative(-18f); close()
        moveTo(9f, 10f); arcToRelative(1f, 1f, 0f, true, false, 0f, 0.01f)
        moveTo(21f, 15f); lineToRelative(-5f, -5f); lineToRelative(-10f, 10f)
    } }

    val Document      by lazy { stroke24(1.8f) {
        moveTo(6f, 3f); horizontalLineToRelative(9f); lineToRelative(5f, 5f); verticalLineToRelative(13f); horizontalLineToRelative(-14f); close()
        moveTo(15f, 3f); verticalLineToRelative(5f); horizontalLineToRelative(5f)
        moveTo(9f, 13f); horizontalLineToRelative(6f)
        moveTo(9f, 17f); horizontalLineToRelative(6f)
    } }

    val Location      by lazy { stroke24(1.8f) {
        moveTo(12f, 3f); curveToRelative(-4f, 0f, -7f, 3f, -7f, 7f); curveToRelative(0f, 5f, 7f, 11f, 7f, 11f); reflectiveCurveToRelative(7f, -6f, 7f, -11f); curveToRelative(0f, -4f, -3f, -7f, -7f, -7f); close()
        moveTo(12f, 8f); arcToRelative(2.5f, 2.5f, 0f, true, false, 0f, 5f)
        arcToRelative(2.5f, 2.5f, 0f, true, false, 0f, -5f)
    } }

    val Contact       by lazy { stroke24(1.8f) {
        moveTo(4f, 5f); horizontalLineToRelative(16f); verticalLineToRelative(14f); horizontalLineToRelative(-16f); close()
        moveTo(12f, 12f); arcToRelative(2.5f, 2.5f, 0f, true, false, 0f, -5f)
        arcToRelative(2.5f, 2.5f, 0f, true, false, 0f, 5f)
        moveTo(8f, 17f); arcToRelative(4f, 4f, 0f, false, true, 8f, 0f)
    } }

    val Poll          by lazy { stroke24(1.8f) {
        moveTo(4f, 20f); verticalLineToRelative(-6f); horizontalLineToRelative(3f); verticalLineToRelative(6f); close()
        moveTo(10.5f, 20f); verticalLineTo(10f); horizontalLineToRelative(3f); verticalLineToRelative(10f); close()
        moveTo(17f, 20f); verticalLineTo(5f); horizontalLineToRelative(3f); verticalLineToRelative(15f); close()
    } }

    val Settings      by lazy { stroke24(1.6f) {
        moveTo(12f, 9f); arcToRelative(3f, 3f, 0f, true, false, 0f, 6f)
        arcToRelative(3f, 3f, 0f, true, false, 0f, -6f)
        moveTo(20f, 13f); verticalLineToRelative(-2f); horizontalLineToRelative(-2f); arcToRelative(7f, 7f, 0f, false, false, -0.5f, -2f)
        lineToRelative(1.5f, -1.5f); lineToRelative(-1.5f, -1.5f); lineToRelative(-1.5f, 1.5f)
        arcToRelative(7f, 7f, 0f, false, false, -2f, -0.5f); verticalLineTo(5f); horizontalLineToRelative(-2f); verticalLineToRelative(2f)
        arcToRelative(7f, 7f, 0f, false, false, -2f, 0.5f); lineToRelative(-1.5f, -1.5f); lineToRelative(-1.5f, 1.5f); lineToRelative(1.5f, 1.5f)
        arcToRelative(7f, 7f, 0f, false, false, -0.5f, 2f); horizontalLineTo(4f); verticalLineToRelative(2f); horizontalLineToRelative(2f)
        arcToRelative(7f, 7f, 0f, false, false, 0.5f, 2f); lineToRelative(-1.5f, 1.5f); lineToRelative(1.5f, 1.5f); lineToRelative(1.5f, -1.5f)
        arcToRelative(7f, 7f, 0f, false, false, 2f, 0.5f); verticalLineToRelative(2f); horizontalLineToRelative(2f); verticalLineToRelative(-2f)
        arcToRelative(7f, 7f, 0f, false, false, 2f, -0.5f); lineToRelative(1.5f, 1.5f); lineToRelative(1.5f, -1.5f); lineToRelative(-1.5f, -1.5f)
        arcToRelative(7f, 7f, 0f, false, false, 0.5f, -2f); close()
    } }
}

// ── Internal builders ───────────────────────────────────────────────────────

private fun stroke24(stroke: Float, path: PathBuilder.() -> Unit): ImageVector =
    strokeN(24f, 24f, stroke, path)

private fun strokeN(vw: Float, vh: Float, stroke: Float, path: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name          = "KotoIcon",
        defaultWidth  = vw.dp,
        defaultHeight = vh.dp,
        viewportWidth = vw,
        viewportHeight= vh,
    ).path(
        stroke          = SolidColor(Color.Black),
        strokeLineWidth = stroke,
        strokeLineCap   = StrokeCap.Round,
        strokeLineJoin  = StrokeJoin.Round,
        pathBuilder     = path,
    ).build()
