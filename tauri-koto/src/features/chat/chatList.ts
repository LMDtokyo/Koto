import {
  fetchConversations,
  fetchPeerPresence,
  searchConversations,
  searchUsers,
  type ConversationSearchDto,
  type UserSearchDto,
} from "@/shared/services/authService";
import { formatInvokeError } from "@/shared/services/invokeError";
import { loadSession } from "@/shared/state/sessionStore";
import { mainNav, Screen } from "@/shared/state/navStore";
import { navigateToChat } from "@/features/chat/chatThread";
import { openAttachOverlay } from "@/features/shell/overlaysLayer";
import { getComposerDraft } from "@/features/chat/composerDraft";

interface ConversationRow {
  id: string;
  display_name?: string;
  peer_id?: string;
  name?: string;
  /** REST: `type` (1=direct, 2=group). */
  type?: number;
  conv_type?: number;
  member_ids?: string[];
  unread_count?: number;
  online?: boolean;
  last_message?: { sent_at?: number } | null;
}

function convTypeOf(r: ConversationRow): number | undefined {
  return r.conv_type ?? r.type;
}

let activeTab = "all";
let searchQuery = "";
let lastRows: ConversationRow[] = [];
let searchChatsRemote: ConversationSearchDto[] = [];
let searchUsersRemote: UserSearchDto[] = [];
let searchLoading = false;
let searchReqSeq = 0;
let searchDebounceTimer: ReturnType<typeof setTimeout> | null = null;
let presencePollTimer: ReturnType<typeof setInterval> | null = null;

function stopPresencePolling(): void {
  if (presencePollTimer != null) {
    clearInterval(presencePollTimer);
    presencePollTimer = null;
  }
}

function uniqueDirectPeerIds(rows: ConversationRow[]): string[] {
  const s = new Set<string>();
  for (const r of rows) {
    const ct = convTypeOf(r);
    if (ct != null && ct !== 1) continue;
    const p = r.peer_id?.trim();
    if (p) s.add(p);
  }
  return [...s];
}

async function mergeGatewayPresence(): Promise<void> {
  const peers = uniqueDirectPeerIds(lastRows);
  if (!peers.length) return;
  try {
    const map = await fetchPeerPresence(peers);
    for (const r of lastRows) {
      const p = r.peer_id?.trim();
      if (p && Object.prototype.hasOwnProperty.call(map, p)) {
        r.online = Boolean((map as Record<string, boolean>)[p]);
      }
    }
  } catch {
    /* опционально: шлюз без /v1/presence — список всё равно работает */
  }
}

function startPresencePolling(): void {
  stopPresencePolling();
  presencePollTimer = setInterval(() => {
    if (document.visibilityState !== "visible" || !lastRows.length) return;
    void mergeGatewayPresence().then(() => renderFromCache());
  }, 32_000);
}

function wirePresenceOnWs(): void {
  if (document.body.dataset.kotoChatPresenceWs === "1") return;
  document.body.dataset.kotoChatPresenceWs = "1";
  window.addEventListener("koto:ws-status", (ev) => {
    const d = (ev as CustomEvent<{ state?: string }>).detail;
    if (d?.state === "connected") {
      void mergeGatewayPresence().then(() => renderFromCache());
    }
  });

  // WS: новое сообщение → обновить last_message и unread_count для нужного чата.
  window.addEventListener("koto:ws:new-message", (ev) => {
    const frame = (ev as CustomEvent<{
      conversation_id: string;
      message_id: string;
      sender_id: string;
      ciphertext: string;
      sent_at: number;
    }>).detail;
    if (!frame?.conversation_id) return;
    const selfId = (loadSession()?.accountId || "").trim();
    const isOwn = frame.sender_id === selfId;
    const idx = lastRows.findIndex((r) => r.id === frame.conversation_id);
    if (idx === -1) {
      // Чат ещё не в кэше — подгрузим список.
      void refreshChatList();
      return;
    }
    const row = lastRows[idx];
    row.last_message = {
      sent_at: frame.sent_at,
    };
    if (!isOwn && frame.conversation_id !== currentlyOpenConvId()) {
      row.unread_count = (row.unread_count || 0) + 1;
    }
    // Поднять в начало списка.
    lastRows.splice(idx, 1);
    lastRows.unshift(row);
    renderFromCache();
  });

  // Новый чат создан — перезагрузим список.
  window.addEventListener("koto:ws:conversation-created", () => {
    void refreshChatList();
  });

  // Сбрасываем unread при смене активного чата.
  mainNav.subscribe(() => {
    const convId = currentlyOpenConvId();
    if (!convId) return;
    const row = lastRows.find((r) => r.id === convId);
    if (row && row.unread_count) {
      row.unread_count = 0;
      renderFromCache();
    }
  });
}

