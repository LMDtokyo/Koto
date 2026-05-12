package run.koto.desktop.ui.screens.settings

import org.slf4j.LoggerFactory
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import javax.imageio.ImageWriteParam

/**
 * Defends the upload pipeline against malicious images. The flow:
 *
 *   raw bytes → magic-byte sniff → size cap → dimension probe (no full decode)
 *               → ImageIO.read           ← decode in a sandbox-friendly way
 *               → centred crop to target → bilinear downsample
 *               → ImageIO.write JPEG q=0.9 ← strips EXIF / XMP / ICC / chunks
 *
 * Why each step matters:
 *
 *   1. **Magic-byte sniff.** Filenames are user-controlled and meaningless. We
 *      verify the first ~12 bytes match PNG / JPEG / WebP. SVG, BMP, TIFF are
 *      rejected outright — SVG can carry script-level XML, the others have
 *      historical decoder CVEs that we don't need to support for an avatar.
 *
 *   2. **Size cap before decode.** Bytes count is checked before any parsing,
 *      so a 1 GB tarball masquerading as a JPEG never hits the decoder.
 *
 *   3. **Dimension probe.** Reads only the IHDR-equivalent header via
 *      ImageReader so we can reject decompression bombs before they expand
 *      into hundreds of MB of pixel data.
 *
 *   4. **Re-encode to clean JPEG.** Whatever the user fed us, the upload
 *      blob is now a freshly-generated JPEG with no EXIF location data, no
 *      ICC profile, no XMP, no embedded thumbnails, no random ancillary PNG
 *      chunks. Quality 0.9 is visually loss-free for avatars/banners and
 *      brings most uploads well under 200 KB.
 */
object ImageHardener {

    private val log = LoggerFactory.getLogger(ImageHardener::class.java)

    enum class Kind(
        val maxRawBytes  : Long,
        val targetWidth  : Int,
        val targetHeight : Int,
        val cropAspect   : Float,
    ) {
        Avatar(maxRawBytes = 8L * 1024 * 1024, targetWidth = 512,  targetHeight = 512, cropAspect = 1f),
        Banner(maxRawBytes = 12L * 1024 * 1024, targetWidth = 1500, targetHeight = 500, cropAspect = 3f),
    }

    sealed interface Result {
        data class Ok(val bytes: ByteArray, val contentType: String) : Result
        data class Rejected(val reason: String) : Result
    }

    fun process(rawBytes: ByteArray, kind: Kind): Result {
        if (rawBytes.size.toLong() > kind.maxRawBytes) {
            return Result.Rejected("файл больше ${kind.maxRawBytes / 1024 / 1024} МБ")
        }
        if (detectFormat(rawBytes) == null) {
            return Result.Rejected("формат не поддерживается — нужны PNG, JPEG или WebP")
        }
        val dim = peekDimensions(rawBytes)
            ?: return Result.Rejected("не удалось прочитать изображение")
        if (dim.first > MAX_SIDE || dim.second > MAX_SIDE) {
            return Result.Rejected("слишком большое разрешение — макс ${MAX_SIDE}px")
        }
        if (dim.first.toLong() * dim.second > MAX_PIXELS) {
            return Result.Rejected("слишком большое изображение")
        }

        val src = runCatching { ImageIO.read(ByteArrayInputStream(rawBytes)) }
            .getOrNull()
            ?: return Result.Rejected("не удалось распаковать изображение")
        val target = cropAndScale(src, kind)

        val out = ByteArrayOutputStream()
        val writers = ImageIO.getImageWritersByFormatName("jpeg")
        if (!writers.hasNext()) return Result.Rejected("кодек JPEG недоступен")
        val writer  = writers.next()
        val params  = writer.defaultWriteParam.apply {
            compressionMode    = ImageWriteParam.MODE_EXPLICIT
            compressionQuality = 0.9f
        }
        try {
            ImageIO.createImageOutputStream(out).use { ios ->
                writer.output = ios
                writer.write(null, IIOImage(target, null, null), params)
            }
        } finally {
            writer.dispose()
        }
        val bytes = out.toByteArray()
        log.info(
            "image hardened kind={} src={}x{} -> {}x{} {} -> {} bytes",
            kind, src.width, src.height, target.width, target.height,
            rawBytes.size, bytes.size,
        )
        return Result.Ok(bytes, "image/jpeg")
    }

    /** First 12 bytes — only formats whose decoders we trust. SVG/BMP/TIFF rejected. */
    private fun detectFormat(b: ByteArray): String? {
        if (b.size < 12) return null
        if (b[0] == 0x89.toByte() && b[1] == 0x50.toByte() && b[2] == 0x4E.toByte() && b[3] == 0x47.toByte()) return "png"
        if (b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() && b[2] == 0xFF.toByte()) return "jpeg"
        if (b[0] == 0x52.toByte() && b[1] == 0x49.toByte() && b[2] == 0x46.toByte() && b[3] == 0x46.toByte() &&
            b[8] == 0x57.toByte() && b[9] == 0x45.toByte() && b[10] == 0x42.toByte() && b[11] == 0x50.toByte()
        ) return "webp"
        return null
    }

    /** Read just the dimensions via ImageReader — no full decode, so a
     *  10-billion-pixel header never expands into a heap explosion. */
    private fun peekDimensions(b: ByteArray): Pair<Int, Int>? = runCatching {
        ByteArrayInputStream(b).use { bin ->
            val ios = ImageIO.createImageInputStream(bin) ?: return@runCatching null
            val readers = ImageIO.getImageReaders(ios)
            if (!readers.hasNext()) return@runCatching null
            val reader: ImageReader = readers.next()
            try {
                reader.input = ios
                reader.getWidth(0) to reader.getHeight(0)
            } finally {
                reader.dispose()
            }
        }
    }.getOrNull()

    private fun cropAndScale(src: BufferedImage, kind: Kind): BufferedImage {
        val srcW = src.width
        val srcH = src.height
        val srcAspect = srcW.toFloat() / srcH.toFloat()
        // Centred crop to the kind's aspect ratio.
        val (cropW, cropH) = if (srcAspect > kind.cropAspect) {
            // Source is too wide — clip horizontally.
            (srcH * kind.cropAspect).toInt() to srcH
        } else {
            srcW to (srcW / kind.cropAspect).toInt()
        }
        val cx = (srcW - cropW) / 2
        val cy = (srcH - cropH) / 2

        val out = BufferedImage(kind.targetWidth, kind.targetHeight, BufferedImage.TYPE_INT_RGB)
        val g   = out.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.setRenderingHint(RenderingHints.KEY_RENDERING,     RenderingHints.VALUE_RENDER_QUALITY)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,  RenderingHints.VALUE_ANTIALIAS_ON)
            // Source rect is the centred crop; dest rect is the full target image.
            g.drawImage(
                src,
                0, 0, kind.targetWidth, kind.targetHeight,
                cx, cy, cx + cropW, cy + cropH,
                null,
            )
        } finally {
            g.dispose()
        }
        return out
    }

    private const val MAX_SIDE   = 8192
    private const val MAX_PIXELS = 24_000_000L // ~6000×4000, well under any reasonable photo
}
