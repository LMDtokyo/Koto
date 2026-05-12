import { loadSession, saveSession } from "@/shared/state/sessionStore";
import { isUnauthorizedApiError } from "@/shared/services/invokeError";

const invoke = window.__TAURI__?.core?.invoke?.bind(window.__TAURI__.core);

function requireInvoke(): NonNullable<typeof invoke> {
  if (!invoke) throw new Error("Tauri runtime недоступен");
  return invoke;
}

export async function authRegisterNew(): Promise<unknown> {
  return requireInvoke()("auth_register_new");
}

/** 12 слов для UI-регистрации (без сервера), как desktop `pickSeed`. */
export async function authGenerateMnemonic(): Promise<string[]> {
  return requireInvoke()("auth_generate_mnemonic");
}

export async function authPreviewAccountId(seedPhrase: string[]): Promise<string> {
  return requireInvoke()("auth_preview_account_id", { seedPhrase });
}

export async function authRegisterQuizChoices(
  seedPhrase: string[],
  round: number
): Promise<string[]> {
  return requireInvoke()("auth_register_quiz_choices", { seedPhrase, round });
}

export async function authRegisterFinish(
  seedPhrase: string[],
  displayName: string
): Promise<Record<string, unknown>> {
  return requireInvoke()("auth_register_finish", { seedPhrase, displayName });
}

export async function authProvisionFromSeed(
  seedPhrase: string[],
  restore: boolean
): Promise<Record<string, unknown>> {
  return requireInvoke()("auth_provision_from_seed", { seedPhrase, restore });
}

export async function authRefreshTokens(refreshToken: string): Promise<Record<string, unknown>> {
  return requireInvoke()("auth_refresh_tokens", { refreshToken });
}

/** Повтор запроса с access_token; при HTTP 401 один раз обновляет пару через refresh. */
async function withRefreshedSession<T>(operation: (accessToken: string) => Promise<T>): Promise<T> {
  const s0 = loadSession();
  if (!s0?.accessToken) throw new Error("Нет сессии");
  try {
    return await operation(s0.accessToken);
  } catch (e) {
    if (!isUnauthorizedApiError(e) || !s0.refreshToken) throw e;
    const t = (await requireInvoke()("auth_refresh_tokens", {
      refreshToken: s0.refreshToken,
    })) as Record<string, unknown>;
    saveSession(t);
    const s1 = loadSession();
    if (!s1?.accessToken) throw new Error("Сессия не восстановлена");
    return await operation(s1.accessToken);
  }
}

export async function fetchConversations(): Promise<unknown[]> {
  return withRefreshedSession((accessToken) =>
    requireInvoke()("list_conversations", { accessToken })
  );
}

/** POST /v1/presence — онлайн peer-ов по активным WS на шлюзе. */
export async function fetchPeerPresence(peerIds: string[]): Promise<Record<string, boolean>> {
  return withRefreshedSession((accessToken) =>
    requireInvoke()("fetch_peer_presence", { accessToken, peerIds })
  ) as Promise<Record<string, boolean>>;
}

export async function fetchConversationMessages(
  conversationId: string,
  cursor: string | null = null,
  limit = 50
): Promise<ThreadMessageDto[]> {
  return withRefreshedSession((accessToken) =>
    requireInvoke()("list_conversation_messages", {
      accessToken,
      conversationId,
      cursor,
      limit,
    })
  );
}

export interface ThreadMessageDto {
  id?: string;
  sender_id?: string;
  sent_at?: number;
  ciphertext?: string;
  reply_to?: string | null;
  edited_at?: number;
  forwarded_from?: string | null;
  pinned?: boolean;
}

export interface SearchEnvelope<T> {
  items: T[];
  next_cursor?: string;
  has_more?: boolean;
}

export interface UserSearchDto {
  account_id: string;
  display_name?: string;
  avatar_url?: string;
  username?: string;
}

export interface ConversationSearchDto {
  id: string;
  type?: number;
  conv_type?: number;
  name?: string;
  display_name?: string;
  peer_id?: string;
  member_ids?: string[];
}

/** UTF-8 → base64 для поля `ciphertext` (до интеграции libsignal — тестовая отправка). */
export function utf8TextToCiphertextBase64(text: string): string {
  return btoa(unescape(encodeURIComponent(text)));
}

export interface SendMessageResult {
  id: string;
  sent_at: number;
}

export interface EditMessageResult {
  edited_at: number;
}

export async function editConversationMessage(
  conversationId: string,
  messageId: string,
  ciphertextBase64: string
): Promise<EditMessageResult> {
  return withRefreshedSession((accessToken) =>
    requireInvoke()<EditMessageResult>("edit_conversation_message", {
      accessToken,
      conversationId,
      messageId,
      ciphertextBase64,
    })
  );
}

