import {
  acceptFriendRequest,
  createDirectConversation,
  getFriendRelation,
  rejectFriendRequest,
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
  const cancel = document.getElementById("new-chat-cancel");
  const create = document.getElementById("new-chat-create");
  const reject = document.getElementById("new-chat-reject");
  const input = document.getElementById("new-chat-peer-id") as HTMLInputElement | null;
  const err = document.getElementById("new-chat-err");
  const clear = document.getElementById("new-chat-clear");
  let resolvedPeerId = "";
  let resolveReqSeq = 0;
  let resolveTimer: ReturnType<typeof setTimeout> | null = null;
  let relationState: "none" | "outgoing_pending" | "incoming_pending" | "accepted" = "none";

  function setStatus(text: string, tone: "error" | "success" | "info" = "error"): void {
    if (!err) return;
    err.textContent = text;
    err.classList.remove("new-chat-err--success", "new-chat-err--info");
    if (tone === "success") err.classList.add("new-chat-err--success");
    if (tone === "info") err.classList.add("new-chat-err--info");
  }

  function syncActionUi(): void {
    if (!create) return;
    switch (relationState) {
      case "outgoing_pending":
        create.textContent = "Запрос уже отправлен";
        create.removeAttribute("disabled");
        reject?.setAttribute("hidden", "");
        break;
      case "incoming_pending":
        create.textContent = "Принять заявку";
        create.removeAttribute("disabled");
        reject?.removeAttribute("hidden");
        break;
      case "accepted":
        create.textContent = "Написать";
        create.removeAttribute("disabled");
        reject?.setAttribute("hidden", "");
        break;
      default:
        create.textContent = "Отправить запрос";
        create.removeAttribute("disabled");
        reject?.setAttribute("hidden", "");
        break;
    }
  }

  async function refreshRelation(peerID: string): Promise<void> {
    relationState = await getFriendRelation(peerID);
    syncActionUi();
    if (relationState === "accepted") {
      setStatus("Он уже у вас в друзьях.", "success");
    }
  }

  function syncClearVisibility(): void {
    const v = (input?.value || "").trim();
    if (clear) clear.toggleAttribute("hidden", v.length === 0);
  }

  async function resolvePeerFromInput(raw: string): Promise<void> {
    const v = raw.trim();
    resolvedPeerId = "";
    if (!v) {
      setStatus("", "error");
      relationState = "none";
      syncActionUi();
      return;
    }
    if (/^[0-9a-fA-F]{64}$/.test(v)) {
      resolvedPeerId = v.toLowerCase();
      setStatus("Koto ID распознан.", "info");
      await refreshRelation(resolvedPeerId);
      return;
    }
    if (!v.startsWith("@")) {
      setStatus("Введите @username или Koto ID.");
      return;
    }
    const q = v.slice(1).trim().toLowerCase();
    if (q.length < 2) {
      setStatus("Имя пользователя — минимум 2 символа.");
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
        return;
      }
      resolvedPeerId = exact.account_id;
      setStatus(`Найден: ${exact.display_name || `@${exact.username}`}`, "success");
      await refreshRelation(resolvedPeerId);
    } catch (e) {
      if (reqId !== resolveReqSeq) return;
      setStatus(formatInvokeError(e));
    }
  }

  btn?.addEventListener("click", () => {
    setStatus("", "error");
    if (input) input.value = "";
    syncClearVisibility();
    relationState = "none";
    syncActionUi();
    mainNav.resetTo(Screen.newChat());
    input?.focus();
  });

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
    relationState = "none";
    setStatus("", "error");
    syncActionUi();
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
    setStatus("", "error");
    try {
      await refreshRelation(peer);
      if (relationState === "none") {
        await sendFriendRequest(peer);
        relationState = "outgoing_pending";
        syncActionUi();
        setStatus("Запрос в друзья отправлен.", "success");
        window.dispatchEvent(new CustomEvent("koto:friends-refresh"));
        return;
      }
      if (relationState === "outgoing_pending") {
        setStatus("Запрос уже отправлен.", "success");
        return;
      }
      if (relationState === "incoming_pending") {
        await acceptFriendRequest(peer);
        relationState = "accepted";
        syncActionUi();
        setStatus("Заявка принята. Пользователь теперь в друзьях.", "success");
        window.dispatchEvent(new CustomEvent("koto:friends-refresh"));
      }
      if (relationState !== "accepted") {
        setStatus("Сначала нужна взаимная дружба.");
        return;
      }
      setStatus("Он уже у вас в друзьях. Открываю чат…", "success");
      const res = await createDirectConversation(peer);
      const convId = res.conversation_id ?? res.conversationId;
      if (!convId) throw new Error("Нет conversation id в ответе");
      await refreshChatList();
      navigateToChat(convId, peer, peer, false);
    } catch (e) {
      setStatus(formatInvokeError(e));
    }
  });

  reject?.addEventListener("click", async () => {
    const peer = resolvedPeerId || (input?.value || "").trim().replace(/\s+/g, "");
    if (!peer) return;
    try {
      await rejectFriendRequest(peer);
      relationState = "none";
      syncActionUi();
      setStatus("Заявка отклонена. Можно отправить новую.", "info");
      window.dispatchEvent(new CustomEvent("koto:friends-refresh"));
    } catch (e) {
      setStatus(formatInvokeError(e));
    }
  });
  syncActionUi();
}
