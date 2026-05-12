import { listen, type UnlistenFn } from "@tauri-apps/api/event";
import { invoke } from "@tauri-apps/api/core";
import { loadSession, saveSession } from "@/shared/state/sessionStore";
import { formatInvokeError, parseKotoApiError } from "@/shared/services/invokeError";

let unlistenReauth: UnlistenFn | null = null;
let unlistenFrame: UnlistenFn | null = null;
let unlistenStatus: UnlistenFn | null = null;

async function unlistenSafe(u: UnlistenFn | null): Promise<void> {
  if (u) {
    try {
      await u();
    } catch {
      /* ignore */
    }
  }
}

/** Подписка на события из Rust WebSocket-цикла (идемпотентно). */
export async function initKotoWsListeners(): Promise<void> {
  if (!window.__TAURI__) return;
  await unlistenSafe(unlistenReauth);
  await unlistenSafe(unlistenFrame);
  await unlistenSafe(unlistenStatus);
  unlistenReauth = null;
  unlistenFrame = null;
  unlistenStatus = null;

  unlistenReauth = await listen("koto-ws-reauth", async () => {
    const s = loadSession();
    if (!s?.refreshToken) return;
    try {
      const t = (await invoke("auth_refresh_tokens", {
        refreshToken: s.refreshToken,
      })) as Record<string, unknown>;
      saveSession(t);
      const accessToken = String(t.accessToken ?? t.access_token ?? "");
      await invoke("koto_ws_ack_token", { accessToken });
    } catch (e) {
      console.warn("koto-ws-reauth:", parseKotoApiError(e));
    }
  });

  unlistenFrame = await listen("koto-ws-frame", (ev) => {
    window.dispatchEvent(new CustomEvent("koto:ws-frame", { detail: ev.payload }));
  });

  unlistenStatus = await listen("koto-ws-status", (ev) => {
    window.dispatchEvent(new CustomEvent("koto:ws-status", { detail: ev.payload }));
  });
}

export async function startKotoWsIfSession(): Promise<void> {
  if (!window.__TAURI__) return;
  const s = loadSession();
  if (!s?.accessToken) return;
  try {
    await invoke("koto_ws_start", { accessToken: s.accessToken });
  } catch (e) {
    console.warn("koto_ws_start:", formatInvokeError(e));
  }
}

export async function stopKotoWs(): Promise<void> {
  if (!window.__TAURI__) return;
  try {
    await invoke("koto_ws_stop");
  } catch (e) {
    console.warn("koto_ws_stop:", formatInvokeError(e));
  }
}
