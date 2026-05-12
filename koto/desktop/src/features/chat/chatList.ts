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
import { pendingRequestPeerIds } from "@/features/chat/friendsSidebar";
import { getPlaintext, rememberPlaintext } from "@/features/chat/messageCache";
import { decryptFromPeer, isCryptoReady } from "@/shared/services/cryptoSession";
import { tryParseMediaEnvelope, mediaPreview } from "@/features/chat/mediaEnvelope";

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
  last_message?: {
    sent_at?: number;
    id?: string;
    ciphertext?: string;
    sender_id?: string;
  } | null;
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
    // На любое входящее от незнакомца — обновим friends-overview (новый
    // pending-запрос мог прилететь). Делаем это до refreshChatList, чтобы
    // pendingRequestPeerIds() были свежими к моменту следующего рендера.
    if (!isOwn) {
      window.dispatchEvent(new CustomEvent("koto:friends-refresh"));
    }
    const idx = lastRows.findIndex((r) => r.id === frame.conversation_id);
    if (idx === -1) {
      // Чат ещё не в кэше — подгрузим список.
      void refreshChatList();
      return;
    }
    const row = lastRows[idx];
    row.last_message = {
      sent_at: frame.sent_at,
      id: frame.message_id || undefined,
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

  // Запросы на чат accept/block → меняется множество pending-peer'ов,
  // нужно перерендерить, чтобы скрыть/показать соответствующие диалоги.
  window.addEventListener("koto:friends-refresh", () => {
    renderFromCache();
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
    el.replaceChildren();
    return;
  }
  el.classList.remove("chatlist-hint--empty");
  el.textContent = text;
}

type EmptyKind = "no_chats" | "no_results" | "all_read" | "no_groups" | "search_loading";

function setEmptyState(kind: EmptyKind): void {
  const el = document.getElementById("chat-list-hint");
  if (!el) return;
  if (mainNav.current.type === "Friends") {
    el.replaceChildren();
    return;
  }
  const config: Record<
    EmptyKind,
    { icon: string; title: string; subtitle: string }
  > = {
    no_chats: {
      icon: emptyIcon("chats"),
      title: "Здесь будут ваши чаты",
      subtitle: "Нажмите значок карандаша вверху, чтобы написать первое сообщение.",
    },
    all_read: {
      icon: emptyIcon("check"),
      title: "Всё прочитано",
      subtitle: "Новые сообщения появятся здесь, когда придут.",
    },
    no_groups: {
      icon: emptyIcon("group"),
      title: "Нет групп",
      subtitle: "Создайте групповой чат через значок карандаша вверху.",
    },
    no_results: {
      icon: emptyIcon("search"),
      title: "Ничего не найдено",
      subtitle: "Проверьте написание или попробуйте другой запрос.",
    },
    search_loading: {
      icon: emptyIcon("search"),
      title: "Ищем…",
      subtitle: "Сравниваем запрос с вашими чатами и контактами.",
    },
  };
  const c = config[kind];
  el.classList.add("chatlist-hint--empty");
  el.innerHTML = `
    <div class="chatlist-empty">
      <div class="chatlist-empty__icon" aria-hidden="true">${c.icon}</div>
      <p class="chatlist-empty__title">${c.title}</p>
      <p class="chatlist-empty__subtitle">${c.subtitle}</p>
    </div>
  `;
}

function emptyIcon(kind: "chats" | "check" | "group" | "search"): string {
  const stroke = "currentColor";
  switch (kind) {
    case "chats":
      return `<svg viewBox="0 0 64 64" width="56" height="56" fill="none" stroke="${stroke}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 18h28a6 6 0 0 1 6 6v16a6 6 0 0 1-6 6H26l-9 8v-8h-3a6 6 0 0 1-6-6V24a6 6 0 0 1 6-6Z"/><path d="M22 30h16M22 36h10"/></svg>`;
    case "check":
      return `<svg viewBox="0 0 64 64" width="56" height="56" fill="none" stroke="${stroke}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="32" cy="32" r="22"/><path d="m22 33 7 7 14-16"/></svg>`;
    case "group":
      return `<svg viewBox="0 0 64 64" width="56" height="56" fill="none" stroke="${stroke}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="24" cy="24" r="8"/><circle cx="44" cy="28" r="6"/><path d="M10 50c2-8 8-12 14-12s12 4 14 12M38 50c1-6 5-9 10-9s9 3 10 9"/></svg>`;
    case "search":
      return `<svg viewBox="0 0 64 64" width="56" height="56" fill="none" stroke="${stroke}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="28" cy="28" r="14"/><path d="m38 38 12 12"/></svg>`;
  }
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
  // Если id известен и расшифровка/собственный plaintext в кеше — покажем текст.
  if (lm.id) {
    const plain = getPlaintext(lm.id);
    if (plain) {
      const env = tryParseMediaEnvelope(plain);
      if (env) return mediaPreview(env);
      const t = plain.trim();
      return t.length > 56 ? t.slice(0, 56) + "…" : t;
    }
  }
  return "Зашифрованное сообщение";
}

function applyFilters(rows: ConversationRow[]): ConversationRow[] {
  let out = rows;
  // Session-style: чаты от незнакомцев (пока pending-запрос на чат) скрыты
  // из основного списка — они показываются только в «Запросы на чат».
  const pendingPeers = pendingRequestPeerIds();
  if (pendingPeers.size > 0) {
    out = out.filter((r) => {
      const peer = r.peer_id?.trim();
      if (peer && pendingPeers.has(peer)) return false;
      return true;
    });
  }
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
  if (el) {
    if (totalUnread > 0) {
      el.textContent = String(totalUnread);
      el.removeAttribute("hidden");
    } else {
      el.setAttribute("hidden", "");
    }
  }

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
    const kind: EmptyKind =
      searchQuery.trim() !== ""
        ? "no_results"
        : activeTab === "unread"
          ? "all_read"
          : activeTab === "groups"
            ? "no_groups"
            : "no_chats";
    setEmptyState(kind);
    return;
  }
  setHint("");

  if (searchQuery.trim().length >= 2) {
    if (searchLoading) {
      setEmptyState("search_loading");
    } else if (!searchChatsRemote.length && !searchUsersRemote.length && !filtered.length) {
      setEmptyState("no_results");
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
    // Фоном расшифровываем превью входящих last_message — чтобы в списке
    // чатов показывался текст вместо «Зашифрованное сообщение» (как в TG).
    void decryptPreviewsBackground();
  } catch (e) {
    stopPresencePolling();
    lastRows = [];
    setHint(formatInvokeError(e));
  }
}

async function decryptPreviewsBackground(): Promise<void> {
  if (!isCryptoReady()) return;
  const selfId = (loadSession()?.accountId || "").trim();
  await Promise.all(
    lastRows.map(async (row) => {
      const lm = row.last_message;
      if (!lm?.id || !lm.ciphertext || !lm.sender_id) return;
      if (lm.sender_id === selfId) return; // свой plaintext помнится через rememberPlaintext в момент send
      if (getPlaintext(lm.id) !== undefined) return;
      try {
        const plain = await decryptFromPeer(lm.sender_id, lm.ciphertext);
        rememberPlaintext(lm.id, plain);
      } catch {
        /* ignore — оставим Зашифрованное */
      }
    }),
  );
  renderFromCache();
}
