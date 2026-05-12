package run.koto.data.remote.api

import retrofit2.http.Body
import retrofit2.http.POST

// ── Request / Response DTOs ───────────────────────────────────────────────────

data class RegisterRequest(
    val identity_key          : String,  // hex Ed25519 public key
    val signed_pre_key        : String,  // hex
    val signed_pre_key_sig    : String,  // hex — sig over signed_pre_key with identity key
    val signed_pre_key_id     : Int = 1,
    val one_time_pre_keys     : List<OneTimePreKeyDto> = emptyList(),
)

data class OneTimePreKeyDto(
    val id       : Int,
    val key_data : String,  // hex
)

data class RegisterResponse(
    val account_id    : String,
    val access_token  : String,
    val refresh_token : String,
)

data class RefreshRequest(
    val refresh_token : String,
)

data class TokenResponse(
    val access_token  : String,
    val refresh_token : String,
)

// ── Interface ─────────────────────────────────────────────────────────────────

interface AuthApi {

    @POST("v1/auth/register")
    suspend fun register(@Body body: RegisterRequest): RegisterResponse

    @POST("v1/auth/token/refresh")
    suspend fun refresh(@Body body: RefreshRequest): TokenResponse
}
