/**
 * UI-логика секции «Сеансы»: загрузка списка с сервера, рендер карточек,
 * отзыв чужих устройств. Текущий сеанс — без кнопки отзыва, плюс перекрытие
 * на уровне сервиса: revokeSession для своего ID не вызывается.
 */

import {
  listSessions,
  revokeOtherSessions,
  revokeSession,
  type SessionRow,
} from "@/shared/services/sessionsService";
import { loadSession } from "@/shared/state/sessionStore";

let inflight = false;
let cached: SessionRow[] = [];

function $(id: string): HTMLElement | null {
  return document.getElementById(id);
}

function setStatus(text: string, kind: "" | "error" = ""): void {
  const el = $("sessions-status");
  if (!el) return;
  el.textContent = text;
  el.classList.toggle("settings-overlay__empty-line--error", kind === "error");
}

function escapeHtml(s: string): string {
  return s.replace(/[&<>"']/g, (ch) => {
    switch (ch) {
      case "&":
        return "&amp;";
      case "<":
        return "&lt;";
      case ">":
        return "&gt;";
      case '"':
        return "&quot;";
      default:
        return "&#39;";
    }
  });
}

function platformIcon(platform: string, deviceName: string): string {
  const all = `${platform} ${deviceName}`.toLowerCase();
  if (/(ios|iphone|ipad|android|mobile|phone)/.test(all)) {
    return `<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="6" y="2" width="12" height="20" rx="2"/><path d="M11 18h2"/></svg>`;
  }
  return `<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="4" width="20" height="13" rx="2"/><path d="M8 21h8M12 17v4"/></svg>`;
}

/** Crown line — что-то типа "LINUX · KOTO DESKTOP" в Discord-стиле. */
function targetLine(s: SessionRow): string {
  const parts: string[] = [];
  const platform = (s.platform || "").trim();
  if (platform) parts.push(platform);
  // Имя клиента: убираем суффикс «(hostname)», если он есть.
  const raw = (s.device_name || "").trim();
  const client = raw.replace(/\s*\([^)]+\)\s*$/, "").trim() || raw || "Koto";
  if (client) parts.push(client);
  return parts.join(" · ");
}

function metaLine(s: SessionRow, isCurrent: boolean): string {
  const parts: string[] = [];
  if (s.client_ip) parts.push(s.client_ip);
  if (isCurrent) parts.push("текущий сеанс");
  else {
    const seen = relativeTime(s.last_seen_at);
    if (seen) parts.push(seen);
  }
  return parts.join(" · ");
}

function relativeTime(iso: string): string {
  if (!iso) return "";
  const t = Date.parse(iso);
  if (Number.isNaN(t)) return "";
  const diff = Math.max(0, Date.now() - t);
  const sec = Math.round(diff / 1000);
  if (sec < 60) return "только что";
  const min = Math.round(sec / 60);
  if (min < 60) return `${min} мин назад`;
  const hr = Math.round(min / 60);
  if (hr < 24) return `${hr} ч назад`;
  const day = Math.round(hr / 24);
  if (day < 30) return `${day} дн назад`;
  const mo = Math.round(day / 30);
  if (mo < 12) return `${mo} мес назад`;
  const yr = Math.round(mo / 12);
  return `${yr} г назад`;
}

function render(rows: SessionRow[]): void {
  const list = $("sessions-list");
  if (!list) return;
  const currentId = loadSession()?.sessionId || "";

  // Текущий — наверх.
  const sorted = [...rows].sort((a, b) => {
    if (a.id === currentId) return -1;
    if (b.id === currentId) return 1;
    return Date.parse(b.last_seen_at || "") - Date.parse(a.last_seen_at || "");
  });

  list.innerHTML = "";
  for (const s of sorted) {
    const isCurrent = s.id === currentId;
    const card = document.createElement("div");
    card.className =
      "settings-overlay__device-card" + (isCurrent ? " settings-overlay__device-card--current" : "");
    card.dataset.sessionId = s.id;
    card.innerHTML = `
      <div class="settings-overlay__device-icon" aria-hidden="true">${platformIcon(s.platform, s.device_name)}</div>
      <div class="settings-overlay__device-body">
        <span class="settings-overlay__device-target">${escapeHtml(targetLine(s))}</span>
        <span class="settings-overlay__device-meta">${escapeHtml(metaLine(s, isCurrent))}</span>
      </div>
      ${
        isCurrent
          ? `<span class="settings-overlay__chip settings-overlay__chip--ok">Это устройство</span>`
          : `<button type="button" class="settings-overlay__device-revoke" data-revoke-id="${escapeHtml(
              s.id
            )}" title="Завершить сеанс" aria-label="Завершить сеанс">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 6 6 18M6 6l12 12"/></svg>
            </button>`
      }
    `;
    list.appendChild(card);
  }

  const others = rows.filter((s) => s.id !== currentId).length;
  setStatus(others === 0 ? "Других устройств пока нет." : "");
  const revokeAllBtn = $("sessions-revoke-others") as HTMLButtonElement | null;
  if (revokeAllBtn) revokeAllBtn.disabled = others === 0 || inflight;
}

