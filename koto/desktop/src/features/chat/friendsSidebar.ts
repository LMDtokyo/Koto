import {
  acceptFriendRequest,
  createDirectConversation,
  fetchConversationMessages,
  fetchConversations,
  fetchFriendsOverview,
  fetchPeerPresence,
  rejectFriendRequest,
  type FriendSummaryDto,
  type FriendsOverviewDto,
} from "@/shared/services/authService";
import { formatInvokeError } from "@/shared/services/invokeError";
import { loadSession } from "@/shared/state/sessionStore";
import { mainNav, Screen } from "@/shared/state/navStore";
import { navigateToChat } from "@/features/chat/chatThread";
import { refreshChatList } from "@/features/chat/chatList";
import { decryptFromPeer } from "@/shared/services/cryptoSession";

let lastOverview: FriendsOverviewDto | null = null;
let friendsTab: "all" | "pending" = "all";
const previewCache = new Map<string, string>();

/** Список peer-ID чьи сообщения сейчас «pending request» — chat-list их прячет. */
export function pendingRequestPeerIds(): Set<string> {
  const out = new Set<string>();
  if (!lastOverview) return out;
  for (const p of lastOverview.incoming) {
    if (p.peer_id) out.add(p.peer_id);
  }
  return out;
}

/** Найти conversation_id для DM с peer'ом и расшифровать последнее сообщение. */
async function loadMessagePreview(peerId: string): Promise<string> {
  const cached = previewCache.get(peerId);
  if (cached !== undefined) return cached;
  try {
    const convs = (await fetchConversations()) as Array<Record<string, unknown>>;
    const dm = convs.find((c) => {
      const memberIds = (c.member_ids ?? c.memberIds) as string[] | undefined;
      const peerField = (c.peer_id ?? c.peerId) as string | undefined;
      const t = (c.type ?? c.conv_type) as number | undefined;
      if (peerField === peerId) return true;
      if (t === 1 && Array.isArray(memberIds) && memberIds.includes(peerId)) return true;
      return false;
    });
    const convId = dm ? String(dm.id ?? dm.conversation_id ?? "") : "";
    if (!convId) return "";
    const msgs = await fetchConversationMessages(convId, null, 5);
    const fromPeer = msgs.find((m) => m.sender_id === peerId && m.ciphertext);
    if (!fromPeer?.ciphertext) return "";
    const plaintext = await decryptFromPeer(peerId, fromPeer.ciphertext).catch(() => "");
    const trimmed = plaintext.trim().slice(0, 120);
    previewCache.set(peerId, trimmed);
    return trimmed;
  } catch {
    return "";
  }
}

function hashHue(id: string): number {
  let h = 0;
  const s = id || "";
  for (let i = 0; i < s.length; i += 1) h = s.charCodeAt(i) + ((h << 5) - h);
  return Math.abs(h) % 360;
}

function initialsFor(peer: FriendSummaryDto): string {
  const name = (peer.display_name || "").trim();
  if (name.length >= 2) {
    const parts = name.split(/\s+/).filter(Boolean);
    if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
    return name.slice(0, 2).toUpperCase();
  }
  const id = peer.peer_id || "?";
  return id.slice(0, 2).toUpperCase();
}

function titleFor(peer: FriendSummaryDto): string {
  const n = (peer.display_name || "").trim();
  if (n) return n;
  const u = (peer.username || "").trim();
  if (u) return `@${u}`;
  return peer.peer_id.slice(0, 12) + (peer.peer_id.length > 12 ? "…" : "");
}

function subFor(peer: FriendSummaryDto): string {
  const u = (peer.username || "").trim();
  if (u) return `@${u}`;
  return peer.peer_id.length > 16 ? `${peer.peer_id.slice(0, 14)}…` : peer.peer_id;
}

function syncRailFriendsActive(): void {
  const btn = document.getElementById("sidebar-friends-btn");
  if (!btn) return;
  const on = mainNav.current.type === "Friends";
  btn.classList.toggle("sidebar-rail__btn--active", on);
  btn.setAttribute("aria-current", on ? "page" : "false");
}

function syncFriendTabsUi(): void {
  document.querySelectorAll<HTMLButtonElement>(".friends-sidebar__tab[data-friends-tab]").forEach((b) => {
    const t = b.dataset.friendsTab as "all" | "pending" | undefined;
    const on = t === friendsTab;
    b.classList.toggle("friends-sidebar__tab--active", on);
    b.setAttribute("aria-selected", on ? "true" : "false");
  });
}

