package run.koto.desktop.domain.repository

import run.koto.desktop.domain.model.UploadedFile

interface MediaRepository {
    /**
     * Upload a blob to the object store via the presigned-URL flow.
     * Returns the server-assigned file id that can be referenced by other entities
     * (profile avatar, message payloads, etc).
     */
    suspend fun upload(
        contentType : String,
        bytes       : ByteArray,
        isPublic    : Boolean = false,
    ): Result<UploadedFile>

    /**
     * Resolve a downloadable URL for an existing file id. The URL is presigned and
     * has a short TTL — consumers should download immediately.
     */
    suspend fun downloadUrl(fileId: String): Result<String>

    /**
     * Fetch the raw bytes of a file id. Goes through the same SigV4 / Host
     * dance as [downloadUrl] but reads the bytes here so callers don't have
     * to handle the host-mismatch problem on dev MinIO.
     */
    suspend fun downloadBytes(fileId: String): Result<ByteArray>
}
