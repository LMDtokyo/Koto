package run.koto.desktop.data.remote.dto

import kotlinx.serialization.Serializable

// ── auth service ─────────────────────────────────────────────────────────────

/**
 * POST /v1/auth/register — initial registration.
 * All key material is **hex-encoded** per the auth service contract.
 * PQXDH (Kyber) is uploaded separately via PUT /v1/keys after the token pair arrives.
 */
@Serializable
data class RegisterRequest(
    val identity_key       : String,
    val signed_pre_key     : String,
    val signed_pre_key_sig : String,
    val signed_pre_key_id  : Int,
    val one_time_pre_keys  : List<String>,
)

@Serializable
data class TokenPair(
    val account_id    : String,
    val session_id    : String = "",
    val access_token  : String,
    val refresh_token : String,
    val expires_at    : Long,
)

@Serializable
data class SessionDto(
    val id            : String,
    val device_name   : String,
    val platform      : String,
    val app_version   : String,
    val client_ip     : String,
    val created_at    : String,    // RFC3339 from server, parsed client-side
    val last_seen_at  : String,
)

@Serializable
data class SessionsListResponse(val sessions: List<SessionDto>)

@Serializable
data class RefreshRequest(val refresh_token: String)

@Serializable
data class RevokeRequest(val refresh_token: String)

@Serializable
data class PublishPrekeysRequest(val keys: List<String>)

@Serializable
data class PublishPrekeysResponse(val total: Int)

// ── chat service ─────────────────────────────────────────────────────────────

/**
 * GET /v1/conversations → list of these.
 * `last_message.ciphertext` is a base64-encoded byte array on the wire.
 */
@Serializable
data class ConversationDto(
    val id           : String,
    val type         : Int = 1, // 1 = direct, 2 = group
    val name         : String = "", // group label, empty for direct
    val display_name : String = "",
    val peer_id      : String = "",
    val member_ids   : List<String> = emptyList(),
    val last_message : LastMessageDto? = null,
    val unread_count : Int = 0,
    val online       : Boolean = false,
)

@Serializable
data class LastMessageDto(
    val id         : String,
    val ciphertext : String,
    val sender_id  : String,
    val sent_at    : Long,
    val delivered  : Boolean = false,
)

@Serializable
data class CreateConversationRequest(
    val member_ids : List<String>,
    val type       : Int,
    val name       : String = "",
)

@Serializable
data class CreateConversationResponse(
    val conversation_id : String,
    val type            : Int = 1,
    val name            : String = "",
    val member_ids      : List<String> = emptyList(),
)

@Serializable
data class ReactionDto(
    val actor_id   : String,
    val emoji      : String,
    val reacted_at : Long,
)

@Serializable
data class ToggleReactionResponse(val added: Boolean)

/**
 * POST /v1/conversations/{convID}/messages → {id, sent_at} only.
 * Full message row is not echoed back; client already holds the ciphertext it sent.
 */
@Serializable
data class SendMessageRequest(
    val type            : Int,
    val ciphertext      : String,
    val sender_key_data : String? = null,
    val client_seq      : Long,
    val reply_to        : String? = null,
    val forwarded_from  : String? = null,
)

@Serializable
data class SendMessageResponse(
    val id      : String,
    val sent_at : Long,
)

/**
 * GET /v1/conversations/{convID}/messages → list; newest-first, cursor-paginated.
 * Response rows do NOT include conversation_id or expires_at — those are
 * local-only fields.
 */
@Serializable
data class HistoryMessageDto(
    val id              : String,
    val ciphertext      : String,
    val sender_id       : String,
    val sent_at         : Long,
    val delivered       : Boolean = false,
    val reply_to        : String? = null,
    val edited_at       : Long    = 0L, // 0 = never edited
    val forwarded_from  : String? = null,
    val pinned          : Boolean = false,
)

@Serializable
data class EditMessageRequest(val ciphertext: String)

