package run.koto.desktop.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import run.koto.desktop.crypto.KotoCryptoProvider
import run.koto.desktop.crypto.PeerBundle
import run.koto.desktop.data.local.db.KotoDb
import run.koto.desktop.data.remote.api.ChatApi
import run.koto.desktop.data.remote.api.UserApi
import run.koto.desktop.data.remote.dto.PrekeyBundleDto
import run.koto.desktop.data.remote.dto.SendMessageRequest
import run.koto.desktop.domain.DomainError
import run.koto.desktop.domain.SecurityLimits
import run.koto.desktop.domain.model.Message
import run.koto.desktop.domain.model.MessageReaction
import run.koto.desktop.domain.model.WebSocketEvent
import run.koto.desktop.domain.repository.AuthRepository
import run.koto.desktop.domain.repository.ChatRepository
import run.koto.desktop.domain.repository.PreferencesRepository
import run.koto.desktop.domain.util.IdRedactor.mask
import java.util.Base64
import java.util.concurrent.atomic.AtomicLong

class ChatRepositoryImpl(
    private val chatApi  : ChatApi,
    private val userApi  : UserApi,
    private val db       : KotoDb,
    private val crypto   : KotoCryptoProvider,
    private val auth     : AuthRepository,
    private val wsEvents : SharedFlow<WebSocketEvent>,
    private val prefs    : PreferencesRepository,
) : ChatRepository {

    private val log = LoggerFactory.getLogger(ChatRepositoryImpl::class.java)

    /**
     * Monotonic client-side sequence number. Two messages sent in the same millisecond
     * from the same process will still get distinct values, which matters for the server's
     * deduplication window.
     */
    private val clientSeq = AtomicLong(System.currentTimeMillis() shl 10)

    override fun observeMessages(conversationId: String): Flow<List<Message>> =
        combine(
            db.kotoDbQueries.selectMessagesByConv(conversationId).asFlow().mapToList(Dispatchers.IO),
            db.kotoDbQueries.selectReactionsForConv(conversationId).asFlow().mapToList(Dispatchers.IO),
            auth.session,
        ) { rows, reactions, session ->
            val me = session?.accountId
            val byMsg = reactions.groupBy { it.message_id }
            rows.map { row ->
                row.toDomain(me, byMsg[row.id].orEmpty().map { it.toReaction() })
            }
        }

    override fun observeEvents(): Flow<WebSocketEvent> = wsEvents

    override suspend fun syncMessages(conversationId: String): Result<Unit> = runCatching {
        auth.currentAccountId() ?: throw DomainError.Unauthorized()
        val rows = withContext(Dispatchers.IO) { chatApi.history(conversationId) }
        rows.forEach { dto ->
            val cipherBytes = decodeBase64(dto.ciphertext)
            if (cipherBytes.size > SecurityLimits.MAX_CIPHERTEXT_BYTES) {
                log.warn("skipping oversized ciphertext msg={} size={}", mask(dto.id), cipherBytes.size)
                return@forEach
            }
            val plaintext = tryDecrypt(dto.sender_id, dto.ciphertext) ?: CIPHERTEXT_PLACEHOLDER
            db.kotoDbQueries.insertMessage(
                id              = dto.id,
                conversation_id = conversationId,
                sender_id       = dto.sender_id,
                plaintext_cache = plaintext,
                ciphertext      = cipherBytes,
                sent_at         = dto.sent_at,
                delivered       = if (dto.delivered) 1 else 0,
                read_at         = null,
                expires_at      = null,
                reply_to        = dto.reply_to?.takeIf { it.isNotBlank() },
                edited_at       = dto.edited_at.takeIf { it > 0L }?.let { it * 1000 },
                forwarded_from  = dto.forwarded_from?.takeIf { it.isNotBlank() },
                pinned          = if (dto.pinned) 1 else 0,
            )
        }
        log.debug("synced {} history rows for conv={}", rows.size, mask(conversationId))
    }.onFailure { log.warn("syncMessages failed conv={}", mask(conversationId), it) }

    override suspend fun send(
        conversationId         : String,
        plaintext              : String,
        replyToId              : String?,
        forwardedFromAccountId : String?,
    ): Result<Message> = runCatching {
        val plaintextBytes = plaintext.toByteArray(Charsets.UTF_8)
        if (plaintextBytes.size > SecurityLimits.MAX_PLAINTEXT_BYTES) {
            throw DomainError.InvalidInput("message exceeds ${SecurityLimits.MAX_PLAINTEXT_BYTES} byte limit")
        }

        val conv = db.kotoDbQueries.selectConversationById(conversationId).executeAsOneOrNull()
            ?: throw DomainError.NotFound("conversation $conversationId")
        val peer = conv.peer_account_id.ifBlank { throw DomainError.EncryptionUnavailable(conversationId) }

        val ciphertext = encryptEstablishingSession(peer, plaintextBytes)
        val request = SendMessageRequest(
            type           = MESSAGE_TYPE_TEXT,
            ciphertext     = Base64.getEncoder().encodeToString(ciphertext),
            client_seq     = clientSeq.incrementAndGet(),
            reply_to       = replyToId,
            forwarded_from = forwardedFromAccountId,
        )
        val resp = withContext(Dispatchers.IO) { chatApi.send(conversationId, request) }
        log.debug("sent msg={} conv={} replyTo={}", mask(resp.id), mask(conversationId), replyToId?.let(::mask))

        val accountId = auth.currentAccountId().orEmpty()
        db.kotoDbQueries.insertMessage(
            id              = resp.id,
            conversation_id = conversationId,
            sender_id       = accountId,
            plaintext_cache = plaintext,
            ciphertext      = ciphertext,
            sent_at         = resp.sent_at,
            delivered       = 0,
            read_at         = null,
            expires_at      = null,
            reply_to        = replyToId,
            edited_at       = null,
            forwarded_from  = forwardedFromAccountId,
            pinned          = 0,
        )
        Message(
            id             = resp.id,
            conversationId = conversationId,
            senderId       = accountId,
            plaintext      = plaintext,
            sentAt         = resp.sent_at,
            delivered      = false,
            read           = false,
            expiresAt      = null,
            isOutgoing     = true,
            replyToId      = replyToId,
            forwardedFrom  = forwardedFromAccountId,
        )
    }.onFailure { log.warn("send failed conv={}", mask(conversationId), it) }

    override suspend fun setPinned(
        conversationId: String,
        messageId: String,
        pinned: Boolean,
    ): Result<Unit> = runCatching {
        // Optimistic local flip; if the server rejects, roll back.
        db.kotoDbQueries.setMessagePinned(if (pinned) 1 else 0, messageId)
        try {
            withContext(Dispatchers.IO) {
                if (pinned) chatApi.pin(conversationId, messageId)
                else        chatApi.unpin(conversationId, messageId)
            }
        } catch (t: Throwable) {
            db.kotoDbQueries.setMessagePinned(if (pinned) 0 else 1, messageId)
            throw t
        }
        Unit
    }.onFailure { log.warn("setPinned failed conv={} msg={}", mask(conversationId), mask(messageId), it) }

    override suspend fun forward(
        sourceConversationId: String,
        messageId: String,
        destinationConversationIds: List<String>,
    ): Result<Int> = runCatching {
        // Read the source message locally — we already have plaintext cached.
        // Server-side forward isn't a single op because each destination
        // gets its own encrypted ciphertext under that destination's session.
        val src = db.kotoDbQueries.selectMessagesByConv(sourceConversationId)
            .executeAsList()
            .firstOrNull { it.id == messageId }
            ?: throw DomainError.NotFound("message $messageId")

        // The "forwarded_from" marker should always point to the original
        // author — chains of forwards still credit the first author.
        val origin = (src.forwarded_from?.takeIf { it.isNotBlank() }) ?: src.sender_id

        var ok = 0
        destinationConversationIds.forEach { destId ->
            send(destId, src.plaintext_cache, replyToId = null, forwardedFromAccountId = origin)
                .onSuccess { ok++ }
                .onFailure { log.warn("forward target failed dest={}", mask(destId), it) }
        }
        ok
    }.onFailure { log.warn("forward failed src={} msg={}", mask(sourceConversationId), mask(messageId), it) }

    override suspend fun markRead(conversationId: String, messageId: String) {
        // Always update the local row so the UI reflects the read state for
        // the user's own benefit. The receipt is sent only when the privacy
        // setting allows it — a read-receipt is metadata leaking *to* the
        // peer, so the user must opt in.
        db.kotoDbQueries.markRead(System.currentTimeMillis(), messageId)
        if (!prefs.current().sendReadReceipts) return
        // Server-side read-receipt broadcast lives on a future endpoint; until
        // it lands the gate above is what stops the client from emitting it.
    }

    override suspend fun delete(conversationId: String, messageId: String): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) { chatApi.delete(conversationId, messageId) }
        db.kotoDbQueries.deleteMessage(messageId)
        db.kotoDbQueries.deleteReactionsForMessage(messageId)
        Unit
    }.onFailure { log.warn("delete failed conv={} msg={}", mask(conversationId), mask(messageId), it) }

    override suspend fun editMessage(
        conversationId: String,
        messageId: String,
        newPlaintext: String,
    ): Result<Unit> = runCatching {
        val plaintextBytes = newPlaintext.toByteArray(Charsets.UTF_8)
        if (plaintextBytes.size > SecurityLimits.MAX_PLAINTEXT_BYTES) {
            throw DomainError.InvalidInput("message exceeds ${SecurityLimits.MAX_PLAINTEXT_BYTES} byte limit")
        }
        val conv = db.kotoDbQueries.selectConversationById(conversationId).executeAsOneOrNull()
            ?: throw DomainError.NotFound("conversation $conversationId")
        val peer = conv.peer_account_id.ifBlank { throw DomainError.EncryptionUnavailable(conversationId) }

        val ciphertext = encryptEstablishingSession(peer, plaintextBytes)
        val resp = withContext(Dispatchers.IO) {
            chatApi.edit(conversationId, messageId, Base64.getEncoder().encodeToString(ciphertext))
        }
        db.kotoDbQueries.editMessage(
            plaintext = newPlaintext,
            ciphertext = ciphertext,
            editedAt = resp.edited_at * 1000,
            id = messageId,
        )
        log.debug("edited msg={} conv={}", mask(messageId), mask(conversationId))
        Unit
    }.onFailure { log.warn("edit failed conv={} msg={}", mask(conversationId), mask(messageId), it) }

    override suspend fun toggleReaction(
        conversationId: String,
        messageId: String,
        emoji: String,
    ): Result<Boolean> = runCatching {
        val me = auth.currentAccountId() ?: throw DomainError.Unauthorized()
        // Optimistic local toggle so the chip flips immediately. The server
        // is the source of truth — its response tells us the final state and
        // we reconcile in case the optimistic guess was wrong (e.g. a stale
        // local row).
        val before = db.kotoDbQueries.selectReactionsForConv(conversationId)
            .executeAsList()
            .any { it.message_id == messageId && it.actor_id == me && it.emoji == emoji }
        val now = System.currentTimeMillis()
        if (before) {
            db.kotoDbQueries.deleteReaction(messageId, me, emoji)
        } else {
            db.kotoDbQueries.upsertReaction(messageId, me, emoji, now)
        }
        val resp = withContext(Dispatchers.IO) { chatApi.toggleReaction(conversationId, messageId, emoji) }
        // Reconcile if optimistic guess disagreed with server.
        if (resp.added && !before.not()) {
            // Server says "added" but we predicted "removed" → re-insert.
            db.kotoDbQueries.upsertReaction(messageId, me, emoji, now)
        } else if (!resp.added && before) {
            // Server says "removed" but we predicted "added" → drop again.
            db.kotoDbQueries.deleteReaction(messageId, me, emoji)
        }
        resp.added
    }.onFailure { log.warn("toggleReaction failed conv={} msg={}", mask(conversationId), mask(messageId), it) }

    private suspend fun encryptEstablishingSession(peer: String, plaintext: ByteArray): ByteArray {
        crypto.encrypt(peer, plaintext).getOrNull()?.let { return it }

        val bundle = runCatching { withContext(Dispatchers.IO) { userApi.fetchPrekeyBundle(peer) } }
            .getOrElse {
                log.warn("prekey bundle fetch failed peer={}", mask(peer), it)
                throw DomainError.EncryptionUnavailable(peer)
            }
        crypto.processPreKeyBundle(peer, bundle.toPeerBundle()).getOrElse {
            log.error("processPreKeyBundle failed peer={}", mask(peer), it)
            throw DomainError.EncryptionUnavailable(peer)
        }
        return crypto.encrypt(peer, plaintext).getOrElse {
            log.error("encrypt after session establishment still failed peer={}", mask(peer), it)
            throw DomainError.EncryptionUnavailable(peer)
        }
    }

    private suspend fun tryDecrypt(senderId: String, ciphertextB64: String): String? {
        val bytes = runCatching { Base64.getDecoder().decode(ciphertextB64) }.getOrNull() ?: return null
        return crypto.decrypt(senderId, bytes).getOrNull()?.toString(Charsets.UTF_8)
    }

    private fun decodeBase64(b64: String): ByteArray =
        runCatching { Base64.getDecoder().decode(b64) }.getOrElse { ByteArray(0) }

    companion object {
        private const val CIPHERTEXT_PLACEHOLDER = "[encrypted]"
        private const val MESSAGE_TYPE_TEXT = 1
    }
}

