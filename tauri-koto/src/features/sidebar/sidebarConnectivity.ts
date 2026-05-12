import { hasSession, clearSession, clearSecrets, loadSession } from "@/shared/state/sessionStore";
import { clearCryptoSession } from "@/shared/services/cryptoSession";
import { clearMessageCache } from "@/features/chat/messageCache";
import { clearOfflineCache } from "@/features/chat/offlineCache";
import { mainNav, Screen } from "@/shared/state/navStore";
import { initUserbarDrawerEnhancements, syncUserbarDrawerUi } from "@/features/sidebar/userbarDrawer";
import { startKotoWsIfSession } from "@/shared/services/wsService";

/** Есть ли живой WS к шлюзу (события + «онлайн» для друзей). */
let wsGatewayConnected = false;
/** Последнее состояние из `koto-ws-status` (для баннера). */
let lastWsState = "";

function hashHue(id: string): number {
  let h = 0;
  for (let i = 0; i < id.length; i += 1) h = id.charCodeAt(i) + ((h << 5) - h);
  return Math.abs(h) % 360;
}

/** Нижняя панель профиля (как в Discord) — имя, статус, аватар из сессии. */
export function syncUserbarFromSession(): void {
  const bar = document.getElementById("chatlist-userbar");
  const nameEl = document.getElementById("sidebar-user-name");
  const statusEl = document.getElementById("sidebar-user-status");
  const avatar = document.getElementById("sidebar-user-avatar");
  const railAvatar = document.getElementById("sidebar-rail-user-avatar");

  if (!bar || !nameEl || !statusEl || !avatar) return;

  const session = loadSession();
  if (!session?.accessToken) {
    bar.classList.add("chatlist-userbar--guest");
    nameEl.textContent = "Гость";
    statusEl.textContent = "Войдите в аккаунт";
    avatar.textContent = "?";
    avatar.style.removeProperty("background");
    if (railAvatar) {
      railAvatar.textContent = "?";
      (railAvatar as HTMLElement).style.removeProperty("background");
    }
    applyWsStatusDot("idle");
    syncUserbarDrawerUi();
    return;
  }

  bar.classList.remove("chatlist-userbar--guest");
  const id = session.accountId?.trim() || "";
  if (id.length >= 12) {
    nameEl.textContent = `${id.slice(0, 6)}…${id.slice(-4)}`;
  } else if (id) {
    nameEl.textContent = id;
  } else {
    nameEl.textContent = "Аккаунт";
  }

  refreshUserbarPresenceLine();

  const hex = id.replace(/[^0-9a-fA-F]/g, "");
  if (hex.length >= 2) {
    avatar.textContent = hex.slice(0, 2).toUpperCase();
    if (railAvatar) railAvatar.textContent = hex.slice(0, 2).toUpperCase();
  } else {
    avatar.textContent = (id.slice(0, 1) || "?").toUpperCase();
    if (railAvatar) railAvatar.textContent = (id.slice(0, 1) || "?").toUpperCase();
  }
  if (id) {
    const hue = hashHue(id);
    avatar.style.background = `hsl(${hue} 46% 42%)`;
    if (railAvatar) (railAvatar as HTMLElement).style.background = `hsl(${hue} 46% 42%)`;
  }
  syncUserbarDrawerUi();
}

function refreshUserbarPresenceLine(): void {
  const statusEl = document.getElementById("sidebar-user-status");
  const bar = document.getElementById("chatlist-userbar");
  if (!statusEl || !bar || bar.classList.contains("chatlist-userbar--guest")) return;
  statusEl.textContent = wsGatewayConnected ? "В сети" : "Нет связи";
}

function applyWsStatusDot(state: string): void {
  const dot = document.getElementById("sidebar-connection-dot");
  if (!dot) return;
  dot.classList.remove(
    "chatlist-connection-dot--idle",
    "chatlist-connection-dot--rest-ok",
    "chatlist-connection-dot--ws-connected",
    "chatlist-connection-dot--ws-disconnected",
    "chatlist-connection-dot--stopped",
    "chatlist-connection-dot--ws-error",
    "chatlist-connection-dot--rest-fail"
  );
  if (state === "connected") {
    dot.classList.add("chatlist-connection-dot--ws-connected");
    dot.setAttribute("title", "Соединение активно");
  } else if (state === "disconnected") {
    dot.classList.add("chatlist-connection-dot--ws-disconnected");
    dot.setAttribute("title", "Переподключение…");
  } else if (state === "stopped") {
    dot.classList.add("chatlist-connection-dot--stopped");
    dot.setAttribute("title", "Отключено");
  } else if (state === "error" || state === "unauthorized") {
    dot.classList.add("chatlist-connection-dot--ws-error");
    dot.setAttribute("title", "Ошибка");
  } else {
    dot.classList.add("chatlist-connection-dot--idle");
    dot.setAttribute("title", "");
  }
}

