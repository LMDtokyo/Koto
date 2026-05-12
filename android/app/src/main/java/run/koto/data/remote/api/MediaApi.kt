package run.koto.data.remote.api

import retrofit2.http.*

data class UploadUrlRequest(
    val content_type : String,
    val size_bytes   : Long,
    val is_public    : Boolean = false,
)

data class UploadUrlResponse(
    val file_id    : String,
    val upload_url : String,
    val expires_in : String,
)

data class DownloadUrlResponse(
    val file_id      : String,
    val download_url : String,
    val content_type : String,
    val size_bytes   : Long,
    val expires_in   : String,
)

interface MediaApi {

    /** Request a presigned MinIO PUT URL. Upload bytes directly to upload_url. */
    @POST("v1/media/upload-url")
    suspend fun requestUploadUrl(@Body body: UploadUrlRequest): UploadUrlResponse

    /** Request a short-lived presigned MinIO GET URL for the given file. */
    @GET("v1/media/{fileId}")
    suspend fun getDownloadUrl(@Path("fileId") fileId: String): DownloadUrlResponse
}
