/**
 * E2E крипто-сессия Signal Protocol для Tauri-клиента.
 *
 * Стейт сессии (KotoCrypto) живёт в Rust как `tauri::State`. На фронте мы
 * только дёргаем команды и кешируем `Set<peerId>`, для которых уже выполнили
 * X3DH+Kyber handshake (чтобы не запрашивать prekey-bundle при каждой
 * отправке).
 */

import { loadSecrets, loadSession, saveSession, type SessionSecrets } from "@/shared/state/sessionStore";

const invoke = window.__TAURI__?.core?.invoke?.bind(window.__TAURI__.core);

function requireInvoke(): NonNullable<typeof invoke> {
  if (!invoke) throw new Error("Tauri runtime недоступен");
  return invoke;
}

/** Peers, для которых сессия уже инициализирована — кэш в памяти на текущий запуск. */
const initializedPeers = new Set<string>();

let cryptoReady = false;

/** Инициализация криптосессии (вызывать после login/restore или при старте приложения). */
export async function initCryptoSession(secrets?: SessionSecrets): Promise<boolean> {
  const session = loadSession();
  const sec = secrets || loadSecrets();
  if (!session?.accountId || !sec?.seedPhrase?.length || !sec.registrationId) {
    cryptoReady = false;
    return false;
  }
  // Перед публикацией bundle на сервер обновляем access-token: он живёт 15 мин,
  // и при «холодном» рестарте Tauri/после простоя в localStorage обычно лежит
  // протухший. Без этого PUT /v1/keys = 401 → server-bundle не обновляется →
  // recipient не может расшифровать (его local store расходится с server).
  let accessToken = session.accessToken;
  if (session.refreshToken) {
    try {
      const refreshed = (await requireInvoke()("auth_refresh_tokens", {
        refreshToken: session.refreshToken,
      })) as Record<string, unknown>;
      saveSession(refreshed);
      const fresh = (refreshed.access_token ?? refreshed.accessToken) as string | undefined;
      if (fresh) accessToken = fresh;
    } catch {
      /* keep existing token; upload может не удаться, fallback handled in Rust */
    }
  }
  await requireInvoke()<void>("crypto_init_session", {
    seedPhrase: sec.seedPhrase,
    registrationId: sec.registrationId,
    accountId: session.accountId,
    accessToken: accessToken || null,
  });
  cryptoReady = true;
  initializedPeers.clear();
  return true;
}

export async function clearCryptoSession(): Promise<void> {
  cryptoReady = false;
  initializedPeers.clear();
  try {
    await requireInvoke()<void>("crypto_clear_session");
  } catch {
    /* runtime may already be down */
  }
}

export function isCryptoReady(): boolean {
  return cryptoReady;
}

/** Проверка через Rust (на случай рассинхрона). */
export async function probeCryptoReady(): Promise<boolean> {
  try {
    cryptoReady = await requireInvoke()<boolean>("crypto_is_ready");
  } catch {
    cryptoReady = false;
  }
  return cryptoReady;
}

async function ensurePeerSession(peerId: string): Promise<void> {
  if (!peerId) throw new Error("Не указан peer_id");
  if (initializedPeers.has(peerId)) return;
  const accessToken = loadSession()?.accessToken;
  if (!accessToken) throw new Error("Нет активной сессии");
  await requireInvoke()<void>("crypto_ensure_peer_session", {
    accessToken,
    peerId,
  });
  initializedPeers.add(peerId);
}

/** Сбросить кеш «инициализированных» peer'ов (например, после ошибки). */
export function resetPeerCache(peerId?: string): void {
  if (peerId) initializedPeers.delete(peerId);
  else initializedPeers.clear();
}

/** Зашифровать UTF-8 текст для peer'а. Возвращает base64-ciphertext (с type-byte). */
export async function encryptForPeer(peerId: string, plaintext: string): Promise<string> {
  if (!cryptoReady) {
    throw new Error("Криптосессия не готова");
  }
  await ensurePeerSession(peerId);
  const dto = await requireInvoke()<{ ciphertextBase64: string }>("crypto_encrypt", {
    peerId,
    plaintext,
  });
  return dto.ciphertextBase64;
}

/** Расшифровать ciphertext (base64) от peer'а. PreKeyMessage обрабатывается автоматически. */
export async function decryptFromPeer(peerId: string, ciphertextBase64: string): Promise<string> {
  if (!cryptoReady) throw new Error("Криптосессия не готова");
  return requireInvoke()<string>("crypto_decrypt", {
    peerId,
    ciphertextBase64,
  });
}
