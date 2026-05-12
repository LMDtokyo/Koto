package run.koto.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import run.koto.data.local.dao.ConversationDao
import run.koto.data.local.dao.MessageDao
import run.koto.data.local.entity.ConversationEntity
import run.koto.data.local.entity.MessageEntity
import run.koto.data.local.entity.toInfo
import run.koto.data.local.entity.toUi
import run.koto.crypto.CryptoManager
import run.koto.data.prefs.AccountPrefs
import run.koto.data.prefs.SettingsPrefs
import android.util.Base64 as AndroidBase64
import run.koto.data.remote.api.ChatApi
import run.koto.data.remote.api.CreateConversationRequest
import run.koto.data.remote.api.SendMessageRequest
import run.koto.data.remote.api.UserApi
import uniffi.koto_crypto.PreKeyBundleInput
import run.koto.data.remote.ws.KotoWebSocket
import run.koto.data.remote.ws.WsEvent
import run.koto.domain.model.ConversationInfo
import run.koto.domain.model.ConversationUi
import run.koto.domain.model.MessageUi
import javax.inject.Inject
import javax.inject.Singleton

private const val DISAPPEARING_TTL_MS = 7 * 24 * 60 * 60 * 1_000L   // 7 days

@Singleton
class ChatRepository @Inject constructor(
    private val chatApi        : ChatApi,
    private val userApi        : UserApi,
    private val messageDao     : MessageDao,
    private val conversationDao: ConversationDao,
    private val accountPrefs   : AccountPrefs,
    private val settingsPrefs  : SettingsPrefs,
    private val cryptoManager  : CryptoManager,
    private val webSocket      : KotoWebSocket,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Cached disappearing-messages flag — avoids suspend in send/receive hot paths.
    private val disappearingMessages: StateFlow<Boolean> =
        settingsPrefs.settingsFlow()
            .map { it.disappearingMessages }
            .stateIn(scope, SharingStarted.Eagerly, false)

    init {
        scope.launch {
            // Initialise Signal Protocol crypto from persisted keys.
            val accountId = accountPrefs.getAccountId()
            if (accountId != null) cryptoManager.init(accountId)

            // Connect WebSocket using the stored access token.
            val token = accountPrefs.getAccessToken() ?: return@launch
            webSocket.connect(token)
        }
        observeWebSocket()
        scope.launch { messageDao.deleteExpired(System.currentTimeMillis()) }
    }

    // ── Conversations ─────────────────────────────────────────────────────────

    fun getConversationsFlow(): Flow<List<ConversationUi>> {
        scope.launch { syncConversations() }
        return conversationDao.observeAll().map { list -> list.map { it.toUi() } }
    }

    suspend fun getConversationInfo(convId: String): ConversationInfo? =
        conversationDao.getById(convId)?.toInfo()

    suspend fun createDirectConversation(peerId: String): Result<String> = runCatching {
        val myId = accountPrefs.getAccountId() ?: error("not registered")
        val resp = chatApi.createConversation(
            CreateConversationRequest(
                member_ids = listOf(myId, peerId),
                type       = 1,  // direct
            )
        )
        syncConversations()

        // Establish an X3DH + Kyber session with the peer so the first message
        // is end-to-end encrypted. Non-fatal — falls back to Base64 if keys unavailable.
        runCatching {
            val bundleDto = userApi.getPreKeyBundle(peerId)
            val bundle = PreKeyBundleInput(
                registrationId     = bundleDto.registration_id.toUInt(),
                deviceId           = bundleDto.device_id.toUInt(),
                identityKey        = AndroidBase64.decode(bundleDto.identity_key, AndroidBase64.NO_WRAP),
                signedPrekeyId     = bundleDto.signed_prekey.id.toUInt(),
                signedPrekeyPublic = AndroidBase64.decode(bundleDto.signed_prekey.public_key, AndroidBase64.NO_WRAP),
                signedPrekeySig    = AndroidBase64.decode(bundleDto.signed_prekey.signature, AndroidBase64.NO_WRAP),
                prekeyId           = bundleDto.one_time_prekey?.id?.toUInt(),
                prekeyPublic       = bundleDto.one_time_prekey?.let {
                    AndroidBase64.decode(it.public_key, AndroidBase64.NO_WRAP)
                },
                kyberPrekeyId      = bundleDto.kyber_prekey.id.toUInt(),
                kyberPrekeyPublic  = AndroidBase64.decode(bundleDto.kyber_prekey.public_key, AndroidBase64.NO_WRAP),
                kyberPrekeySig     = AndroidBase64.decode(bundleDto.kyber_prekey.signature, AndroidBase64.NO_WRAP),
            )
            cryptoManager.processPreKeyBundle(peerId, bundle)
        }

        resp.conversation_id
    }

    // ── Messages ──────────────────────────────────────────────────────────────

    fun getMessagesFlow(convId: String): Flow<List<MessageUi>> {
        scope.launch { syncMessages(convId) }
        return messageDao.observeMessages(convId).map { list -> list.map { it.toUi() } }
    }

    suspend fun sendMessage(convId: String, plaintext: String): Result<Unit> = runCatching {
        val myId = accountPrefs.getAccountId() ?: error("not registered")

        // Encrypt with Signal Protocol (Double Ratchet).
        // Use the peer's account ID (not convId) as the session address.
        // Falls back to Base64 if no session exists yet.
        val peerId = conversationDao.getById(convId)?.peerAccountId?.takeIf { it.isNotEmpty() }
        val ciphertext = if (peerId != null) {
            runCatching {
                cryptoManager.encrypt(peerId, plaintext)
            }.getOrElse {
                AndroidBase64.encodeToString(plaintext.toByteArray(), AndroidBase64.NO_WRAP)
            }
        } else {
            AndroidBase64.encodeToString(plaintext.toByteArray(), AndroidBase64.NO_WRAP)
        }

        val resp = chatApi.sendMessage(convId, SendMessageRequest(type = 1, ciphertext = ciphertext))

        // Optimistically insert into local DB
        val expiresAt = if (disappearingMessages.value)
            System.currentTimeMillis() + DISAPPEARING_TTL_MS else 0L
        messageDao.upsert(
            MessageEntity(
                id             = resp.id,
                conversationId = convId,
                ciphertext     = ciphertext,
                plaintextCache = plaintext,
                senderId       = myId,
                myAccountId    = myId,
                sentAt         = resp.sent_at,
                delivered      = false,
                expiresAt      = expiresAt,
            )
        )
        conversationDao.updateLastMessage(convId, plaintext, resp.sent_at)
    }

    // ── Sync ──────────────────────────────────────────────────────────────────

    private suspend fun syncConversations() {
        val myId = accountPrefs.getAccountId() ?: return
        runCatching {
            val dtos = chatApi.listConversations()
            val entities = dtos.map { dto ->
                val peerProfileName = dto.peer_id
                    .takeIf { it.isNotBlank() }
                    ?.let { peerId ->
                        runCatching { userApi.getProfile(peerId).display_name }
                            .getOrNull()
                            ?.takeIf { it.isNotBlank() }
                    }
                val finalName = peerProfileName ?: DEFAULT_PEER_NAME

                // Preserve the local preview if the server didn't send one
                // (older servers, transient failure in chat's GetHistory call).
                // Otherwise the list would regress to "No messages" after each
                // sync even though we clearly have messages locally.
                val existing = conversationDao.getById(dto.id)
                val serverPreview = dto.last_message?.let {
                    decryptPreview(it.sender_id, it.ciphertext)
                }
                val serverTime = dto.last_message?.sent_at
                val preview    = serverPreview ?: existing?.lastMessagePreview
                val time       = serverTime    ?: existing?.lastMessageTime

                ConversationEntity(
                    id                 = dto.id,
                    displayName        = finalName,
                    lastMessagePreview = preview,
                    lastMessageTime    = time,
                    unreadCount        = dto.unread_count,
                    online             = dto.online,
                    peerAccountId      = dto.peer_id,
                )
            }
            conversationDao.upsertAll(entities)
        }
    }

    companion object {
        /** Shown when a peer has not set a display name yet. */
        const val DEFAULT_PEER_NAME = "Пользователь Koto"
    }

    private suspend fun syncMessages(convId: String) {
        val myId = accountPrefs.getAccountId() ?: return
        runCatching {
            val dtos = chatApi.getMessages(convId)
            val entities = dtos.map { dto ->
                MessageEntity(
                    id             = dto.id,
                    conversationId = convId,
                    ciphertext     = dto.ciphertext,
                    plaintextCache = decryptMessage(dto.sender_id, dto.ciphertext),
                    senderId       = dto.sender_id,
                    myAccountId    = myId,
                    sentAt         = dto.sent_at,
                    delivered      = dto.delivered,
                )
            }
            messageDao.upsertAll(entities)
        }
    }

    // ── WebSocket events ──────────────────────────────────────────────────────

    private fun observeWebSocket() {
        scope.launch {
            webSocket.events.collect { event ->
                val myId = accountPrefs.getAccountId() ?: return@collect
                when (event) {
                    is WsEvent.NewMessage -> {
                        val plaintext = decryptMessage(event.senderId, event.ciphertext)
                        val expiresAt = if (disappearingMessages.value)
                            System.currentTimeMillis() + DISAPPEARING_TTL_MS else 0L
                        messageDao.upsert(
                            MessageEntity(
                                id             = event.messageId,
                                conversationId = event.conversationId,
                                ciphertext     = event.ciphertext,
                                plaintextCache = plaintext,
                                senderId       = event.senderId,
                                myAccountId    = myId,
                                sentAt         = event.sentAt,
                                delivered      = false,
                                expiresAt      = expiresAt,
                            )
                        )
                        conversationDao.updateLastMessage(event.conversationId, plaintext, event.sentAt)
                    }
                    is WsEvent.Delivered -> messageDao.markDelivered(event.messageId)
                    is WsEvent.PresenceUpdate -> conversationDao.updateOnline(event.accountId, event.online)
                    else -> Unit
                }
            }
        }
    }

    // ── Crypto ────────────────────────────────────────────────────────────────

    private suspend fun decryptMessage(senderId: String, ciphertext: String): String =
        // Try real Signal decryption first; fall back to legacy Base64 decode.
        cryptoManager.decrypt(senderId, ciphertext)
            ?: runCatching {
                String(AndroidBase64.decode(ciphertext, AndroidBase64.NO_WRAP))
            }.getOrDefault("[зашифровано]")

    private suspend fun decryptPreview(senderId: String, ciphertext: String): String {
        val text = decryptMessage(senderId, ciphertext)
        return if (text.length > 60) text.take(60) + "…" else text
    }
}
