/**
 * Маршрутизация WS-фреймов от gateway. Rust emit'ит сырой text-frame в
 * `koto:ws-frame` (CustomEvent с detail=String), мы парсим и переадресовываем
 * в typed-события, на которые подписаны chatList/chatThread.
 */

export interface NewMessageFrame {
  conversation_id: string;
  message_id: string;
  sender_id: string;
  ciphertext: string; // base64
  sent_at: number;
  reply_to?: string | null;
  forwarded_from?: string | null;
}

export interface ConversationCreatedFrame {
  conversation_id: string;
  type: number;
  name?: string;
  creator_id: string;
  member_ids: string[];
  created_at: number;
}

export interface MessageEditedFrame {
  conversation_id: string;
  message_id: string;
  sender_id: string;
  ciphertext: string;
  edited_at: number;
}

export interface MessagePinnedFrame {
  conversation_id: string;
  message_id: string;
  actor_id: string;
  pinned: boolean;
  at: number;
}

export interface ReactionFrame {
  conversation_id: string;
  message_id: string;
  actor_id: string;
  emoji: string;
  added: boolean;
}

type Envelope = { type?: string; payload?: unknown };

function emit<T>(name: string, detail: T): void {
  window.dispatchEvent(new CustomEvent<T>(name, { detail }));
}

export function initWsRouter(): void {
  if (document.body.dataset.kotoWsRouter === "1") return;
  document.body.dataset.kotoWsRouter = "1";

  window.addEventListener("koto:ws-frame", (ev) => {
    const raw = (ev as CustomEvent<unknown>).detail;
    let env: Envelope | null = null;
    try {
      env = typeof raw === "string" ? (JSON.parse(raw) as Envelope) : (raw as Envelope);
    } catch {
      return;
    }
    if (!env || typeof env.type !== "string") return;
    const payload = env.payload;
    switch (env.type) {
      case "new_message":
        emit<NewMessageFrame>("koto:ws:new-message", payload as NewMessageFrame);
        break;
      case "conversation_created":
        emit<ConversationCreatedFrame>(
          "koto:ws:conversation-created",
          payload as ConversationCreatedFrame
        );
        break;
      case "message_edited":
        emit<MessageEditedFrame>("koto:ws:message-edited", payload as MessageEditedFrame);
        break;
      case "message_pinned":
        emit<MessagePinnedFrame>("koto:ws:message-pinned", payload as MessagePinnedFrame);
        break;
      case "reaction":
        emit<ReactionFrame>("koto:ws:reaction", payload as ReactionFrame);
        break;
      default:
        // unknown type — proxy as-is для будущих расширений
        emit<unknown>(`koto:ws:${env.type}`, payload);
    }
  });
}
