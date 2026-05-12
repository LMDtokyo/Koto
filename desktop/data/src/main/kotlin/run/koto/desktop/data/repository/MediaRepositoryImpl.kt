package run.koto.desktop.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import run.koto.desktop.data.remote.api.MediaApi
import run.koto.desktop.domain.DomainError
import run.koto.desktop.domain.model.UploadedFile
import run.koto.desktop.domain.repository.MediaRepository

/**
 * Two-step upload: request presigned URL → PUT bytes straight to object storage.
 * The storage is MinIO in dev, S3-compatible in production; the flow is identical.
 *
 * Desktop quirk: the dev media-service is configured with
 * `MINIO_PUBLIC_ENDPOINT=10.0.2.2:9000` so Android-emulator clients can resolve
 * the host's MinIO. From the desktop, `10.0.2.2` is meaningless. We rewrite
 * the URL host to localhost while preserving the original `Host:` header so
 * SigV4's signature check still passes.
 */
class MediaRepositoryImpl(
    private val mediaApi: MediaApi,
) : MediaRepository {

    private val log = LoggerFactory.getLogger(MediaRepositoryImpl::class.java)

    override suspend fun upload(
        contentType : String,
        bytes       : ByteArray,
        isPublic    : Boolean,
    ): Result<UploadedFile> = runCatching {
        if (bytes.size > MAX_FILE_BYTES) throw DomainError.InvalidInput("file exceeds 100MB limit")
        val ticket = withContext(Dispatchers.IO) {
            mediaApi.requestUploadUrl(contentType, bytes.size.toLong(), isPublic)
        }
        val rewritten = rewriteEmulatorHost(ticket.upload_url)
        withContext(Dispatchers.IO) {
            mediaApi.putToPresignedUrl(
                presignedUrl = rewritten.url,
                contentType  = contentType,
                body         = bytes,
                hostOverride = rewritten.originalHost,
            )
        }
        UploadedFile(
            fileId      = ticket.file_id,
            contentType = contentType,
            sizeBytes   = bytes.size.toLong(),
            isPublic    = isPublic,
        )
    }.onFailure { log.warn("media upload failed contentType={} size={}", contentType, bytes.size, it) }

    override suspend fun downloadUrl(fileId: String): Result<String> = runCatching {
        val raw = withContext(Dispatchers.IO) { mediaApi.requestDownloadUrl(fileId) }.download_url
        // Caller (e.g. Coil) cannot easily preserve the SigV4-signed Host
        // header, so a rewritten URL would 403. Hand the original URL back
        // — callers that need the bytes locally should use [downloadBytes]
        // instead, which fetches via our HTTP client with the proper Host.
        raw
    }.onFailure { log.warn("download url fetch failed id={}", fileId, it) }

    override suspend fun downloadBytes(fileId: String): Result<ByteArray> = runCatching {
        val raw       = withContext(Dispatchers.IO) { mediaApi.requestDownloadUrl(fileId) }.download_url
        val rewritten = rewriteEmulatorHost(raw)
        withContext(Dispatchers.IO) {
            mediaApi.getBytes(rewritten.url, hostOverride = rewritten.originalHost)
        }
    }.onFailure { log.warn("download bytes failed id={}", fileId, it) }

    private data class Rewritten(val url: String, val originalHost: String?)

    /** If [presignedUrl] contains the Android-emulator host alias, swap it
     *  for `127.0.0.1` and return the original host so the caller can still
     *  send a matching Host header. Otherwise return the URL unchanged. */
    private fun rewriteEmulatorHost(presignedUrl: String): Rewritten {
        val emulatorHost = "10.0.2.2"
        val target       = "127.0.0.1"
        if (!presignedUrl.contains("//$emulatorHost")) return Rewritten(presignedUrl, null)
        // Capture the original "host:port" so we can echo it as Host header.
        val match = Regex("""//$emulatorHost(:\d+)?""").find(presignedUrl)
        val origHostPort = match?.value?.removePrefix("//")
        return Rewritten(
            url          = presignedUrl.replace("//$emulatorHost", "//$target"),
            originalHost = origHostPort,
        )
    }

    companion object {
        private const val MAX_FILE_BYTES = 100L * 1024 * 1024
    }
}