function initUserbarDrawer(): void {
  if (document.body.dataset.kotoUserbarDrawer === "1") return;
  document.body.dataset.kotoUserbarDrawer = "1";

  const expand = document.getElementById("chatlist-userbar-expand");
  const drawer = document.getElementById("chatlist-userbar-drawer");
  if (!expand || !drawer) return;

  const setOpen = (open: boolean) => {
    expand.setAttribute("aria-expanded", open ? "true" : "false");
    drawer.classList.toggle("chatlist-userbar__drawer--open", open);
    drawer.setAttribute("aria-hidden", open ? "false" : "true");
    expand.classList.toggle("chatlist-userbar__expand--open", open);
  };

  expand.addEventListener("click", (e) => {
    e.stopPropagation();
    const open = expand.getAttribute("aria-expanded") === "true";
    setOpen(!open);
  });

  const openSettingsFromProfile = () => {
    window.dispatchEvent(new CustomEvent("koto:open-settings"));
  };

  document.getElementById("chatlist-userbar-profile-hit")?.addEventListener("click", openSettingsFromProfile);
  document.getElementById("chatlist-userbar-open-settings-row")?.addEventListener("click", openSettingsFromProfile);
}

function syncConnectivityBanner(): void {
  const bar = document.getElementById("chatlist-connectivity-banner");
  const msg = document.getElementById("chatlist-connectivity-banner-msg");
  const retry = document.getElementById("chatlist-connectivity-retry");
  if (!bar || !msg || !retry) return;

  if (!hasSession()) {
    bar.setAttribute("hidden", "");
    retry.setAttribute("hidden", "");
    return;
  }

  if (lastWsState === "connected") {
    bar.setAttribute("hidden", "");
    retry.setAttribute("hidden", "");
    return;
  }

  bar.removeAttribute("hidden");
  bar.classList.remove("chatlist-connectivity-banner--warn", "chatlist-connectivity-banner--error");

  if (lastWsState === "disconnected") {
    bar.classList.add("chatlist-connectivity-banner--warn");
    msg.textContent = "Связь с сервером потеряна. Пробуем переподключиться…";
    retry.setAttribute("hidden", "");
  } else if (lastWsState === "error") {
    bar.classList.add("chatlist-connectivity-banner--error");
    msg.textContent = "Не удалось установить соединение. Проверьте сеть и настройки.";
    retry.removeAttribute("hidden");
  } else if (lastWsState === "stopped") {
    bar.classList.add("chatlist-connectivity-banner--warn");
    msg.textContent = "Соединение с сервером закрыто.";
    retry.removeAttribute("hidden");
  } else {
    msg.textContent = "Подключение к серверу…";
    retry.setAttribute("hidden", "");
  }
}

function initConnectivityBanner(): void {
  if (document.body.dataset.kotoConnectivityBanner === "1") return;
  document.body.dataset.kotoConnectivityBanner = "1";
  document.getElementById("chatlist-connectivity-retry")?.addEventListener("click", () => {
    void startKotoWsIfSession().catch(console.error);
  });
}

function initWsStatusSidebar(): void {
  if (document.body.dataset.kotoSidebarWsUi === "1") return;
  document.body.dataset.kotoSidebarWsUi = "1";

  window.addEventListener("koto:ws-status", (ev) => {
    const d = (ev as CustomEvent<{ state?: string }>).detail;
    const st = d?.state ?? "";
    lastWsState = st;
    wsGatewayConnected = st === "connected";
    const dot =
      st === "connected"
        ? "connected"
        : st === "disconnected"
          ? "disconnected"
          : st === "stopped"
            ? "stopped"
            : st === "error"
              ? "error"
              : "idle";
    applyWsStatusDot(dot);
    refreshUserbarPresenceLine();
    syncConnectivityBanner();
  });
  syncConnectivityBanner();
}

/** Очистить сессию и снова показать экран входа (как desktop `onSignOut`). */
export function performSignOut(): void {
  mainNav.resetTo(Screen.empty());
  clearSession();
  clearSecrets();
  clearMessageCache();
  clearOfflineCache();
  void clearCryptoSession();
  const ul = document.getElementById("chat-list");
  if (ul) ul.innerHTML = "";
  const authLayer = document.getElementById("auth-layer");
  authLayer?.removeAttribute("hidden");
  syncSessionChrome();
  window.dispatchEvent(new CustomEvent("koto:signed-out"));
}

/** Подсказка над списком чатов — вызывать после входа/выхода. */
export function syncSessionChrome(): void {
  const listHint = document.getElementById("chat-list-hint");
  if (hasSession()) {
    if (listHint) listHint.textContent = "";
  } else {
    if (listHint) listHint.textContent = "Войдите, чтобы загрузить диалоги.";
  }
  syncUserbarFromSession();
}

export async function initSidebarConnectivity(): Promise<void> {
  syncSessionChrome();
  window.addEventListener("koto:session-changed", () => {
    syncSessionChrome();
    syncConnectivityBanner();
  });

  initUserbarDrawer();
  initUserbarDrawerEnhancements();
  initConnectivityBanner();
  initWsStatusSidebar();
}