export async function deleteConversationMessage(
  conversationId: string,
  messageId: string
): Promise<void> {
  await withRefreshedSession((accessToken) =>
    requireInvoke()<void>("delete_conversation_message", {
      accessToken,
      conversationId,
      messageId,
    })
  );
}

export interface ToggleReactionResult {
  added: boolean;
}

export async function toggleMessageReaction(
  conversationId: string,
  messageId: string,
  emoji: string
): Promise<ToggleReactionResult> {
  return withRefreshedSession((accessToken) =>
    requireInvoke()<ToggleReactionResult>("toggle_message_reaction", {
      accessToken,
      conversationId,
      messageId,
      emoji,
    })
  );
}

export async function sendConversationMessage(
  conversationId: string,
  opts: {
    messageType?: number;
    ciphertextBase64: string;
    clientSeq: number;
    replyTo?: string | null;
  }
): Promise<SendMessageResult> {
  const { messageType = 1, ciphertextBase64, clientSeq, replyTo = null } = opts;
  return withRefreshedSession((accessToken) =>
    requireInvoke()<SendMessageResult>("send_conversation_message", {
      accessToken,
      conversationId,
      messageType,
      ciphertextBase64,
      clientSeq,
      replyTo,
    })
  );
}

export async function createDirectConversation(
  peerAccountId: string
): Promise<{ conversation_id?: string; conversationId?: string }> {
  return withRefreshedSession((accessToken) =>
    requireInvoke()("create_direct_conversation", {
      accessToken,
      peerAccountId,
    })
  );
}

export async function sendFriendRequest(peerId: string): Promise<void> {
  await withRefreshedSession((accessToken) =>
    requireInvoke()("user_contact_request_send", { accessToken, peerId })
  );
}

export async function listIncomingFriendRequests(): Promise<Array<{ from_id: string; to_id: string; status: string }>> {
  return withRefreshedSession((accessToken) =>
    requireInvoke()("user_contact_request_incoming", { accessToken })
  );
}

export async function acceptFriendRequest(fromId: string): Promise<void> {
  await withRefreshedSession((accessToken) =>
    requireInvoke()("user_contact_request_accept", { accessToken, fromId })
  );
}

export async function canMessagePeer(peerId: string): Promise<boolean> {
  const res = (await withRefreshedSession((accessToken) =>
    requireInvoke()("user_can_message_peer", { accessToken, peerId })
  )) as { can_message?: boolean };
  return Boolean(res?.can_message);
}

export type FriendRelationState = "none" | "outgoing_pending" | "incoming_pending" | "accepted";

export async function getFriendRelation(peerId: string): Promise<FriendRelationState> {
  const res = (await withRefreshedSession((accessToken) =>
    requireInvoke()("user_friend_relation", { accessToken, peerId })
  )) as { state?: string };
  const s = String(res?.state || "none") as FriendRelationState;
  return s;
}

export async function rejectFriendRequest(fromId: string): Promise<void> {
  await withRefreshedSession((accessToken) =>
    requireInvoke()("user_contact_request_reject", { accessToken, fromId })
  );
}

export interface FriendSummaryDto {
  peer_id: string;
  display_name?: string;
  username?: string;
  avatar_url?: string;
  relation_at?: number;
}

export interface FriendsOverviewDto {
  friends: FriendSummaryDto[];
  incoming: FriendSummaryDto[];
  outgoing: FriendSummaryDto[];
}

export async function fetchFriendsOverview(): Promise<FriendsOverviewDto> {
  return withRefreshedSession((accessToken) =>
    requireInvoke()("user_friends_overview", { accessToken })
  ) as Promise<FriendsOverviewDto>;
}

export async function searchUsers(
  query: string,
  limit = 20,
  cursor: string | null = null
): Promise<SearchEnvelope<UserSearchDto>> {
  return withRefreshedSession((accessToken) =>
    requireInvoke()("search_users", { accessToken, query, limit, cursor })
  );
}

export async function searchConversations(
  query: string,
  limit = 20,
  cursor: string | null = null
): Promise<SearchEnvelope<ConversationSearchDto>> {
  return withRefreshedSession((accessToken) =>
    requireInvoke()("search_conversations", { accessToken, query, limit, cursor })
  );
}

export async function searchConversationMessagesMeta(
  conversationId: string,
  opts: {
    senderId?: string | null;
    fromTs?: number | null;
    toTs?: number | null;
    messageType?: number | null;
    cursor?: string | null;
    limit?: number;
  } = {}
): Promise<SearchEnvelope<ThreadMessageDto>> {
  const { senderId = null, fromTs = null, toTs = null, messageType = null, cursor = null, limit = 50 } = opts;
  return withRefreshedSession((accessToken) =>
    requireInvoke()("search_conversation_messages_meta", {
      accessToken,
      conversationId,
      senderId,
      fromTs,
      toTs,
      messageType,
      cursor,
      limit,
    })
  );
}