function currentlyOpenConvId(): string | null {
  const s = mainNav.current;
  return s.type === "Chat" ? s.convId : null;
}

function setHint(text: string): void {
  const el = document.getElementById("chat-list-hint");
  if (!el) return;
  // В режиме «Друзья» подсказка списка чатов не показываем (только контент друзей).
  if (mainNav.current.type === "Friends") {
    el.textContent = "";
    return;
  }
  el.textContent = text;
}

function hashHue(id: string): number {
  let h = 0;
  const s = id || "";
  for (let i = 0; i < s.length; i += 1) h = s.charCodeAt(i) + ((h << 5) - h);
  return Math.abs(h) % 360;
}

function initialsFromTitle(title: string, fallbackId: string): string {
  const t = (title || "").trim();
  if (!t) return (fallbackId || "?").slice(0, 2).toUpperCase();
  const parts = t.split(/\s+/).filter(Boolean);
  if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
  return t.slice(0, 2).toUpperCase();
}

function formatChatTime(ts: number | undefined): string {
  if (!ts) return "";
  const d = new Date(ts * 1000);
  const now = new Date();
  if (Number.isNaN(d.getTime())) return "";
  if (d.toDateString() === now.toDateString()) {
    return d.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" });
  }
  return d.toLocaleDateString(undefined, { day: "2-digit", month: "2-digit" });
}

function previewFromRow(row: ConversationRow): string {
  const draft = getComposerDraft(row.id).trim();
  if (draft) {
    const snippet = draft.slice(0, 56) + (draft.length > 56 ? "…" : "");
    return `Черновик: ${snippet}`;
  }
  const lm = row.last_message;
  if (!lm) return "Нет сообщений";
  return "Зашифрованное сообщение";
}

function applyFilters(rows: ConversationRow[]): ConversationRow[] {
  let out = rows;
  const q = searchQuery.trim().toLowerCase();
  if (q) {
    out = out.filter((r) => {
      const name = (r.display_name || r.peer_id || r.name || "").toLowerCase();
      const prev = previewFromRow(r).toLowerCase();
      const id = (r.id || "").toLowerCase();
      const peer = (r.peer_id || "").toLowerCase();
      return name.includes(q) || prev.includes(q) || id.includes(q) || peer.includes(q);
    });
  }
  if (activeTab === "unread") {
    out = out.filter((r) => (r.unread_count || 0) > 0);
  }
  if (activeTab === "groups") {
    out = out.filter((r) => convTypeOf(r) === 2);
  }
  return out;
}

