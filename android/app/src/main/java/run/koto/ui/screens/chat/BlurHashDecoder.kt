package run.koto.ui.screens.chat

import android.graphics.Bitmap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign

/**
 * BlurHash decoder — converts a BlurHash string to an Android Bitmap.
 * CH-07: Used as a placeholder while the real image loads via Coil AsyncImage.
 *
 * Algorithm: https://blurha.sh (MIT / public domain)
 * No external dependencies — ~100 lines of pure Kotlin math.
 *
 * @param hash   BlurHash string (from MessageUi.blurHash)
 * @param width  Decode width in pixels (use 32 for placeholder thumbnails)
 * @param height Decode height in pixels (use 32 for placeholder thumbnails)
 * @return ARGB_8888 Bitmap, or a solid gray fallback if the hash is malformed
 */
fun decodeBlurHash(hash: String, width: Int, height: Int): Bitmap {
    // Validate minimum hash length
    if (hash.length < 6) return solidFallback(width, height)

    val sizeFlag   = decode83(hash, 0, 1)
    val numX       = (sizeFlag % 9) + 1
    val numY       = (sizeFlag / 9) + 1
    val expectedLen = 4 + 2 * numX * numY
    if (hash.length != expectedLen) return solidFallback(width, height)

    val quantisedMaximumValue = decode83(hash, 1, 2)
    val maximumValue          = (quantisedMaximumValue + 1) / 166.0f

    val colors = Array(numX * numY) { i ->
        if (i == 0) {
            val int = decode83(hash, 2, 6)
            decodeDC(int)
        } else {
            val int = decode83(hash, 4 + i * 2, 4 + i * 2 + 2)
            decodeAC(int, maximumValue)
        }
    }

    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            var r = 0f; var g = 0f; var b = 0f
            for (j in 0 until numY) {
                for (i in 0 until numX) {
                    val basis = (cos(PI * x * i / width) * cos(PI * y * j / height)).toFloat()
                    val color = colors[j * numX + i]
                    r += color[0] * basis
                    g += color[1] * basis
                    b += color[2] * basis
                }
            }
            pixels[y * width + x] = android.graphics.Color.rgb(
                linearToSrgb(r),
                linearToSrgb(g),
                linearToSrgb(b),
            )
        }
    }

    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}

// ─── Private helpers ──────────────────────────────────────────────────────────

private val BASE83_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#\$%*+,-.:;=?@[]^_{|}~"

private fun decode83(hash: String, start: Int, end: Int): Int {
    var value = 0
    for (i in start until end) {
        val index = BASE83_CHARS.indexOf(hash[i])
        if (index == -1) return 0
        value = value * 83 + index
    }
    return value
}

private fun decodeDC(value: Int): FloatArray {
    val r = value shr 16
    val g = (value shr 8) and 255
    val b = value and 255
    return floatArrayOf(srgbToLinear(r), srgbToLinear(g), srgbToLinear(b))
}

private fun decodeAC(value: Int, maximumValue: Float): FloatArray {
    val qr = value / (19 * 19)
    val qg = (value / 19) % 19
    val qb = value % 19
    return floatArrayOf(
        signedPow2((qr - 9).toFloat() / 9f) * maximumValue,
        signedPow2((qg - 9).toFloat() / 9f) * maximumValue,
        signedPow2((qb - 9).toFloat() / 9f) * maximumValue,
    )
}

private fun srgbToLinear(value: Int): Float {
    val f = value / 255f
    return if (f <= 0.04045f) f / 12.92f else ((f + 0.055f) / 1.055f).pow(2.4f)
}

private fun linearToSrgb(value: Float): Int {
    val clamped = max(0f, min(1f, value))
    return if (clamped <= 0.0031308f) {
        (clamped * 12.92f * 255f + 0.5f).toInt()
    } else {
        ((1.055f * clamped.pow(1f / 2.4f) - 0.055f) * 255f + 0.5f).toInt()
    }
}

private fun signedPow2(value: Float): Float = sign(value) * value * value

private fun solidFallback(width: Int, height: Int): Bitmap =
    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
        it.eraseColor(android.graphics.Color.parseColor("#1E1E2E"))  // KotoPalette.surface1
    }
