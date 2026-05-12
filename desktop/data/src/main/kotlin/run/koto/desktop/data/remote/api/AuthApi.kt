package run.koto.desktop.data.remote.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import run.koto.desktop.data.remote.LocalDevice
import run.koto.desktop.data.remote.dto.PublishPrekeysRequest
import run.koto.desktop.data.remote.dto.PublishPrekeysResponse
import run.koto.desktop.data.remote.dto.RefreshRequest
import run.koto.desktop.data.remote.dto.RegisterRequest
import run.koto.desktop.data.remote.dto.RevokeRequest
import run.koto.desktop.data.remote.dto.TokenPair

/**
 * Auth service client. All routes that return a fresh token pair must go through the
 * `bare` HttpClient (no Bearer plugin) — otherwise the plugin tries to refresh using the
 * very token we are attempting to produce.
 *
 * Prekey endpoints here are the **legacy hex-encoded** flow rooted at auth.
 * For the PQXDH (Kyber) flow use [UserApi.uploadKeys] / [UserApi.fetchPrekeyBundle].
 */
class AuthApi(private val http: HttpClient) {

    suspend fun register(request: RegisterRequest): TokenPair =
        http.post("/v1/auth/register") {
            contentType(ContentType.Application.Json)
            applyDeviceHeaders()
            setBody(request)
        }.body()

    /**
     * Re-establish a session for an existing account. Body shape matches register;
     * the server verifies the XEdDSA signature on `signed_pre_key` against the
     * stored identity key, proving the caller controls the private half.
     */
    suspend fun restore(request: RegisterRequest): TokenPair =
        http.post("/v1/auth/restore") {
            contentType(ContentType.Application.Json)
            applyDeviceHeaders()
            setBody(request)
        }.body()

    private fun io.ktor.client.request.HttpRequestBuilder.applyDeviceHeaders() {
        val d = LocalDevice.info
        header("X-Device-Name", d.name)
        header("X-Platform",    d.platform)
        header("X-App-Version", d.appVersion)
    }

    suspend fun refresh(refreshToken: String): TokenPair =
        http.post("/v1/auth/token/refresh") {
            contentType(ContentType.Application.Json)
            header("X-Koto-No-Auth", "1")
            // Forward device metadata so the heal-refresh path (which mints
            // a session row when one is missing) populates device_name +
            // platform with real values instead of leaving them blank.
            applyDeviceHeaders()
            setBody(RefreshRequest(refreshToken))
        }.body()

    suspend fun revoke(refreshToken: String) {
        http.post("/v1/auth/token/revoke") {
            contentType(ContentType.Application.Json)
            setBody(RevokeRequest(refreshToken))
        }
    }

    /**
     * Append one-time prekeys to the account's pool. Hex-encoded keys per the
     * auth service contract. Returns the new total size of the pool.
     */
    suspend fun publishPrekeys(keys: List<String>): PublishPrekeysResponse =
        http.post("/v1/auth/prekeys/publish") {
            contentType(ContentType.Application.Json)
            setBody(PublishPrekeysRequest(keys))
        }.body()
}