async function runRemoteSearchDebounced(): Promise<void> {
  if (searchDebounceTimer) clearTimeout(searchDebounceTimer);
  const q = searchQuery.trim();
  if (q.length < 2) {
    searchChatsRemote = [];
    searchUsersRemote = [];
    searchLoading = false;
    renderFromCache();
    return;
  }
  searchDebounceTimer = setTimeout(async () => {
    const reqId = ++searchReqSeq;
    searchLoading = true;
    renderFromCache();
    try {
      const [chats, users] = await Promise.all([searchConversations(q, 20), searchUsers(q, 20)]);
      if (reqId !== searchReqSeq) return;
      searchChatsRemote = chats.items || [];
      searchUsersRemote = users.items || [];
    } catch {
      if (reqId !== searchReqSeq) return;
      searchChatsRemote = [];
      searchUsersRemote = [];
    } finally {
      if (reqId === searchReqSeq) {
        searchLoading = false;
        renderFromCache();
      }
    }
  }, 280);
}

function updateUnreadBadge(rows: ConversationRow[]): void {
  const totalUnread = rows.reduce((a, r) => a + (r.unread_count || 0), 0);
  const unreadChats = rows.filter((r) => (r.unread_count || 0) > 0).length;
  const groupsUnread = rows
    .filter((r) => convTypeOf(r) === 2)
    .reduce((a, r) => a + (r.unread_count || 0), 0);

  const el = document.getElementById("chatlist-unread-badge");
  if (el) el.textContent = `${totalUnread} новых`;

  setTabCount("all", totalUnread);
  setTabCount("unread", unreadChats);
  setTabCount("groups", groupsUnread);
}

function setTabCount(tab: string, n: number): void {
  const el = document.querySelector(
    `.chatlist-tab__count[data-tab-count="${tab}"]`
  ) as HTMLSpanElement | null;
  if (!el) return;
  if (n > 0) {
    el.textContent = n > 999 ? "999+" : String(n);
    el.removeAttribute("hidden");
  } else {
    el.setAttribute("hidden", "");
  }
}

function initTabs(): void {
  if (document.body.dataset.kotoChatlistTabs === "1") return;
  document.body.dataset.kotoChatlistTabs = "1";
  document.querySelectorAll(".chatlist-tab").forEach((btn) => {
    btn.addEventListener("click", () => {
      if ((btn as HTMLButtonElement).disabled) return;
      const tab = (btn as HTMLButtonElement).dataset.tab;
      if (!tab) return;
      if (tab === "stories") {
        mainNav.resetTo(Screen.stories());
        return;
      }
      activeTab = tab;
      document.querySelectorAll(".chatlist-tab").forEach((b) => {
        const on = (b as HTMLButtonElement).dataset.tab === tab;
        b.classList.toggle("chatlist-tab--active", on);
        b.setAttribute("aria-selected", on ? "true" : "false");
      });
      renderFromCache();
    });
  });
}

function initSearch(): void {
  if (document.body.dataset.kotoChatlistSearch === "1") return;
  document.body.dataset.kotoChatlistSearch = "1";
  const input = document.getElementById("chatlist-search-input") as HTMLInputElement | null;
  input?.addEventListener("input", () => {
    searchQuery = input.value || "";
    void runRemoteSearchDebounced();
    renderFromCache();
  });
  window.addEventListener("keydown", (e) => {
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") {
      e.preventDefault();
      input?.focus();
      input?.select();
      return;
    }
    if (e.key === "Escape" && document.activeElement === input) {
      if (input) input.value = "";
      searchQuery = "";
      searchChatsRemote = [];
      searchUsersRemote = [];
      renderFromCache();
    }
  });
}

