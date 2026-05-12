package run.koto.desktop.data.remote.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import run.koto.desktop.data.remote.dto.DownloadUrlResponse
import run.koto.desktop.data.remote.dto.UploadUrlRequest
import run.koto.desktop.data.remote.dto.UploadUrlResponse

/**
 * Media service client. Two-step upload:
 *   1. [requestUploadUrl] — gateway returns a presigned PUT URL (1h TTL).
 *   2. [putToPresignedUrl] — client PUTs the bytes directly to object storage.
 *
 * Uses two distinct HTTP clients: the main one (Bearer auth) for gateway calls, and
 * a hostless `storage` client without the Auth plugin for the presigned URL — the URL
 * carries its own SigV4 credentials in the query string, so any additional
 * `Authorization` header would break the signature.
 */
class MediaApi(
    private val http        : HttpClient,
    private val storageHttp : HttpClient,
) {

    suspend fun requestUploadUrl(
        contentType : String,
        sizeBytes   : Long,
        isPublic    : Boolean,
    ): UploadUrlResponse =
        http.post("/v1/media/upload-url") {
            contentType(ContentType.Application.Json)
            setBody(UploadUrlRequest(contentType, sizeBytes, isPublic))
        }.body()

    suspend fun requestDownloadUrl(fileId: String): DownloadUrlResponse =
        http.get("/v1/media/$fileId").body()

    suspend fun putToPresignedUrl(
        presignedUrl : String,
        contentType  : String,
        body         : ByteArray,
        hostOverride : String? = null,
    ) {
        storageHttp.put(presignedUrl) {
            header(HttpHeaders.ContentType, contentType)
            // SigV4 signs the Host header. If the caller rewrote the URL
            // host (e.g. 10.0.2.2 → 127.0.0.1 on desktop) we must still
            // send the original host so MinIO's signature check passes.
            if (hostOverride != null) header(HttpHeaders.Host, hostOverride)
            setBody(body)
        }
    }

    suspend fun getBytes(
        presignedUrl : String,
        hostOverride : String? = null,
    ): ByteArray =
        storageHttp.get(presignedUrl) {
            if (hostOverride != null) header(HttpHeaders.Host, hostOverride)
        }.body()
}
