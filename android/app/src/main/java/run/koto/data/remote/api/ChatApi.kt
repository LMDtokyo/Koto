package run.koto.data.remote.api

import retrofit2.http.*

// ── DTOs ──────────────────────────────────────────────────────────────────────

data class ConversationDto(
    val id              : String,
    val display_name    : String,
    val peer_id         : String = "",  // other member's account ID for direct conversations
    val last_message    : MessageDto?,
    val unread_count    : Int,
    val online          : Boolean,
)

data class MessageDto(
    val id          : String,
    val ciphertext  : String,   // base64 — client decrypts
    val sender_id   : String,
    val sent_at     : Long,     // Unix seconds
    val delivered   : Boolean,
)

data class SendMessageRequest(
    val type        : Int    = 1,   // 1 = text (MessageTypeText on server)
    val ciphertext  : String,       // base64 encoded payload
)

data class SendMessageResponse(
    val id          : String,
    val sent_at     : Long,
)

// Backend expects: { "member_ids": ["id1", "id2"], "type": 1 }
// type: 1 = direct, 2 = group
data class CreateConversationRequest(
    val member_ids  : List<String>,
    val type        : Int = 1,
)

data class CreateConversationResponse(
    val conversation_id : String,
)

// ── Interface ─────────────────────────────────────────────────────────────────

interface ChatApi {

    @GET("v1/conversations")
    suspend fun listConversations(): List<ConversationDto>

    @POST("v1/conversations")
    suspend fun createConversation(@Body body: CreateConversationRequest): CreateConversationResponse

    @GET("v1/conversations/{convId}/messages")
    suspend fun getMessages(
        @Path("convId") convId: String,
        @Query("cursor") cursor: String? = null,   // was "before" — server uses "cursor"
        @Query("limit")  limit: Int = 50,
    ): List<MessageDto>

    @POST("v1/conversations/{convId}/messages")
    suspend fun sendMessage(
        @Path("convId") convId: String,
        @Body body: SendMessageRequest,
    ): SendMessageResponse

    @DELETE("v1/conversations/{convId}/messages/{msgId}")
    suspend fun deleteMessage(
        @Path("convId") convId: String,
        @Path("msgId")  msgId: String,
    )
}