function escapeHtml(s: string): string {
  return String(s)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function renderFromCache(): void {
  const ul = document.getElementById("chat-list");
  if (!ul) return;
  const filtered = applyFilters(lastRows);
  ul.innerHTML = "";
  updateUnreadBadge(lastRows);

  if (!filtered.length && !(searchQuery.trim().length >= 2 && (searchLoading || searchChatsRemote.length || searchUsersRemote.length))) {
    const empty =
      searchQuery.trim() !== ""
        ? "Ничего не найдено"
        : activeTab === "unread"
          ? "Всё прочитано"
          : activeTab === "groups"
            ? "Нет групп"
            : "Пока тихо";
    setHint(empty);
    return;
  }
  setHint("");

  if (searchQuery.trim().length >= 2) {
    if (searchLoading) {
      setHint("Ищем…");
    } else if (!searchChatsRemote.length && !searchUsersRemote.length && !filtered.length) {
      setHint("Ничего не найдено");
    }
  }

  if (searchQuery.trim().length >= 2 && searchChatsRemote.length) {
    const sec = document.createElement("li");
    sec.className = "chatlist-section";
    sec.innerHTML = `<span class="chatlist-section__label">Чаты</span>`;
    ul.appendChild(sec);
    for (const c of searchChatsRemote) {
      const row: ConversationRow = {
        id: c.id,
        type: c.type ?? c.conv_type,
        conv_type: c.conv_type ?? c.type,
        name: c.name,
        display_name: c.display_name,
        peer_id: c.peer_id,
        member_ids: c.member_ids,
      };
      const title = row.display_name?.trim() || row.peer_id?.trim() || row.name?.trim() || row.id;
      const peerKey = row.peer_id?.trim() || row.id;
      const hue = hashHue(peerKey);
      const initials = initialsFromTitle(title, row.id);
      const li = document.createElement("li");
      li.className = "chatlist-row";
      li.dataset.convId = row.id;
      li.dataset.convTitle = title;
      li.dataset.peerId = peerKey;
      li.dataset.online = "0";
      li.innerHTML = `
        <div class="chatlist-row__inner">
          <div class="chatlist-row__avatar-wrap">
            <div class="chatlist-avatar chatlist-avatar--peer" style="--av-bg:hsl(${hue} 46% 42%)">${escapeHtml(initials)}</div>
          </div>
          <div class="chatlist-row__body">
            <div class="chatlist-row__top">
              <span class="chatlist-row__name">${escapeHtml(title)}</span>
            </div>
            <div class="chatlist-row__bottom">
              <span class="chatlist-row__preview">Открыть чат</span>
            </div>
          </div>
        </div>
      `;
      ul.appendChild(li);
    }
  }

  if (searchQuery.trim().length >= 2 && searchUsersRemote.length) {
    const sec = document.createElement("li");
    sec.className = "chatlist-section";
    sec.innerHTML = `<span class="chatlist-section__label">Пользователи</span>`;
    ul.appendChild(sec);
    for (const u of searchUsersRemote) {
      const title = (u.display_name || "").trim() || (u.username ? `@${u.username}` : u.account_id);
      const peerKey = u.account_id;
      const hue = hashHue(peerKey);
      const initials = initialsFromTitle(title, peerKey);
      const li = document.createElement("li");
      li.className = "chatlist-row";
      li.dataset.openPeerId = u.account_id;
      li.dataset.openPeerTitle = title;
      li.dataset.online = "0";
      li.innerHTML = `
        <div class="chatlist-row__inner">
          <div class="chatlist-row__avatar-wrap">
            <div class="chatlist-avatar chatlist-avatar--peer" style="--av-bg:hsl(${hue} 46% 42%)">${escapeHtml(initials)}</div>
          </div>
          <div class="chatlist-row__body">
            <div class="chatlist-row__top">
              <span class="chatlist-row__name">${escapeHtml(title)}</span>
            </div>
            <div class="chatlist-row__bottom">
              <span class="chatlist-row__preview">${escapeHtml(u.username ? `@${u.username}` : u.account_id)}</span>
            </div>
          </div>
        </div>
      `;
      ul.appendChild(li);
    }
  }

  for (const row of filtered) {
    const title =
      row.display_name?.trim() || row.peer_id?.trim() || row.name?.trim() || row.id;
    const peerKey = row.peer_id?.trim() || row.id;
    const hue = hashHue(peerKey);
    const initials = initialsFromTitle(title, row.id);
    const lm = row.last_message;
    const time = formatChatTime(lm?.sent_at);
    const unread = row.unread_count || 0;
    const online = Boolean(row.online);

    const li = document.createElement("li");
    li.className = "chatlist-row";
    li.dataset.convId = row.id;
    li.dataset.convTitle = title;
    li.dataset.peerId = peerKey;
    li.dataset.online = online ? "1" : "0";

    li.innerHTML = `
      <div class="chatlist-row__inner">
        <div class="chatlist-row__avatar-wrap">
          <div class="chatlist-avatar chatlist-avatar--peer ${online ? "chatlist-avatar--online" : ""}" style="--av-bg:hsl(${hue} 46% 42%)">${escapeHtml(initials)}</div>
        </div>
        <div class="chatlist-row__body">
          <div class="chatlist-row__top">
            <span class="chatlist-row__name">${escapeHtml(title)}</span>
            <span class="chatlist-row__time ${unread > 0 ? "chatlist-row__time--unread" : ""}">${escapeHtml(time)}</span>
          </div>
          <div class="chatlist-row__bottom">
            <span class="chatlist-row__preview">${escapeHtml(previewFromRow(row))}</span>
            ${unread > 0 ? `<span class="chatlist-row__badge">${unread}</span>` : ""}
          </div>
        </div>
      </div>
    `;
    ul.appendChild(li);
  }
}

/** Кнопки шапки списка и архив — пока заглушки в духе desktop PlaceholderScreen. */
export function initChatListSidebarStubs(): void {
  if (document.body.dataset.kotoChatlistSidebarStubs === "1") return;
  document.body.dataset.kotoChatlistSidebarStubs = "1";

  document.getElementById("sidebar-bots-btn")?.addEventListener("click", () => {
    mainNav.resetTo(Screen.bots());
  });
  document.getElementById("sidebar-camera-btn")?.addEventListener("click", () => {
    openAttachOverlay();
  });
  document.getElementById("chatlist-archive-row")?.addEventListener("click", () => {
    mainNav.resetTo(Screen.archive());
  });
}

function initListDelegation(): void {
  const ul = document.getElementById("chat-list");
  if (!ul || ul.dataset.kotoDelegated === "1") return;
  ul.dataset.kotoDelegated = "1";
  ul.addEventListener("click", (e) => {
    const li = (e.target as HTMLElement).closest(".chatlist-row") as HTMLElement | null;
    if (!li) return;
    const openPeer = li.dataset.openPeerId;
    if (openPeer) {
      window.dispatchEvent(
        new CustomEvent("koto:new-chat-prefill", {
          detail: { peerId: openPeer, title: li.dataset.openPeerTitle || openPeer },
        })
      );
      mainNav.resetTo(Screen.newChat());
      return;
    }
    if (!li.dataset.convId) return;
    ul.querySelectorAll(".chatlist-row--active").forEach((x) => x.classList.remove("chatlist-row--active"));
    li.classList.add("chatlist-row--active");
    navigateToChat(
      li.dataset.convId,
      li.dataset.convTitle || "",
      li.dataset.peerId || "",
      li.dataset.online === "1"
    );
  });
}

export async function refreshChatList(): Promise<void> {
  initListDelegation();
  initTabs();
  initSearch();
  if (document.body.dataset.kotoChatlistDraftListen !== "1") {
    document.body.dataset.kotoChatlistDraftListen = "1";
    window.addEventListener("koto:composer-draft-updated", () => renderFromCache());
  }
  wirePresenceOnWs();

  const session = loadSession();
  if (!session?.accessToken) {
    stopPresencePolling();
    setHint("Войдите, чтобы загрузить диалоги.");
    lastRows = [];
    renderFromCache();
    return;
  }

  setHint("Загрузка…");
  try {
    lastRows = (await fetchConversations()) as ConversationRow[];
    await mergeGatewayPresence();
    renderFromCache();
    startPresencePolling();
  } catch (e) {
    stopPresencePolling();
    lastRows = [];
    setHint(formatInvokeError(e));
  }
}
