import { mainNav, Screen } from "@/shared/state/navStore";
import { dismissAllOverlays, isAnyOverlayOpen } from "@/features/shell/overlaysLayer";

function authVisible(): boolean {
  const auth = document.getElementById("auth-layer");
  return Boolean(auth && !auth.hasAttribute("hidden"));
}

function isEditableTarget(t: EventTarget | null): boolean {
  const el = t as HTMLElement | null;
  if (!el) return false;
  const tag = el.tagName;
  if (tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT") return true;
  if (el.isContentEditable) return true;
  return Boolean(el.closest("[contenteditable='true']"));
}

export function initGlobalShortcuts(): void {
  if (document.body.dataset.kotoGlobalKeys === "1") return;
  document.body.dataset.kotoGlobalKeys = "1";

  document.getElementById("overlay-call-end")?.addEventListener("click", () => {
    mainNav.pop();
  });

  document.addEventListener("keydown", (e) => {
    if (authVisible()) return;

    if (e.key === "Escape") {
      if (isAnyOverlayOpen()) {
        e.preventDefault();
        dismissAllOverlays();
        return;
      }
      const call = document.getElementById("overlay-call");
      if (call && !call.hasAttribute("hidden")) {
        e.preventDefault();
        mainNav.pop();
        return;
      }
      if (mainNav.depth > 1) {
        e.preventDefault();
        mainNav.pop();
        return;
      }
      if (mainNav.current.type === "NewChat") {
        e.preventDefault();
        mainNav.resetTo(Screen.empty());
      }
      return;
    }

    if (isEditableTarget(e.target)) return;

    const mod = e.ctrlKey || e.metaKey;
    if (!mod) return;

    const k = e.key.toLowerCase();
    if (k === "k" || k === "n") {
      e.preventDefault();
      mainNav.resetTo(Screen.newChat());
      (document.getElementById("new-chat-peer-id") as HTMLInputElement | null)?.focus();
      return;
    }
    if (e.key === ",") {
      e.preventDefault();
      window.dispatchEvent(new CustomEvent("koto:open-settings"));
    }
  });
}
