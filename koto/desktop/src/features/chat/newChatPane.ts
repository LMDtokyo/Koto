import {
  createDirectConversation,
  fetchConversations,
  searchUsers,
  sendFriendRequest,
} from "@/shared/services/authService";
import { formatInvokeError } from "@/shared/services/invokeError";
import { refreshChatList } from "@/features/chat/chatList";
import { mainNav, Screen, type AppScreen } from "@/shared/state/navStore";
import { navigateToChat } from "@/features/chat/chatThread";

export function syncNewChatPaneVisibility(s: AppScreen): void {
  if (s.type !== "NewChat") return;
  const err = document.getElementById("new-chat-err");
  if (err) {
    err.textContent = "";
    err.classList.remove("new-chat-err--success", "new-chat-err--info");
  }
}

export function initNewChatPane(): void {
  if (document.body.dataset.kotoNewchatPaneInit === "1") return;
  document.body.dataset.kotoNewchatPaneInit = "1";

  const btn = document.getElementById("chatlist-new-chat-btn");
  const btnToolbar = document.getElementById("chatlist-toolbar-new-chat");
  const cancel = document.getElementById("new-chat-cancel");
  const create = document.getElementById("new-chat-create");
  const reject = document.getElementById("new-chat-reject");
  const input = document.getElementById("new-chat-peer-id") as HTMLInputElement | null;
  const err = document.getElementById("new-chat-err");
  const clear = document.getElementById("new-chat-clear");
  let resolvedPeerId = "";
  let resolvedDisplayName = "";
  let resolveReqSeq = 0;
  let resolveTimer: ReturnType<typeof setTimeout> | null = null;

  // «Запрос в друзья» больше не блокирует переписку (Signal/Session-style):
  // любой может написать, получатель решает Accept/Block. Кнопка reject в этой
  // панели больше не нужна; держим её скрытой.
  reject?.setAttribute("hidden", "");

  function setStatus(text: string, tone: "error" | "success" | "info" = "error"): void {
    if (!err) return;
    err.textContent = text;
    err.classList.remove("new-chat-err--success", "new-chat-err--info");
    if (tone === "success") err.classList.add("new-chat-err--success");
    if (tone === "info") err.classList.add("new-chat-err--info");
  }

  function syncCreateButton(): void {
    if (!create) return;
    create.textContent = "Открыть чат";
    if (resolvedPeerId) create.removeAttribute("disabled");
    else create.setAttribute("disabled", "");
  }

  function syncClearVisibility(): void {
    const v = (input?.value || "").trim();
    if (clear) clear.toggleAttribute("hidden", v.length === 0);
  }

  async function resolvePeerFromInput(raw: string): Promise<void> {
    const v = raw.trim();
    resolvedPeerId = "";
    resolvedDisplayName = "";
    if (!v) {
      setStatus("", "error");
      syncCreateButton();
      return;
    }
    if (/^[0-9a-fA-F]{64}$/.test(v)) {
      resolvedPeerId = v.toLowerCase();
      setStatus("Koto ID распознан.", "info");
      syncCreateButton();
      return;
    }
    if (!v.startsWith("@")) {
      setStatus("Введите @username или Koto ID.");
      syncCreateButton();
      return;
    }
    const q = v.slice(1).trim().toLowerCase();
    if (q.length < 2) {
      setStatus("Имя пользователя — минимум 2 символа.");
      syncCreateButton();
      return;
    }
    const reqId = ++resolveReqSeq;
    setStatus("Поиск пользователя…", "info");
    try {
      const res = await searchUsers(q, 10);
      if (reqId !== resolveReqSeq) return;
      const exact =
        res.items.find((x) => (x.username || "").toLowerCase() === q) ??
        res.items.find((x) => (x.username || "").toLowerCase().startsWith(q));
      if (!exact?.account_id) {
        setStatus("Пользователь не найден.");
        syncCreateButton();
        return;
      }
      resolvedPeerId = exact.account_id;
      resolvedDisplayName = exact.display_name || (exact.username ? `@${exact.username}` : "");
      setStatus(`Найден: ${resolvedDisplayName || resolvedPeerId.slice(0, 12) + "…"}`, "success");
      syncCreateButton();
    } catch (e) {
      if (reqId !== resolveReqSeq) return;
      setStatus(formatInvokeError(e));
    }
  }

  const onComposeClick = () => {
    setStatus("", "error");
    if (input) input.value = "";
    syncClearVisibility();
    syncCreateButton();
    mainNav.resetTo(Screen.newChat());
    input?.focus();
  };
  btn?.addEventListener("click", onComposeClick);
  btnToolbar?.addEventListener("click", onComposeClick);

  input?.addEventListener("input", () => {
    syncClearVisibility();
    if (resolveTimer) clearTimeout(resolveTimer);
    resolveTimer = setTimeout(() => {
      void resolvePeerFromInput(input.value || "");
    }, 300);
  });
  clear?.addEventListener("click", () => {
    if (input) input.value = "";
    resolvedPeerId = "";
    resolvedDisplayName = "";
    setStatus("", "error");
    syncCreateButton();
    syncClearVisibility();
    input?.focus();
  });

  cancel?.addEventListener("click", () => {
    mainNav.resetTo(Screen.empty());
  });

  document.getElementById("new-chat-new-group")?.addEventListener("click", () => {
    mainNav.push(Screen.newGroup());
  });

  window.addEventListener("koto:new-chat-prefill", (ev) => {
    const d = (ev as CustomEvent<{ peerId?: string }>).detail;
    const peerId = (d?.peerId || "").trim();
    if (!peerId) return;
    if (input) input.value = peerId;
    syncClearVisibility();
    void resolvePeerFromInput(peerId);
  });

  create?.addEventListener("click", async () => {
    const peer = resolvedPeerId || (input?.value || "").trim().replace(/\s+/g, "");
    if (!peer) {
      setStatus("Введите @username или Koto ID.");
      return;
    }
    setStatus("Открываем чат…", "info");
    try {
      // Идемпотентность direct-чата на клиенте: бэк всегда создаёт новую
      // conversation, поэтому повторное нажатие плодит дубли. Сначала ищем
      // существующий direct-чат с этим peer'ом и переиспользуем его id.
      const convId = (await findExistingDirectConvId(peer)) || (await createNewDirect(peer));
      if (!convId) throw new Error("Нет conversation id в ответе");
      // Side-effect: создаём pending-marker через friend_requests, чтобы
      // получатель увидел этот чат как «запрос на чат» (Session-style).
      // Если уже есть accepted-relation или дубликат — вызов вернёт ошибку,
      // игнорируем.
      try {
        await sendFriendRequest(peer);
        window.dispatchEvent(new CustomEvent("koto:friends-refresh"));
      } catch {
        /* peer уже знаком или request уже есть — нормально */
      }
      await refreshChatList();
      navigateToChat(convId, resolvedDisplayName || peer, peer, false);
    } catch (e) {
      setStatus(formatInvokeError(e));
    }
  });

  async function findExistingDirectConvId(peerId: string): Promise<string | null> {
    try {
      const convs = (await fetchConversations()) as Array<Record<string, unknown>>;
      const dm = convs.find((c) => {
        const t = (c.type ?? c.conv_type) as number | undefined;
        const peer = (c.peer_id ?? c.peerId) as string | undefined;
        const memberIds = (c.member_ids ?? c.memberIds) as string[] | undefined;
        if (t !== 1) return false;
        if (peer === peerId) return true;
        if (Array.isArray(memberIds) && memberIds.includes(peerId)) return true;
        return false;
      });
      if (!dm) return null;
      const id = dm.id ?? dm.conversation_id ?? dm.conversationId;
      return typeof id === "string" && id ? id : null;
    } catch {
      return null;
    }
  }

  async function createNewDirect(peerId: string): Promise<string | null> {
    const res = await createDirectConversation(peerId);
    const id = res.conversation_id ?? res.conversationId;
    return typeof id === "string" && id ? id : null;
  }

  syncCreateButton();
}
