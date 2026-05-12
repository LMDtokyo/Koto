package run.koto.desktop.data.remote.api

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import run.koto.desktop.data.remote.dto.DeregisterDeviceRequest
import run.koto.desktop.data.remote.dto.RegisterDeviceRequest

/**
 * Notification service client. Desktop does not receive APNs/UnifiedPush directly
 * today, but the service is plumbed for completeness — the bot/chat integration
 * and future mobile ports share the same endpoint surface. Platform field: 1=iOS, 2=Android.
 */
class NotificationApi(private val http: HttpClient) {

    suspend fun registerDevice(token: String, platform: Int) {
        http.put("/v1/devices") {
            contentType(ContentType.Application.Json)
            setBody(RegisterDeviceRequest(token, platform))
        }
    }

    suspend fun deregisterDevice(token: String) {
        http.delete("/v1/devices") {
            contentType(ContentType.Application.Json)
            setBody(DeregisterDeviceRequest(token))
        }
    }
}
