/**
 * Раскрывающаяся панель профиля: локальный статус + строка про E2EE; режим фокуса — в настройках.
 */
import { loadSession } from "@/shared/state/sessionStore";

const LS_PRESENCE = "koto.desktop.localPresence";
const LS_FOCUS = "koto.desktop.focusMode";

type LocalPresenceMode = "online" | "away" | "dnd" | "invisible";

function readPresence(): LocalPresenceMode {
  try {
    const v = localStorage.getItem(LS_PRESENCE);
    if (v === "away" || v === "dnd" || v === "invisible" || v === "online") return v;
  } catch {
    /* ignore */
  }
  return "online";
}

function writePresence(m: LocalPresenceMode): void {
  try {
    localStorage.setItem(LS_PRESENCE, m);
  } catch {
    /* ignore */
  }
}

function readFocus(): boolean {
  try {
    return localStorage.getItem(LS_FOCUS) === "1";
  } catch {
    return false;
  }
}

function writeFocus(on: boolean): void {
  try {
    localStorage.setItem(LS_FOCUS, on ? "1" : "0");
  } catch {
    /* ignore */
  }
}

function $(id: string): HTMLElement | null {
  return document.getElementById(id);
}

/** Подпись «Режим фокуса» в списке настроек (без дубля в drawer). */
export function syncFocusSettingsDetail(): void {
  const el = $("settings-focus-detail");
  if (!el) return;
  const guest = !loadSession()?.accessToken;
  if (guest) {
    el.textContent = "Нужен вход";
    return;
  }
  el.textContent = readFocus() ? "Вкл." : "Выкл.";
}

/** Переключить режим фокуса (настройки). */
export function toggleDesktopFocusMode(): void {
  if (!loadSession()?.accessToken) return;
  writeFocus(!readFocus());
  syncUserbarDrawerUi();
  syncFocusSettingsDetail();
  window.dispatchEvent(new CustomEvent("koto:focus-mode-changed", { detail: { on: readFocus() } }));
}

/** Обновить UI панели (пилюли, фокус, кастомный статус) — вызывать после смены сессии. */
export function syncUserbarDrawerUi(): void {
  const bar = $("chatlist-userbar");
  const guest = !loadSession()?.accessToken;
  if (bar) {
    bar.dataset.localPresence = readPresence();
    bar.toggleAttribute("data-focus-mode", readFocus());
    bar.classList.toggle("chatlist-userbar--focus", readFocus() && !guest);
  }

  document.querySelectorAll<HTMLButtonElement>(".chatlist-userbar__pill[data-presence]").forEach((btn) => {
    const m = btn.dataset.presence as LocalPresenceMode | undefined;
    const on = m === readPresence();
    btn.classList.toggle("chatlist-userbar__pill--on", on);
    btn.setAttribute("aria-pressed", on ? "true" : "false");
    btn.disabled = guest;
  });

  const deck = $("userbar-deck-line");
  if (deck) {
    if (guest) {
      deck.textContent = "Войдите, чтобы пользоваться аккаунтом.";
    } else if (readFocus()) {
      deck.textContent = "Фокус: меньше отвлечений в интерфейсе.";
    } else {
      const pm = readPresence();
      const map: Record<LocalPresenceMode, string> = {
        online: "Сообщения шифруются на устройстве (E2EE).",
        away: "Пресет «Отошёл» — только в этом клиенте.",
        dnd: "Пресет «Не беспокоить» — только в этом клиенте.",
        invisible: "Пресет «Невидимый» — только в этом клиенте.",
      };
      deck.textContent = map[pm];
    }
  }

  syncFocusSettingsDetail();
}

export function initUserbarDrawerEnhancements(): void {
  if (document.body.dataset.kotoUserbarEnhance === "1") return;
  document.body.dataset.kotoUserbarEnhance = "1";

  document.querySelectorAll(".chatlist-userbar__pill[data-presence]").forEach((el) => {
    el.addEventListener("click", () => {
      const m = (el as HTMLButtonElement).dataset.presence as LocalPresenceMode | undefined;
      if (!m || (el as HTMLButtonElement).disabled) return;
      writePresence(m);
      syncUserbarDrawerUi();
      window.dispatchEvent(new CustomEvent("koto:userbar-local-presence", { detail: { mode: m } }));
    });
  });

  window.addEventListener("koto:session-changed", () => syncUserbarDrawerUi());

  syncUserbarDrawerUi();
}
