/**
 * Лёгкий offline-кеш сообщений в localStorage. До запуска полноценного
 * tauri-plugin-sql даёт мгновенный показ последних диалогов после re-open.
 *
 * Структура:
 *   koto.cache.threads.<convId> = JSON.stringify({ updatedAt, messages: ThreadCacheRow[] })
 *
 * Лимит: 100 последних сообщений на чат, 12 чатов в кеше — ~~200 КБ totally.
 * При превышении — выкидываются самые старые (LRU по convId).
 */

const THREAD_PREFIX = "koto.cache.threads.";
const INDEX_KEY = "koto.cache.thread-index";
const MAX_THREADS = 12;
const MAX_MESSAGES_PER_THREAD = 100;

export interface ThreadCacheRow {
  id: string;
  ciphertext: string;
  sender_id: string;
  sent_at: number;
  /** Plaintext, если он у нас был на момент кеширования. */
  plain?: string;
  reply_to?: string;
}

interface ThreadEnvelope {
  updatedAt: number;
  messages: ThreadCacheRow[];
}

function readIndex(): string[] {
  try {
    const raw = localStorage.getItem(INDEX_KEY);
    if (!raw) return [];
    const arr = JSON.parse(raw) as unknown;
    return Array.isArray(arr) ? (arr as unknown[]).map(String) : [];
  } catch {
    return [];
  }
}

function writeIndex(ids: string[]): void {
  try {
    localStorage.setItem(INDEX_KEY, JSON.stringify(ids));
  } catch {
    /* quota */
  }
}

function bumpIndex(convId: string): void {
  const idx = readIndex().filter((x) => x !== convId);
  idx.unshift(convId);
  while (idx.length > MAX_THREADS) {
    const drop = idx.pop();
    if (drop) {
      try {
        localStorage.removeItem(THREAD_PREFIX + drop);
      } catch {
        /* ignore */
      }
    }
  }
  writeIndex(idx);
}

export function loadThreadFromCache(convId: string): ThreadCacheRow[] {
  if (!convId) return [];
  try {
    const raw = localStorage.getItem(THREAD_PREFIX + convId);
    if (!raw) return [];
    const env = JSON.parse(raw) as ThreadEnvelope;
    return Array.isArray(env.messages) ? env.messages : [];
  } catch {
    return [];
  }
}

export function saveThreadToCache(convId: string, messages: ThreadCacheRow[]): void {
  if (!convId || !messages.length) return;
  const trimmed = messages.slice(-MAX_MESSAGES_PER_THREAD);
  const env: ThreadEnvelope = { updatedAt: Date.now(), messages: trimmed };
  try {
    localStorage.setItem(THREAD_PREFIX + convId, JSON.stringify(env));
    bumpIndex(convId);
  } catch (e) {
    // Если quota превышена — выкинем самый старый и попробуем ещё раз.
    const idx = readIndex();
    if (idx.length) {
      const drop = idx.pop();
      if (drop) localStorage.removeItem(THREAD_PREFIX + drop);
      writeIndex(idx);
      try {
        localStorage.setItem(THREAD_PREFIX + convId, JSON.stringify(env));
      } catch {
        console.warn("offline cache write failed:", e);
      }
    }
  }
}

export function appendToThreadCache(convId: string, row: ThreadCacheRow): void {
  if (!convId || !row.id) return;
  const existing = loadThreadFromCache(convId);
  if (existing.some((r) => r.id === row.id)) return;
  existing.push(row);
  saveThreadToCache(convId, existing);
}

export function updateCachedPlaintext(convId: string, msgId: string, plain: string): void {
  if (!convId || !msgId) return;
  const existing = loadThreadFromCache(convId);
  const m = existing.find((r) => r.id === msgId);
  if (!m) return;
  m.plain = plain;
  saveThreadToCache(convId, existing);
}

export function clearOfflineCache(): void {
  for (const id of readIndex()) {
    try {
      localStorage.removeItem(THREAD_PREFIX + id);
    } catch {
      /* ignore */
    }
  }
  try {
    localStorage.removeItem(INDEX_KEY);
  } catch {
    /* ignore */
  }
}
