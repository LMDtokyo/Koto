package run.koto.data.remote.api

import retrofit2.http.*

data class ProfileDto(
    val account_id   : String,
    val display_name : String,
    val avatar_url   : String  = "",
    val online       : Boolean = false,
)

data class UpdateProfileRequest(
    val display_name : String,
    val avatar_url   : String  = "",
)

// ── Signal Protocol PreKey DTOs ───────────────────────────────────────────────

/** A signed or unsigned prekey (public key + optional signature), Base64-encoded. */
data class PrekeyCertDto(
    val id         : Int,
    val public_key : String,        // Base64-encoded
    val signature  : String = "",   // Base64-encoded; empty for unsigned one-time prekeys
)

data class UploadPrekeysRequest(
    val identity_key     : String,          // Base64
    val registration_id  : Int,
    val signed_prekey    : PrekeyCertDto,
    val kyber_prekey     : PrekeyCertDto,
    val one_time_prekeys : List<PrekeyCertDto> = emptyList(),
)

data class PreKeyBundleDto(
    val identity_key    : String,           // Base64
    val registration_id : Int,
    val device_id       : Int,
    val signed_prekey   : PrekeyCertDto,
    val kyber_prekey    : PrekeyCertDto,
    val one_time_prekey : PrekeyCertDto? = null,
)

interface UserApi {

    @GET("v1/users/{accountId}")
    suspend fun getProfile(@Path("accountId") accountId: String): ProfileDto

    @PUT("v1/users/me")
    suspend fun updateProfile(@Body body: UpdateProfileRequest): ProfileDto

    @PUT("v1/keys")
    suspend fun uploadPrekeys(@Body body: UploadPrekeysRequest)

    @GET("v1/keys/{accountId}")
    suspend fun getPreKeyBundle(@Path("accountId") accountId: String): PreKeyBundleDto
}