private fun PrekeyBundleDto.toPeerBundle(): PeerBundle = PeerBundle(
    registrationId    = registration_id.toLong(),
    deviceId          = device_id.toLong(),
    identityPublicKey = Base64.getDecoder().decode(identity_key),
    signedPreKeyId    = signed_prekey.id.toLong(),
    signedPreKeyPub   = Base64.getDecoder().decode(signed_prekey.public_key),
    signedPreKeySig   = Base64.getDecoder().decode(signed_prekey.signature),
    kyberPreKeyId     = kyber_prekey.id.toLong(),
    kyberPreKeyPub    = Base64.getDecoder().decode(kyber_prekey.public_key),
    kyberPreKeySig    = Base64.getDecoder().decode(kyber_prekey.signature),
    oneTimePreKeyId   = one_time_prekey?.id?.toLong(),
    oneTimePreKeyPub  = one_time_prekey?.public_key?.let(Base64.getDecoder()::decode),
)

private fun run.koto.desktop.data.local.db.Message.toDomain(
    me: String?,
    reactions: List<MessageReaction> = emptyList(),
) = Message(
    id             = id,
    conversationId = conversation_id,
    senderId       = sender_id,
    plaintext      = plaintext_cache,
    sentAt         = sent_at,
    delivered      = delivered == 1L,
    read           = read_at != null,
    expiresAt      = expires_at,
    isOutgoing     = me != null && sender_id == me,
    replyToId      = reply_to?.takeIf { it.isNotBlank() },
    editedAt       = edited_at,
    forwardedFrom  = forwarded_from?.takeIf { it.isNotBlank() },
    pinned         = pinned == 1L,
    reactions      = reactions,
)

private fun run.koto.desktop.data.local.db.Message_reaction.toReaction() = MessageReaction(
    messageId = message_id,
    actorId   = actor_id,
    emoji     = emoji,
    reactedAt = reacted_at,
)
