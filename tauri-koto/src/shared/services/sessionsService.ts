/**
 * Список сеансов и завершение чужих устройств. Текущий сеанс защищён в UI:
 * мы знаем свой `sessionId` из локального стора и никогда не вызываем revoke
 * для него. Сервер тоже не запрещает явно revoke собственного — поэтому защита
 * лежит на клиенте, а не где-то в auth-сервисе.
 */

import { loadSession } from "@/shared/state/sessionStore";

const invoke = window.__TAURI__?.core?.invoke?.bind(window.__TAURI__.core);

function requireInvoke(): NonNullable<typeof invoke> {
  if (!invoke) throw new Error("Tauri runtime недоступен");
  return invoke;
}

function requireToken(): string {
  const t = loadSession()?.accessToken;
  if (!t) throw new Error("Нет активной сессии");
  return t;
}

export interface SessionRow {
  id: string;
  device_name: string;
  platform: string;
  app_version: string;
  client_ip: string;
  created_at: string;
  last_seen_at: string;
}

export async function listSessions(): Promise<SessionRow[]> {
  return requireInvoke()<SessionRow[]>("auth_list_sessions", { accessToken: requireToken() });
}

export async function revokeSession(sessionId: string): Promise<void> {
  await requireInvoke()<void>("auth_revoke_session", {
    accessToken: requireToken(),
    sessionId,
  });
}

/** Завершить все сеансы, кроме текущего (или указанного). */
export async function revokeOtherSessions(keepId: string): Promise<{ ok: number; failed: number }> {
  const sessions = await listSessions();
  let ok = 0;
  let failed = 0;
  for (const s of sessions) {
    if (s.id === keepId) continue;
    try {
      await revokeSession(s.id);
      ok += 1;
    } catch {
      failed += 1;
    }
  }
  return { ok, failed };
}
