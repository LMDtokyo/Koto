/**
 * In-app уведомления о новых сообщениях.
 *
 * Без Tauri-plugin-notification (на MVP) — используем Web Notification API
 * + title-badge + короткий beep. Уважаем «Не беспокоить» (LS_FOCUS) из настроек.
 *
 * Скоро: миграция на `@tauri-apps/plugin-notification` + system tray icon
 * с badge — даст нативные toast'ы и unread-точку на иконке окна.
 */

import { loadSession } from "@/shared/state/sessionStore";
import { mainNav } from "@/shared/state/navStore";
import { getPlaintext } from "@/features/chat/messageCache";

const LS_FOCUS = "koto.desktop.focusMode";
const ORIGINAL_TITLE = "Koto";
let unreadTotal = 0;
let permissionAsked = false;
let beepCtx: AudioContext | null = null;

function isMuted(): boolean {
  try {
    return localStorage.getItem(LS_FOCUS) === "1";
  } catch {
    return false;
  }
}

function activeConvId(): string | null {
  const s = mainNav.current;
  return s.type === "Chat" ? s.convId : null;
}

function refreshTitle(): void {
  if (!unreadTotal) {
    document.title = ORIGINAL_TITLE;
    return;
  }
  document.title = `(${unreadTotal > 99 ? "99+" : unreadTotal}) ${ORIGINAL_TITLE}`;
}

function clearBadge(): void {
  unreadTotal = 0;
  refreshTitle();
}

async function ensurePermission(): Promise<NotificationPermission | "denied"> {
  if (typeof Notification === "undefined") return "denied";
  if (Notification.permission !== "default") return Notification.permission;
  if (permissionAsked) return "default";
  permissionAsked = true;
  try {
    return await Notification.requestPermission();
  } catch {
    return "denied";
  }
}

function playBeep(): void {
  if (isMuted()) return;
  try {
    if (!beepCtx) beepCtx = new (window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext)();
    const ctx = beepCtx!;
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.frequency.setValueAtTime(880, ctx.currentTime);
    osc.frequency.exponentialRampToValueAtTime(660, ctx.currentTime + 0.12);
    gain.gain.setValueAtTime(0.0001, ctx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.18, ctx.currentTime + 0.02);
    gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + 0.18);
    osc.connect(gain).connect(ctx.destination);
    osc.start();
    osc.stop(ctx.currentTime + 0.2);
  } catch {
    /* ignore */
  }
}

function shouldNotify(senderId: string, conversationId: string): boolean {
  const selfId = (loadSession()?.accountId || "").trim();
  if (!selfId || senderId === selfId) return false;
  if (isMuted()) return false;
  // Если окно сфокусировано И активный чат совпадает — не дёргаем юзера.
  if (typeof document !== "undefined" && document.hasFocus() && activeConvId() === conversationId) {
    return false;
  }
  return true;
}

async function showNotification(title: string, body: string, conversationId: string): Promise<void> {
  const perm = await ensurePermission();
  if (perm !== "granted") return;
  try {
    const n = new Notification(title, { body, silent: true, tag: conversationId });
    n.onclick = () => {
      window.focus();
      n.close();
      // Открыть нужный чат
      // (при желании — добавить mainNav.resetTo(Screen.chat(conversationId)))
    };
  } catch {
    /* ignore */
  }
}

export function initNotifications(): void {
  if (document.body.dataset.kotoNotifications === "1") return;
  document.body.dataset.kotoNotifications = "1";

  window.addEventListener("focus", clearBadge);
  window.addEventListener("koto:nav-active-chat", clearBadge);
  document.addEventListener("visibilitychange", () => {
    if (!document.hidden) clearBadge();
  });

  window.addEventListener("koto:ws:new-message", (ev) => {
    const frame = (ev as CustomEvent<{
      conversation_id: string;
      message_id: string;
      sender_id: string;
    }>).detail;
    if (!frame) return;
    if (!shouldNotify(frame.sender_id, frame.conversation_id)) return;

    unreadTotal += 1;
    refreshTitle();
    playBeep();

    const preview = getPlaintext(frame.message_id) || "Новое сообщение";
    const truncated = preview.length > 80 ? `${preview.slice(0, 80)}…` : preview;
    const title = `Сообщение от ${frame.sender_id.slice(0, 8)}…`;
    void showNotification(title, truncated, frame.conversation_id);
  });

  window.addEventListener("koto:signed-out", clearBadge);

  // Запрашиваем permission при первом проявленном интересе пользователя (login).
  window.addEventListener(
    "koto:session-changed",
    () => {
      if (loadSession()?.accessToken) void ensurePermission();
    },
    { once: false }
  );
}