async function openDmWithPeer(peer: FriendSummaryDto): Promise<void> {
  const peerId = peer.peer_id.trim();
  if (!peerId) return;
  const title = titleFor(peer);
  const res = await createDirectConversation(peerId);
  const raw = res as Record<string, unknown>;
  const convId = String(raw.conversation_id ?? raw.conversationId ?? "").trim();
  if (!convId) throw new Error("Нет conversation_id");
  let online = false;
  try {
    const pr = await fetchPeerPresence([peerId]);
    online = Boolean((pr as Record<string, boolean>)[peerId]);
  } catch {
    /* optional */
  }
  navigateToChat(convId, title, peerId, online);
  void refreshChatList().catch(() => {});
}

function renderSectionLabel(text: string): HTMLLIElement {
  const li = document.createElement("li");
  li.className = "friends-list__section";
  const span = document.createElement("span");
  span.className = "friends-list__section-label";
  span.textContent = text;
  li.appendChild(span);
  return li;
}

function renderFriendRow(peer: FriendSummaryDto, kind: "friend" | "incoming" | "outgoing"): HTMLLIElement {
  const li = document.createElement("li");
  li.className = "friends-list__row";

  const av = document.createElement("div");
  av.className = "friends-list__avatar";
  av.style.setProperty("--friends-av-hue", String(hashHue(peer.peer_id)));
  av.setAttribute("aria-hidden", "true");
  av.textContent = initialsFor(peer);

  const body = document.createElement("div");
  body.className = "friends-list__body";
  const t = document.createElement("div");
  t.className = "friends-list__title";
  t.textContent = titleFor(peer);
  const s = document.createElement("div");
  s.className = "friends-list__sub";
  s.textContent = subFor(peer);
  body.appendChild(t);
  body.appendChild(s);

  li.appendChild(av);
  li.appendChild(body);

  if (kind === "friend") {
    li.setAttribute("role", "button");
    li.tabIndex = 0;
    const open = (): void => {
      void openDmWithPeer(peer).catch((e) => console.error(formatInvokeError(e)));
    };
    li.addEventListener("click", open);
    li.addEventListener("keydown", (ev) => {
      if (ev.key === "Enter" || ev.key === " ") {
        ev.preventDefault();
        open();
      }
    });
  }

  if (kind === "incoming") {
    // Превью первого сообщения от автора запроса (Session-style — видно ЧТО написали).
    const previewEl = document.createElement("div");
    previewEl.className = "friends-list__preview";
    previewEl.textContent = "Загружаем сообщение…";
    body.appendChild(previewEl);
    void loadMessagePreview(peer.peer_id)
      .then((text) => {
        previewEl.textContent = text || "Хочет написать вам";
      })
      .catch(() => {
        previewEl.textContent = "Хочет написать вам";
      });

    const actions = document.createElement("div");
    actions.className = "friends-list__actions";
    const ok = document.createElement("button");
    ok.type = "button";
    ok.className = "friends-list__btn friends-list__btn--primary";
    ok.textContent = "Принять";
    ok.addEventListener("click", (ev) => {
      ev.stopPropagation();
      void acceptFriendRequest(peer.peer_id)
        .then(() => {
          window.dispatchEvent(new CustomEvent("koto:friends-refresh"));
          void refreshChatList().catch(() => {});
        })
        .catch((e) => console.error(formatInvokeError(e)));
    });
    const no = document.createElement("button");
    no.type = "button";
    no.className = "friends-list__btn friends-list__btn--ghost";
    no.textContent = "Заблокировать";
    no.addEventListener("click", (ev) => {
      ev.stopPropagation();
      void rejectFriendRequest(peer.peer_id)
        .then(() => {
          window.dispatchEvent(new CustomEvent("koto:friends-refresh"));
          void refreshChatList().catch(() => {});
        })
        .catch((e) => console.error(formatInvokeError(e)));
    });
    actions.appendChild(ok);
    actions.appendChild(no);
    li.appendChild(actions);

    // Клик по строке (но не по кнопкам) — открывает превью-чат для просмотра.
    li.setAttribute("role", "button");
    li.tabIndex = 0;
    li.addEventListener("click", (ev) => {
      if ((ev.target as HTMLElement).closest(".friends-list__btn")) return;
      void openDmWithPeer(peer).catch((e) => console.error(formatInvokeError(e)));
    });
  }

  if (kind === "outgoing") {
    const badge = document.createElement("span");
    badge.className = "friends-list__badge";
    badge.textContent = "Отправлено";
    li.appendChild(badge);
  }

  return li;
}

