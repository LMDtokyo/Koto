/**
 * Управление правой выезжающей панелью деталей чата.
 *
 * - Открывается по клику на peer-area в `thread-header`.
 * - Закрывается X / Esc / повторным кликом по peer-header / выходом из чата.
 * - Пишет в DOM-узлы panel'а: avatar (с цветом по hue), имя, hex-id, bio.
 *
 * Источники данных синхронизируются через стандартные события
 * `koto:chat-active-changed` (новое — мы их эмитим из chatThread при activate).
 */

import { mainNav } from "@/shared/state/navStore";

function $(id: string): HTMLElement | null {
  return document.getElementById(id);
}

function isOpen(): boolean {
  const el = $("chat-right-panel");
  return Boolean(el && el.dataset.open === "true");
}

function setOpen(open: boolean): void {
  const el = $("chat-right-panel");
  if (!el) return;
  if (open) {
    el.removeAttribute("hidden");
    requestAnimationFrame(() => {
      el.dataset.open = "true";
      el.setAttribute("aria-hidden", "false");
    });
  } else {
    el.dataset.open = "false";
    el.setAttribute("aria-hidden", "true");
    setTimeout(() => {
      // Скрываем после анимации, чтобы pointer-events не ловились.
      if (el.dataset.open === "false") el.setAttribute("hidden", "");
    }, 280);
  }
}

interface PeerSnapshot {
  title: string;
  peerId: string;
  hue: number;
  initials: string;
  bio?: string;
}

function snapshotPeerFromThreadHeader(): PeerSnapshot | null {
  const titleEl = $("thread-title");
  const subtitleEl = $("thread-subtitle");
  const avatarEl = $("thread-avatar");
  const wrapEl = $("thread-avatar-wrap");
  if (!titleEl?.textContent) return null;
  const title = titleEl.textContent.trim();
  // peer_id хранится в dataset на conversation row, оттуда мы и взяли при открытии чата;
  // на header мы его не пишем — берём из mainNav state как convId как fallback.
  const peerId =
    avatarEl?.dataset?.peerId ||
    wrapEl?.dataset?.peerId ||
    (subtitleEl?.dataset?.peerId ?? "");
  // hue из inline-style avatar-а (`--av-bg: hsl(N 46% 42%)`) — простой regex
  let hue = 210;
  const styleAttr = (avatarEl?.getAttribute("style") || "") + (wrapEl?.getAttribute("style") || "");
  const match = styleAttr.match(/hsl\((\d{1,3})\s/);
  if (match) hue = Number(match[1]);
  const initials = (avatarEl?.textContent || title.slice(0, 2)).trim().toUpperCase().slice(0, 2);
  return { title, peerId, hue, initials };
}

function applyPeer(snap: PeerSnapshot): void {
  const avatar = $("chat-right-panel-avatar");
  const name = $("chat-right-panel-name");
  const handle = $("chat-right-panel-handle");
  const bio = $("chat-right-panel-bio");
  if (avatar) {
    avatar.textContent = snap.initials;
    avatar.style.setProperty("--av-bg", `hsl(${snap.hue} 46% 42%)`);
  }
  if (name) name.textContent = snap.title;
  if (handle) {
    if (snap.peerId) {
      const id = snap.peerId;
      handle.textContent = id.length > 32 ? `${id.slice(0, 12)}…${id.slice(-8)}` : id;
    } else {
      handle.textContent = "";
    }
  }
  if (bio) {
    if (snap.bio?.trim()) {
      bio.textContent = snap.bio;
      bio.removeAttribute("hidden");
    } else {
      bio.setAttribute("hidden", "");
    }
  }
}

export function initChatRightPanel(): void {
  if (document.body.dataset.kotoChatRightPanel === "1") return;
  document.body.dataset.kotoChatRightPanel = "1";

  const peerHit = $("thread-header-peer");
  peerHit?.addEventListener("click", () => {
    if (isOpen()) {
      setOpen(false);
      return;
    }
    const snap = snapshotPeerFromThreadHeader();
    if (snap) applyPeer(snap);
    setOpen(true);
  });

  $("chat-right-panel-close")?.addEventListener("click", () => setOpen(false));

  window.addEventListener("keydown", (ev) => {
    if (ev.key === "Escape" && isOpen()) {
      ev.stopPropagation();
      setOpen(false);
    }
  });

  // При выходе из чата — закрываем панель.
  mainNav.subscribe(() => {
    if (mainNav.current.type !== "Chat" && isOpen()) setOpen(false);
  });

  // Tab-переключатели секции медиа: пока что просто toggle active-класса.
  document
    .querySelectorAll<HTMLButtonElement>(".chat-right-panel__tab[data-media-tab]")
    .forEach((btn) => {
      btn.addEventListener("click", () => {
        document
          .querySelectorAll(".chat-right-panel__tab")
          .forEach((b) => b.classList.remove("chat-right-panel__tab--active"));
        btn.classList.add("chat-right-panel__tab--active");
      });
    });

  // Заглушки для action-row: TODO в следующих итерациях
  $("chat-right-panel-mute")?.addEventListener("click", () => {
    const row = $("chat-right-panel-mute");
    if (!row) return;
    const muted = row.dataset.muted !== "true";
    row.dataset.muted = muted ? "true" : "false";
    const detail = $("chat-right-panel-mute-state");
    if (detail) detail.textContent = muted ? "Выключены" : "Включены";
  });

  $("chat-right-panel-search")?.addEventListener("click", () => {
    setOpen(false);
    document.getElementById("thread-header-search")?.click();
  });

  $("chat-right-panel-block")?.addEventListener("click", () => {
    if (!confirm("Заблокировать этого пользователя?")) return;
    // TODO: вызвать user_contact_block через authService
    setOpen(false);
  });

  $("chat-right-panel-clear")?.addEventListener("click", () => {
    if (!confirm("Очистить переписку с этим контактом? Действие нельзя отменить.")) return;
    // TODO: на бэке нет batch-delete; реализуется через цикл DELETE по сообщениям.
  });

  $("chat-right-panel-disappearing")?.addEventListener("click", () => {
    setOpen(false);
    window.dispatchEvent(
      new CustomEvent("koto:open-settings", { detail: { section: "ephemeral" } })
    );
  });
}
