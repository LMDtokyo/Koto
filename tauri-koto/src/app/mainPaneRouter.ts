import { mainNav, screenEquals, type AppScreen } from "@/shared/state/navStore";
import { activateChat, deactivateChat } from "@/features/chat/chatThread";
import { refreshSettingsPane, applySettingsSubSection } from "@/features/settings/settingsPane";
import { syncNewChatPaneVisibility } from "@/features/chat/newChatPane";

const PANE_IDS: Record<AppScreen["type"], string> = {
  Empty: "pane-empty",
  Chat: "pane-chat",
  Friends: "pane-friends",
  Settings: "pane-settings-sub",
  SettingsSub: "pane-settings-sub",
  NewChat: "pane-new-chat",
  NewGroup: "pane-new-group",
  Contact: "pane-contact",
  Stories: "pane-stories",
  Safety: "pane-safety",
  SafetyDetail: "pane-safety-detail",
  Bots: "pane-bots",
  BotForge: "pane-bot-forge",
  Archive: "pane-archive",
  Call: "pane-empty",
};

function paneIdFor(s: AppScreen): string {
  return PANE_IDS[s.type] ?? "pane-empty";
}

function isAuthBlocking(): boolean {
  const auth = document.getElementById("auth-layer");
  return Boolean(auth && !auth.hasAttribute("hidden"));
}

type CallScreen = Extract<AppScreen, { type: "Call" }>;

export function isCallScreen(s: AppScreen): s is CallScreen {
  return s.type === "Call";
}

function setCallOverlay(call: CallScreen | null): void {
  const ov = document.getElementById("overlay-call");
  const app = document.getElementById("app");
  if (!ov) return;
  if (!call) {
    ov.setAttribute("hidden", "");
    app?.removeAttribute("hidden");
    return;
  }
  const title = ov.querySelector(".call-overlay__title");
  const sub = ov.querySelector(".call-overlay__sub");
  if (title) title.textContent = call.video ? "Видеозвонок" : "Звонок";
  if (sub) sub.textContent = call.peerId;
  ov.removeAttribute("hidden");
  app?.setAttribute("hidden", "");
}

function runPaneTransition(
  host: HTMLElement,
  activeId: string,
  transition: "PUSH" | "POP" | "NONE"
): void {
  const kind = transition === "PUSH" ? "push" : transition === "POP" ? "pop" : "none";
  host.dataset.navTransition = kind;
  const el = document.getElementById(activeId);
  if (!el) return;
  el.classList.remove("main-pane--enter");
  void el.offsetWidth;
  if (kind !== "none") {
    el.classList.add("main-pane--enter");
    window.setTimeout(() => {
      el.classList.remove("main-pane--enter");
      host.dataset.navTransition = "none";
    }, 280);
  } else {
    host.dataset.navTransition = "none";
  }
}

let lastScreen: AppScreen = mainNav.current;

function sync(): void {
  if (isAuthBlocking()) {
    setCallOverlay(null);
    return;
  }

  const s = mainNav.current;
  const host = document.getElementById("main-pane-host");
  if (!host) return;

  if (isCallScreen(s)) {
    setCallOverlay(s);
    lastScreen = s;
    return;
  }
  setCallOverlay(null);

  const prev = lastScreen;
  lastScreen = s;

  if (prev.type === "Chat" && s.type !== "Chat") {
    deactivateChat();
  }

  const activeId = paneIdFor(s);
  const panes = host.querySelectorAll(".main-pane");
  panes.forEach((p) => {
    if (p.id === activeId) p.removeAttribute("hidden");
    else p.setAttribute("hidden", "");
  });

  const tr = mainNav.lastTransition;
  if (!screenEquals(prev, s)) {
    runPaneTransition(host, activeId, tr);
  }

  if (s.type === "Chat") {
    void activateChat(s.convId);
  }
  if (s.type === "Settings" || s.type === "SettingsSub") {
    void refreshSettingsPane().catch(console.error);
  }
  if (s.type === "SettingsSub") {
    applySettingsSubSection(s.section);
  } else if (s.type === "Settings") {
    applySettingsSubSection("profile");
  }
  if (s.type === "Contact") {
    const idEl = document.getElementById("contact-pane-id");
    const sub = document.getElementById("contact-pane-sub");
    if (sub) sub.textContent = "Идентификатор";
    if (idEl) idEl.textContent = s.id;
  }
  syncNewChatPaneVisibility(s);
}

export function initMainPaneRouter(): void {
  mainNav.subscribe(sync);
  window.addEventListener("koto:session-changed", () => sync());
  sync();
}