@Serializable
data class EditMessageResponse(val edited_at: Long)

// ── user service ─────────────────────────────────────────────────────────────

@Serializable
data class ProfileDto(
    val account_id   : String,
    val display_name : String = "",
    val avatar_url   : String = "",
    val banner_url   : String = "",
    val bio          : String = "",
    val username     : String = "",
    val updated_at   : Long   = 0,
)

@Serializable
data class UpdateProfileRequest(
    val display_name : String? = null,
    val avatar_url   : String? = null,
    val banner_url   : String? = null,
    val bio          : String? = null,
)

@Serializable
data class UsernameAvailabilityDto(
    val available : Boolean,
    val valid     : Boolean,
    val reason    : String? = null,
)

@Serializable
data class SetUsernameRequest(val username: String)

@Serializable
data class SetUsernameResponse(val username: String)

@Serializable
data class BatchProfilesRequest(val ids: List<String>)

@Serializable
data class ContactDto(
    val owner_id   : String,
    val contact_id : String,
    val nickname   : String  = "",
    val added_at   : Long    = 0,
    val blocked    : Boolean = false,
)

@Serializable
data class AddContactRequest(
    val contact_id : String,
    val nickname   : String = "",
)

/**
 * PUT /v1/keys — upload the **PQXDH** key bundle.
 * All byte arrays are **base64-encoded** (note: different encoding from the
 * auth-service register endpoint which uses hex).
 */
@Serializable
data class UploadKeysRequest(
    val identity_key     : String,
    val registration_id  : Int,
    val signed_prekey    : SignedPrekeyPayload,
    val kyber_prekey     : KyberPrekeyPayload,
    val one_time_prekeys : List<OneTimePrekeyPayload>,
)

@Serializable
data class SignedPrekeyPayload(
    val id         : Int,
    val public_key : String,
    val signature  : String,
)

@Serializable
data class KyberPrekeyPayload(
    val id         : Int,
    val public_key : String,
    val signature  : String,
)

@Serializable
data class OneTimePrekeyPayload(
    val id         : Int,
    val public_key : String,
)

/**
 * GET /v1/keys/{targetID} — fetch peer PQXDH bundle.
 * Fields are base64; `one_time_prekey` is nullable — client should retry if null.
 */
@Serializable
data class PrekeyBundleDto(
    val identity_key     : String,
    val registration_id  : Int,
    val device_id        : Int = 1,
    val signed_prekey    : SignedPrekeyPayload,
    val kyber_prekey     : KyberPrekeyPayload,
    val one_time_prekey  : OneTimePrekeyPayload? = null,
)

// ── media service ────────────────────────────────────────────────────────────

@Serializable
data class UploadUrlRequest(
    val content_type : String,
    val size_bytes   : Long,
    val is_public    : Boolean,
)

@Serializable
data class UploadUrlResponse(
    val file_id    : String,
    val upload_url : String,
    val expires_in : String,
)

@Serializable
data class DownloadUrlResponse(
    val file_id      : String,
    val download_url : String,
    val content_type : String,
    val size_bytes   : Long,
    val expires_in   : String,
)

// ── notification service ─────────────────────────────────────────────────────

@Serializable
data class RegisterDeviceRequest(
    val token    : String,
    val platform : Int,
)

@Serializable
data class DeregisterDeviceRequest(val token: String)

// ── bot service ──────────────────────────────────────────────────────────────

@Serializable
data class CreateBotRequest(
    val name        : String,
    val username    : String,
    val webhook_url : String? = null,
)

@Serializable
data class CreateBotResponse(
    val id       : String,
    val token    : String,
    val username : String,
)

@Serializable
data class BotDto(
    val id          : String,
    val account_id  : String,
    val owner_id    : String,
    val name        : String,
    val username    : String,
    val webhook_url : String  = "",
    val active      : Boolean = true,
    val created_at  : Long    = 0,
)
