package run.koto.desktop.data.remote.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import run.koto.desktop.data.remote.dto.ConversationDto
import run.koto.desktop.data.remote.dto.CreateConversationRequest
import run.koto.desktop.data.remote.dto.CreateConversationResponse
import run.koto.desktop.data.remote.dto.HistoryMessageDto
import run.koto.desktop.data.remote.dto.SendMessageRequest
import run.koto.desktop.data.remote.dto.SendMessageResponse

class ChatApi(private val http: HttpClient) {

    suspend fun listConversations(): List<ConversationDto> =
        http.get("/v1/conversations").body()

    suspend fun createDirect(peerId: String): CreateConversationResponse =
        http.post("/v1/conversations") {
            contentType(ContentType.Application.Json)
            setBody(CreateConversationRequest(member_ids = listOf(peerId), type = 1))
        }.body()

    suspend fun createGroup(memberIds: List<String>, name: String = ""): CreateConversationResponse =
        http.post("/v1/conversations") {
            contentType(ContentType.Application.Json)
            setBody(CreateConversationRequest(member_ids = memberIds, type = 2, name = name))
        }.body()

    /**
     * Paged, newest-first. `cursor` = the oldest message id seen so far; empty starts
     * from head. Server clamps limit to [1, 100].
     */
    suspend fun history(convId: String, cursor: String? = null, limit: Int = 50): List<HistoryMessageDto> =
        http.get("/v1/conversations/$convId/messages") {
            cursor?.takeIf { it.isNotBlank() }?.let { parameter("cursor", it) }
            parameter("limit", limit)
        }.body()

    suspend fun send(convId: String, request: SendMessageRequest): SendMessageResponse =
        http.post("/v1/conversations/$convId/messages") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun delete(convId: String, messageId: String) {
        http.delete("/v1/conversations/$convId/messages/$messageId")
    }

    suspend fun edit(convId: String, messageId: String, ciphertextBase64: String): run.koto.desktop.data.remote.dto.EditMessageResponse =
        http.patch("/v1/conversations/$convId/messages/$messageId") {
            contentType(ContentType.Application.Json)
            setBody(run.koto.desktop.data.remote.dto.EditMessageRequest(ciphertext = ciphertextBase64))
        }.body()

    suspend fun pin(convId: String, messageId: String) {
        http.post("/v1/conversations/$convId/messages/$messageId/pin")
    }

    suspend fun unpin(convId: String, messageId: String) {
        http.delete("/v1/conversations/$convId/messages/$messageId/pin")
    }

    suspend fun toggleReaction(convId: String, messageId: String, emoji: String): run.koto.desktop.data.remote.dto.ToggleReactionResponse {
        val encoded = java.net.URLEncoder.encode(emoji, Charsets.UTF_8.name())
        return http.post("/v1/conversations/$convId/messages/$messageId/reactions/$encoded").body()
    }

    suspend fun listReactions(convId: String, messageId: String): List<run.koto.desktop.data.remote.dto.ReactionDto> =
        http.get("/v1/conversations/$convId/messages/$messageId/reactions").body()
}
