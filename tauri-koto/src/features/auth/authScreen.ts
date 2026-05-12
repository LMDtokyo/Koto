import { authProvisionFromSeed } from "@/shared/services/authService";
import {
  saveSession,
  saveSecrets,
  hasSession,
  pickRegistrationId,
} from "@/shared/state/sessionStore";
import { initCryptoSession } from "@/shared/services/cryptoSession";
import { refreshChatList } from "@/features/chat/chatList";
import { closeThread } from "@/features/chat/chatThread";
import { closeRegisterFlow, initRegisterFlow, openRegisterFlow } from "@/features/auth/registerFlow";
import { syncSessionChrome } from "@/features/sidebar/sidebarConnectivity";

function showAuthLayer(visible: boolean): void {
  const layer = document.getElementById("auth-layer");
  if (!layer) return;
  if (visible) layer.removeAttribute("hidden");
  else layer.setAttribute("hidden", "");
}

function setAuthView(which: "welcome" | "restore" | "register"): void {
  const welcome = document.getElementById("auth-view-welcome");
  const restore = document.getElementById("auth-view-restore");
  const register = document.getElementById("auth-view-register");
  if (!welcome || !restore) return;
  welcome.classList.toggle("auth-view--active", which === "welcome");
  restore.classList.toggle("auth-view--active", which === "restore");
  register?.classList.toggle("auth-view--active", which === "register");
}

function setStatus(msg: string): void {
  const text = msg ?? "";
  document.getElementById("auth-status")?.replaceChildren(text);
  document.getElementById("auth-welcome-status")?.replaceChildren(text);
}

function parseSeed(text: string): string[] {
  return text
    .trim()
    .split(/\s+/)
    .map((w) => w.trim().toLowerCase())
    .filter(Boolean);
}

async function enterApp(tokens: Record<string, unknown>, seedPhrase?: string[]): Promise<void> {
  closeThread();
  saveSession(tokens);
  const regId = pickRegistrationId(tokens);
  if (seedPhrase?.length && regId) {
    saveSecrets({ seedPhrase, registrationId: regId });
  }
  try {
    await initCryptoSession();
  } catch (e) {
    console.warn("crypto init failed:", e);
  }
  syncSessionChrome();
  showAuthLayer(false);
  setAuthView("welcome");
  closeRegisterFlow();
  setStatus("");
  await refreshChatList();
  window.dispatchEvent(new CustomEvent("koto:session-changed"));
}

export async function initAuthScreen(): Promise<void> {
  initRegisterFlow({
    setActiveView: setAuthView,
    finishLogin: (tokens, seedPhrase) => enterApp(tokens, seedPhrase),
  });

  window.addEventListener("koto:signed-out", () => {
    closeThread();
    const seed = document.getElementById("auth-seed") as HTMLTextAreaElement | null;
    if (seed) seed.value = "";
    closeRegisterFlow();
    setAuthView("welcome");
    setStatus("");
    window.dispatchEvent(new CustomEvent("koto:session-changed"));
  });

  if (hasSession()) {
    showAuthLayer(false);
    try {
      await initCryptoSession();
    } catch (e) {
      console.warn("crypto bootstrap failed:", e);
    }
    await refreshChatList();
    window.dispatchEvent(new CustomEvent("koto:session-changed"));
    return;
  }

  showAuthLayer(true);
  setAuthView("welcome");

  document.getElementById("auth-btn-have-account")?.addEventListener("click", () => {
    setAuthView("restore");
    setStatus("");
  });

  document.getElementById("auth-restore-back")?.addEventListener("click", () => {
    setAuthView("welcome");
    setStatus("");
  });

  document.getElementById("auth-btn-create")?.addEventListener("click", () => {
    void openRegisterFlow();
  });

  document.getElementById("auth-restore-submit")?.addEventListener("click", async () => {
    const seedEl = document.getElementById("auth-seed") as HTMLTextAreaElement | null;
    const words = parseSeed(seedEl?.value ?? "");
    if (words.length < 12) {
      setStatus("Введите не менее 12 слов.");
      return;
    }
    setStatus("Вход…");
    try {
      const tokens = await authProvisionFromSeed(words, true);
      await enterApp(tokens, words);
    } catch (e) {
      setStatus(String(e));
    }
  });

  document.getElementById("auth-register-seed")?.addEventListener("click", async () => {
    const seedEl = document.getElementById("auth-seed") as HTMLTextAreaElement | null;
    const words = parseSeed(seedEl?.value ?? "");
    if (words.length < 12) {
      setStatus("Введите не менее 12 слов.");
      return;
    }
    setStatus("Регистрация…");
    try {
      const tokens = await authProvisionFromSeed(words, false);
      await enterApp(tokens, words);
    } catch (e) {
      setStatus(String(e));
    }
  });
}
