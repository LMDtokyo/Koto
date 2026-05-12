package run.koto.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import run.koto.data.remote.api.MediaApi
import run.koto.data.remote.api.UploadUrlRequest
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// ─── Security constants ───────────────────────────────────────────────────────

private const val AVATAR_MAX_PX       = 512                // longest side in pixels
private const val JPEG_QUALITY        = 85
private const val MAX_COMPRESSED_BYTES = 3 * 1024 * 1024  // 3 MB hard ceiling post-compression

private val ALLOWED_IMAGE_MIME = setOf(
    "image/jpeg", "image/jpg", "image/png", "image/webp",
)

// Magic bytes: JPEG = FF D8 FF, PNG = 89 50 4E 47, WebP = 52 49 46 46 (RIFF)
private fun ByteArray.hasValidImageMagic(): Boolean {
    if (size < 4) return false
    val b = this
    return (b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() && b[2] == 0xFF.toByte()) ||  // JPEG
           (b[0] == 0x89.toByte() && b[1] == 0x50.toByte() && b[2] == 0x4E.toByte() && b[3] == 0x47.toByte()) || // PNG
           (b[0] == 0x52.toByte() && b[1] == 0x49.toByte() && b[2] == 0x46.toByte() && b[3] == 0x46.toByte())   // WebP (RIFF)
}

@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaApi: MediaApi,
) {
    // Bare OkHttpClient — no auth interceptor.
    // Presigned MinIO URLs are self-authenticated via query params; adding
    // an Authorization header would cause a MinIO signature mismatch error.
    private val bareHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // ── Avatar upload ─────────────────────────────────────────────────────────

    /**
     * Full avatar pipeline:
     * 1. Validate MIME type via ContentResolver
     * 2. Decode + resize to ≤512 px (memory-safe two-pass)
     * 3. Re-encode as JPEG (strips EXIF / metadata)
     * 4. Validate magic bytes on output
     * 5. Request presigned PUT URL from media service
     * 6. Upload JPEG bytes directly to MinIO
     * Returns the file_id to persist as the user's avatar reference.
     */
    suspend fun uploadAvatar(uri: Uri): Result<String> = runCatching {

        // ── 1. MIME validation ────────────────────────────────────────────────
        val mime = context.contentResolver.getType(uri)?.lowercase()
            ?: throw IOException("Cannot determine file type")
        require(mime in ALLOWED_IMAGE_MIME) {
            "Only JPEG, PNG, and WebP images are supported (got $mime)"
        }

        // ── 2. Decode with memory-safe subsampling ────────────────────────────
        val dims = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, dims)
        }
        require(dims.outWidth > 0 && dims.outHeight > 0) { "Cannot read image dimensions" }

        // Subsample so decoded bitmap fits within ~2× target in memory
        val sampleSize = calculateSampleSize(dims.outWidth, dims.outHeight, AVATAR_MAX_PX * 2)
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val rough = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: throw IOException("Cannot decode image")

        // ── 3. Precise scale + JPEG re-encode (EXIF is implicitly stripped) ───
        val scaled = if (rough.width > AVATAR_MAX_PX || rough.height > AVATAR_MAX_PX) {
            val scale = AVATAR_MAX_PX.toFloat() / maxOf(rough.width, rough.height)
            val w = (rough.width * scale).toInt().coerceAtLeast(1)
            val h = (rough.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(rough, w, h, true)
                .also { if (it !== rough) rough.recycle() }
        } else rough

        val jpeg = ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            scaled.recycle()
            out.toByteArray()
        }

        // ── 4. Output validation ──────────────────────────────────────────────
        require(jpeg.hasValidImageMagic()) { "Output is not a valid image" }
        require(jpeg.size <= MAX_COMPRESSED_BYTES) {
            "Image still too large after compression (${jpeg.size / 1024} KB)"
        }

        // ── 5. Request presigned upload URL ──────────────────────────────────
        val uploadResp = mediaApi.requestUploadUrl(
            UploadUrlRequest(
                content_type = "image/jpeg",
                size_bytes   = jpeg.size.toLong(),
                is_public    = true,   // avatars must be readable by conversation partners
            )
        )

        // ── 6. PUT bytes to MinIO ─────────────────────────────────────────────
        val putReq = Request.Builder()
            .url(uploadResp.upload_url)
            .put(jpeg.toRequestBody("image/jpeg".toMediaType()))
            .build()

        bareHttp.newCall(putReq).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Upload failed: HTTP ${resp.code}")
        }

        uploadResp.file_id
    }

    /** Fetch a short-lived presigned download URL for display. Expires in 1 h. */
    suspend fun getDownloadUrl(fileId: String): Result<String> = runCatching {
        mediaApi.getDownloadUrl(fileId).download_url
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Power-of-two subsampling factor so decoded bitmap fits within [targetPx] px. */
    private fun calculateSampleSize(width: Int, height: Int, targetPx: Int): Int {
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= targetPx) {
            sample *= 2
        }
        return sample
    }
}
