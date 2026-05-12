package run.koto.data.remote.api

import retrofit2.http.*

data class RegisterDeviceRequest(
    val token    : String,   // UnifiedPush endpoint URL
    val platform : Int = 2,  // 2 = Android
)

data class UnregisterDeviceRequest(
    val token : String,
)

interface NotificationApi {
    @PUT("v1/devices")
    suspend fun registerDevice(@Body request: RegisterDeviceRequest)

    @HTTP(method = "DELETE", path = "v1/devices", hasBody = true)
    suspend fun unregisterDevice(@Body request: UnregisterDeviceRequest)
}