async function reload(): Promise<void> {
  if (inflight) return;
  inflight = true;
  setStatus("Загрузка…");
  try {
    cached = await listSessions();
    render(cached);
  } catch (e: unknown) {
    setStatus(formatErr(e), "error");
  } finally {
    inflight = false;
    const btn = $("sessions-revoke-others") as HTMLButtonElement | null;
    if (btn) btn.disabled = cached.filter((s) => s.id !== loadSession()?.sessionId).length === 0;
  }
}

async function onRevokeClick(sessionId: string): Promise<void> {
  const currentId = loadSession()?.sessionId || "";
  if (!sessionId || sessionId === currentId) return; // защита: свой сеанс не закрываем
  const card = document.querySelector<HTMLElement>(
    `.settings-overlay__device-card[data-session-id="${cssEscape(sessionId)}"]`
  );
  card?.classList.add("settings-overlay__device-card--busy");
  try {
    await revokeSession(sessionId);
    cached = cached.filter((s) => s.id !== sessionId);
    render(cached);
  } catch (e: unknown) {
    setStatus(formatErr(e), "error");
    card?.classList.remove("settings-overlay__device-card--busy");
  }
}

async function onRevokeAllOthers(): Promise<void> {
  const currentId = loadSession()?.sessionId || "";
  if (!currentId) return;
  const btn = $("sessions-revoke-others") as HTMLButtonElement | null;
  if (btn) btn.disabled = true;
  setStatus("Завершаем сеансы…");
  try {
    const result = await revokeOtherSessions(currentId);
    await reload();
    if (result.failed) {
      setStatus(`Завершено: ${result.ok}, не удалось: ${result.failed}.`, "error");
    } else {
      setStatus(result.ok > 0 ? `Завершено сеансов: ${result.ok}.` : "Других устройств пока нет.");
    }
  } catch (e: unknown) {
    setStatus(formatErr(e), "error");
  } finally {
    if (btn) btn.disabled = cached.filter((s) => s.id !== currentId).length === 0;
  }
}

function cssEscape(s: string): string {
  return s.replace(/[^a-zA-Z0-9_-]/g, (ch) => `\\${ch}`);
}

function formatErr(e: unknown): string {
  if (e && typeof e === "object" && "message" in e) {
    const m = (e as { message?: unknown }).message;
    if (typeof m === "string") return m;
  }
  if (e instanceof Error) return e.message;
  return String(e);
}

export function initSessionsView(): void {
  if (document.body.dataset.kotoSessionsView === "1") return;
  document.body.dataset.kotoSessionsView = "1";

  const list = $("sessions-list");
  list?.addEventListener("click", (ev) => {
    const t = ev.target as HTMLElement | null;
    const btn = t?.closest<HTMLButtonElement>("[data-revoke-id]");
    if (btn?.dataset.revokeId) void onRevokeClick(btn.dataset.revokeId);
  });

  $("sessions-revoke-others")?.addEventListener("click", () => {
    void onRevokeAllOthers();
  });

  // Перезагружаем при каждом открытии overlay (свежий статус активности).
  window.addEventListener("koto:open-settings", () => {
    if (loadSession()?.accessToken) void reload();
  });

  // Также при первом запуске, если уже залогинен.
  if (loadSession()?.accessToken) void reload();
}
