import {
  acceptFriendRequest,
  createDirectConversation,
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

let lastOverview: FriendsOverviewDto | null = null;
let friendsTab: "all" | "pending" = "all";

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
    no.textContent = "Отклонить";
    no.addEventListener("click", (ev) => {
      ev.stopPropagation();
      void rejectFriendRequest(peer.peer_id)
        .then(() => window.dispatchEvent(new CustomEvent("koto:friends-refresh")))
        .catch((e) => console.error(formatInvokeError(e)));
    });
    actions.appendChild(ok);
    actions.appendChild(no);
    li.appendChild(actions);
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
    if (mainNav.current.type === "Friends") {
      void refreshFriendsList();
    }
  });

  window.addEventListener("koto:session-changed", () => {
    void refreshFriendsList();
  });

  syncRailFriendsActive();
  syncFriendTabsUi();
}
