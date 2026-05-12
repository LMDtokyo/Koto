const SESSION_KEY = "koto.session";
const SECRETS_KEY = "koto.identity";

export interface SessionTokens {
  accountId: string;
  sessionId: string;
  accessToken: string;
  refreshToken: string;
  expiresAt: number;
}

/** Локальные крипто-материалы, нужные для инициализации сессии Signal Protocol
 * при холодном старте приложения. Хранятся ОТДЕЛЬНО от tokens, потому что
 * tokens обновляются часто (refresh), а identity — раз при register/restore. */
export interface SessionSecrets {
  /** BIP39 seed-фраза. Sensitive — для альфа-MVP в plain localStorage. */
  seedPhrase: string[];
  /** Идентификатор регистрации, который сервер сохранил в БД. */
  registrationId: number;
}

/** Нормализация DTO из Rust (`camelCase`) и возможных snake_case. */
export function normalizeTokenPair(t: unknown): SessionTokens | null {
  if (!t || typeof t !== "object") return null;
  const o = t as Record<string, unknown>;
  return {
    accountId: String(o.accountId ?? o.account_id ?? ""),
    sessionId: String(o.sessionId ?? o.session_id ?? ""),
    accessToken: String(o.accessToken ?? o.access_token ?? ""),
    refreshToken: String(o.refreshToken ?? o.refresh_token ?? ""),
    expiresAt: Number(o.expiresAt ?? o.expires_at ?? 0),
  };
}

/** Извлекает `registration_id` из ответа Rust (camelCase / snake_case). */
export function pickRegistrationId(t: unknown): number {
  if (!t || typeof t !== "object") return 0;
  const o = t as Record<string, unknown>;
  return Number(o.registrationId ?? o.registration_id ?? 0);
}

export function loadSession(): SessionTokens | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY);
    if (raw) {
      return normalizeTokenPair(JSON.parse(raw) as unknown);
    }
    const access = localStorage.getItem("koto.accessToken");
    if (!access) return null;
    return {
      accountId: "",
      sessionId: "",
      accessToken: access,
      refreshToken: localStorage.getItem("koto.refreshToken") ?? "",
      expiresAt: 0,
    };
  } catch {
    return null;
  }
}

export function saveSession(tokens: SessionTokens | Record<string, unknown>): void {
  const n = normalizeTokenPair(tokens);
  if (!n) return;
  localStorage.setItem(SESSION_KEY, JSON.stringify(n));
  if (n.accessToken) localStorage.setItem("koto.accessToken", n.accessToken);
  if (n.refreshToken) localStorage.setItem("koto.refreshToken", n.refreshToken);
}

export function hasSession(): boolean {
  const s = loadSession();
  return Boolean(s?.accessToken);
}

export function clearSession(): void {
  localStorage.removeItem(SESSION_KEY);
  localStorage.removeItem("koto.accessToken");
  localStorage.removeItem("koto.refreshToken");
  localStorage.removeItem(SECRETS_KEY);
}

export function loadSecrets(): SessionSecrets | null {
  try {
    const raw = localStorage.getItem(SECRETS_KEY);
    if (!raw) return null;
    const o = JSON.parse(raw) as { seedPhrase?: unknown; registrationId?: unknown };
    const seedPhrase = Array.isArray(o.seedPhrase)
      ? (o.seedPhrase as unknown[]).map((w) => String(w).trim()).filter(Boolean)
      : [];
    const registrationId = Number(o.registrationId ?? 0);
    if (seedPhrase.length < 12 || !registrationId) return null;
    return { seedPhrase, registrationId };
  } catch {
    return null;
  }
}

export function saveSecrets(s: SessionSecrets): void {
  if (!s.seedPhrase?.length || !s.registrationId) return;
  try {
    localStorage.setItem(
      SECRETS_KEY,
      JSON.stringify({ seedPhrase: s.seedPhrase, registrationId: s.registrationId })
    );
  } catch {
    /* приватный режим */
  }
}

export function clearSecrets(): void {
  try {
    localStorage.removeItem(SECRETS_KEY);
  } catch {
    /* ignore */
  }
}
