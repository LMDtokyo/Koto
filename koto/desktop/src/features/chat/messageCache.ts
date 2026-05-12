/**
 * Кеш расшифрованных и собственных-отправленных plaintext-ов сообщений.
 *
 * Signal Protocol не даёт расшифровать своё же сообщение, поэтому plaintext
 * исходящих мы запоминаем в момент отправки. Входящие — после успешного
 * `decryptFromPeer`.
 *
 * Помимо in-memory мапы кешируем в localStorage: иначе после Vite-reload (или
 * перезапуска приложения) plaintext теряется, а libsignal-сессия в Rust уже
 * advanced counter — повторная расшифровка того же ciphertext падает с
 * "old counter". С localStorage превью переживает любой reload.
 */

const LS_PREFIX = "koto.msg.plain.";
const LS_INDEX = "koto.msg.plain.index"; // CSV msgIds для FIFO-выселения
const MAX_ENTRIES = 5000;
const cache = new Map<string, string>();

let indexLoaded = false;
function ensureIndexLoaded(): void {
  if (indexLoaded) return;
  indexLoaded = true;
  try {
    const idx = localStorage.getItem(LS_INDEX) || "";
    if (!idx) return;
    for (const id of idx.split(",").filter(Boolean)) {
      const v = localStorage.getItem(LS_PREFIX + id);
      if (v != null) cache.set(id, v);
    }
  } catch {
    /* localStorage unavailable */
  }
}

function persist(messageId: string, plain: string): void {
  try {
    localStorage.setItem(LS_PREFIX + messageId, plain);
    const idx = localStorage.getItem(LS_INDEX) || "";
    const ids = idx ? idx.split(",") : [];
    if (!ids.includes(messageId)) {
      ids.push(messageId);
      // FIFO-выселение чтобы не разрастаться бесконечно.
      while (ids.length > MAX_ENTRIES) {
        const evict = ids.shift();
        if (evict) localStorage.removeItem(LS_PREFIX + evict);
      }
      localStorage.setItem(LS_INDEX, ids.join(","));
    }
  } catch {
    /* quota / unavailable */
  }
}

export function rememberPlaintext(messageId: string, plain: string): void {
  if (!messageId) return;
  cache.set(messageId, plain);
  persist(messageId, plain);
}

export function getPlaintext(messageId: string): string | undefined {
  if (!messageId) return undefined;
  ensureIndexLoaded();
  return cache.get(messageId);
}

export function clearMessageCache(): void {
  cache.clear();
  try {
    const idx = localStorage.getItem(LS_INDEX) || "";
    for (const id of idx.split(",").filter(Boolean)) {
      localStorage.removeItem(LS_PREFIX + id);
    }
    localStorage.removeItem(LS_INDEX);
  } catch {
    /* ignore */
  }
}
