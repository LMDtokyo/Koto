/**
 * Управление окном — Tauri v2 + @tauri-apps/api.
 * https://v2.tauri.app/learn/window-customization/
 */

import { getCurrentWindow } from "@tauri-apps/api/window";

function getWin() {
  try {
    return getCurrentWindow();
  } catch {
    return null;
  }
}

function isChromeDragBlockedTarget(target: EventTarget | null): boolean {
  if (!(target instanceof Element)) return true;
  if (target.closest(".chrome__controls")) return true;
  if (target.closest("button, a, input, textarea, select, label")) return true;
  return false;
}

/** Перетаскивание окна + двойной клик → развернуть (как в доке Tauri). */
function initChromeDrag(chromeBar: HTMLElement | null): void {
  if (!chromeBar) return;

  chromeBar.addEventListener("mousedown", (e) => {
    if (e.button !== 0) return;
    if (isChromeDragBlockedTarget(e.target)) return;

    const w = getWin();
    if (!w) return;

    void (async () => {
      try {
        if (e.detail === 2) {
          await w.toggleMaximize();
        } else {
          await w.startDragging();
        }
      } catch {
        /* вне Tauri / запрет capability */
      }
    })();
  });
}

let windowControlsAttached = false;

function attachWindowAction(el: HTMLElement | null, action: () => void | Promise<void>): void {
  if (!el) return;
  const onPrimaryDown = (e: MouseEvent) => {
    if (e.button !== 0) return;
    /* Tauri/WebView часто «съедает» первый click у title bar; capture + stop — до drag-region. */
    e.preventDefault();
    e.stopPropagation();
    void action();
  };
  el.addEventListener("mousedown", onPrimaryDown, { capture: true });
}

export function initWindowControls(): void {
  if (windowControlsAttached) return;
  windowControlsAttached = true;

  const win = getWin();
  const chromeBar = document.getElementById("chrome-bar");
  initChromeDrag(chromeBar);

  const maxBtn = document.getElementById("win-maximize");
  const fsBtn = document.getElementById("win-fullscreen");
  const minBtn = document.getElementById("win-minimize");
  const closeBtn = document.getElementById("win-close");

  attachWindowAction(maxBtn, async () => {
    try {
      await win?.toggleMaximize?.();
    } catch {
      /* ignore */
    }
  });

  attachWindowAction(fsBtn, async () => {
    try {
      const w = win;
      if (!w?.isFullscreen || !w.setFullscreen) return;
      const on = await w.isFullscreen();
      await w.setFullscreen(!on);
    } catch {
      /* ignore */
    }
  });

  attachWindowAction(minBtn, async () => {
    try {
      await win?.minimize?.();
    } catch {
      /* ignore */
    }
  });

  attachWindowAction(closeBtn, async () => {
    try {
      await win?.close?.();
    } catch {
      /* ignore */
    }
  });
}