function renderFriendsList(): void {
  const ul = document.getElementById("friends-list");
  const hint = document.getElementById("friends-empty-hint");
  const countEl = document.getElementById("friends-pending-count");
  if (!ul) return;

  ul.innerHTML = "";
  const data = lastOverview;
  const pendingN = data ? data.incoming.length + data.outgoing.length : 0;

  // Session-style banner над chat-list: показываем только incoming pending.
  const bannerEl = document.getElementById("chatlist-requests-banner");
  const bannerCountEl = document.getElementById("chatlist-requests-banner-count");
  const incomingN = data?.incoming.length || 0;
  if (bannerEl && bannerCountEl) {
    if (incomingN > 0) {
      bannerCountEl.textContent = String(incomingN);
      bannerEl.removeAttribute("hidden");
    } else {
      bannerEl.setAttribute("hidden", "");
    }
  }

  if (countEl) {
    if (pendingN > 0) {
      countEl.textContent = String(pendingN);
      countEl.removeAttribute("hidden");
    } else {
      countEl.textContent = "";
      countEl.setAttribute("hidden", "");
    }
  }

  if (!data) {
    if (hint) {
      hint.removeAttribute("hidden");
      hint.textContent = "Войдите в аккаунт, чтобы видеть друзей.";
    }
    return;
  }

  let empty = true;

  if (friendsTab === "all") {
    if (data.friends.length === 0) {
      if (hint) {
        hint.removeAttribute("hidden");
        hint.textContent = "Пока нет друзей. Отправьте заявку через «Новый чат».";
      }
    } else {
      empty = false;
      if (hint) hint.setAttribute("hidden", "");
      for (const p of data.friends) {
        ul.appendChild(renderFriendRow(p, "friend"));
      }
    }
    return;
  }

  if (hint) hint.setAttribute("hidden", "");

  if (data.incoming.length > 0) {
    empty = false;
    ul.appendChild(renderSectionLabel("Входящие"));
    for (const p of data.incoming) {
      ul.appendChild(renderFriendRow(p, "incoming"));
    }
  }
  if (data.outgoing.length > 0) {
    empty = false;
    ul.appendChild(renderSectionLabel("Исходящие"));
    for (const p of data.outgoing) {
      ul.appendChild(renderFriendRow(p, "outgoing"));
    }
  }

  if (empty && hint) {
    hint.removeAttribute("hidden");
    hint.textContent = "Нет ожидающих заявок.";
  }
}

export async function refreshFriendsList(): Promise<void> {
  if (!loadSession()?.accessToken) {
    lastOverview = { friends: [], incoming: [], outgoing: [] };
    renderFriendsList();
    return;
  }
  try {
    lastOverview = await fetchFriendsOverview();
  } catch (e) {
    console.error(formatInvokeError(e));
    lastOverview = { friends: [], incoming: [], outgoing: [] };
  }
  renderFriendsList();
}

export function initFriendsSidebar(): void {
  if (document.body.dataset.kotoFriendsSidebarInit === "1") return;
  document.body.dataset.kotoFriendsSidebarInit = "1";

  document.getElementById("sidebar-friends-btn")?.addEventListener("click", () => {
    mainNav.resetTo(Screen.friends());
  });

  // Banner над chat-list — открывает раздел Друзья → pending
  document.getElementById("chatlist-requests-banner")?.addEventListener("click", () => {
    mainNav.resetTo(Screen.friends());
    // Переключаем таб «Ожидают» внутри friends-стека
    setTimeout(() => {
      const pendingTab = document.querySelector<HTMLButtonElement>(
        '.friends-sidebar__tab[data-friends-tab="pending"]'
      );
      pendingTab?.click();
    }, 0);
  });

  document.querySelectorAll<HTMLButtonElement>(".friends-sidebar__tab[data-friends-tab]").forEach((btn) => {
    btn.addEventListener("click", () => {
      const t = btn.dataset.friendsTab as "all" | "pending" | undefined;
      if (!t) return;
      friendsTab = t;
      syncFriendTabsUi();
      renderFriendsList();
    });
  });

  mainNav.subscribe(() => {
    syncRailFriendsActive();
    if (mainNav.current.type === "Friends") {
      const chatHint = document.getElementById("chat-list-hint");
      if (chatHint) chatHint.textContent = "";
      void refreshFriendsList();
    }
  });

  window.addEventListener("koto:friends-refresh", () => {
    // Всегда тянем overview — pendingRequestPeerIds() и баннер «Запросы на чат»
    // зависят от свежести данных независимо от текущего экрана.
    void refreshFriendsList();
  });

  window.addEventListener("koto:session-changed", () => {
    void refreshFriendsList();
  });

  syncRailFriendsActive();
  syncFriendTabsUi();
}
