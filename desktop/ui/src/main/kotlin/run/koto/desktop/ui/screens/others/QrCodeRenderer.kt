package run.koto.desktop.ui.screens.others

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Render [text] as a QR code into [modifier]'s box. Uses ZXing's `QRCodeWriter`
 * to produce a `BitMatrix`, then paints filled squares onto a Compose Canvas
 * — no AWT BufferedImage roundtrip, so it stays snappy on theme changes.
 *
 * Padding around the matrix is left to the caller's modifier; the matrix
 * itself fills the available square area.
 */
@Composable
fun QrCode(
    text       : String,
    modifier   : Modifier = Modifier,
    foreground : Color    = Color.Black,
    background : Color    = Color.White,
) {
    val matrix = remember(text) {
        val hints = mapOf(
            EncodeHintType.MARGIN              to 0,
            EncodeHintType.ERROR_CORRECTION    to ErrorCorrectionLevel.M,
        )
        QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 256, 256, hints)
    }
    Canvas(modifier = modifier) {
        val cells = matrix.width
        val cellSize = size.minDimension / cells
        // Background pass — single rectangle, then dark cells over it.
        drawRect(color = background, topLeft = Offset.Zero, size = size)
        for (y in 0 until cells) {
            for (x in 0 until cells) {
                if (matrix.get(x, y)) {
                    drawRect(
                        color   = foreground,
                        topLeft = Offset(x * cellSize, y * cellSize),
                        size    = Size(cellSize, cellSize),
                    )
                }
            }
        }
    }
}
