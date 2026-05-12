package run.koto.desktop.data.remote.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import run.koto.desktop.data.remote.dto.SessionsListResponse

/**
 * Linked-devices endpoints. Routed through `/v1/auth/sessions*` which the
 * gateway puts behind JWT — uses the **main** [HttpClient] (with Bearer
 * plugin) so refresh-on-401 works automatically.
 */
class SessionsApi(private val http: HttpClient) {

    suspend fun list(): SessionsListResponse =
        http.get("/v1/auth/sessions").body()

    suspend fun revoke(sessionId: String) {
        http.delete("/v1/auth/sessions/$sessionId")
    }
}
