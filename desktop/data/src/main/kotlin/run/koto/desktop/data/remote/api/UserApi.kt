package run.koto.desktop.data.remote.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import run.koto.desktop.data.remote.dto.AddContactRequest
import run.koto.desktop.data.remote.dto.BatchProfilesRequest
import run.koto.desktop.data.remote.dto.ContactDto
import run.koto.desktop.data.remote.dto.PrekeyBundleDto
import run.koto.desktop.data.remote.dto.ProfileDto
import run.koto.desktop.data.remote.dto.SetUsernameRequest
import run.koto.desktop.data.remote.dto.SetUsernameResponse
import run.koto.desktop.data.remote.dto.UpdateProfileRequest
import run.koto.desktop.data.remote.dto.UploadKeysRequest
import run.koto.desktop.data.remote.dto.UsernameAvailabilityDto

class UserApi(private val http: HttpClient) {

    // ── profile ──────────────────────────────────────────────────────────────

    suspend fun me(): ProfileDto = http.get("/v1/users/me").body()

    suspend fun getProfile(accountId: String): ProfileDto =
        http.get("/v1/users/$accountId").body()

    suspend fun updateProfile(
        displayName : String? = null,
        avatarUrl   : String? = null,
        bannerUrl   : String? = null,
        bio         : String? = null,
    ): ProfileDto =
        http.put("/v1/users/me") {
            contentType(ContentType.Application.Json)
            setBody(UpdateProfileRequest(displayName, avatarUrl, bannerUrl, bio))
        }.body()

    suspend fun batchProfiles(ids: List<String>): List<ProfileDto> =
        http.post("/v1/users/batch") {
            contentType(ContentType.Application.Json)
            setBody(BatchProfilesRequest(ids))
        }.body()

    /** Probe whether [username] is syntactically valid AND not taken. */
    suspend fun checkUsername(username: String): UsernameAvailabilityDto =
        http.get("/v1/users/username-available/$username").body()

    /** Resolve a public @handle to a [ProfileDto]; throws on 404. */
    suspend fun findByUsername(username: String): ProfileDto =
        http.get("/v1/users/by-username/$username").body()

    /** Claim [username] for the current account. Throws on collision (409). */
    suspend fun setUsername(username: String): SetUsernameResponse =
        http.put("/v1/users/me/username") {
            contentType(ContentType.Application.Json)
            setBody(SetUsernameRequest(username))
        }.body()

    // ── contacts ─────────────────────────────────────────────────────────────

    suspend fun listContacts(): List<ContactDto> =
        http.get("/v1/contacts").body()

    suspend fun addContact(contactId: String, nickname: String = "") {
        http.post("/v1/contacts") {
            contentType(ContentType.Application.Json)
            setBody(AddContactRequest(contactId, nickname))
        }
    }

    suspend fun removeContact(contactId: String) {
        http.delete("/v1/contacts/$contactId")
    }

    suspend fun blockContact(contactId: String) {
        http.post("/v1/contacts/$contactId/block")
    }

    suspend fun unblockContact(contactId: String) {
        http.delete("/v1/contacts/$contactId/block")
    }

    // ── PQXDH key bundle ─────────────────────────────────────────────────────

    /** Upload or rotate the full Signal + Kyber1024 key bundle (all base64). */
    suspend fun uploadKeys(request: UploadKeysRequest) {
        http.put("/v1/keys") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    /**
     * Fetch a peer's PQXDH prekey bundle. Returns null one_time_prekey if the peer's
     * OTPK pool is drained — caller should retry shortly or fall back to the signed
     * prekey only (less forward secrecy).
     */
    suspend fun fetchPrekeyBundle(accountId: String): PrekeyBundleDto =
        http.get("/v1/keys/$accountId").body()
}
