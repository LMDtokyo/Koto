/**
 * Кеш расшифрованных и собственных-отправленных plaintext-ов сообщений.
 *
 * Signal Protocol не даёт расшифровать своё же сообщение, поэтому plaintext
 * исходящих мы запоминаем в момент отправки. Входящие — после успешного
 * `decryptFromPeer`. Кеш живёт только в памяти (per-runtime); persistent
 * кеш — пост-MVP (P1.8 — SQLite).
 */

const cache = new Map<string, string>();

export function rememberPlaintext(messageId: string, plain: string): void {
  if (!messageId) return;
  cache.set(messageId, plain);
}

export function getPlaintext(messageId: string): string | undefined {
  return messageId ? cache.get(messageId) : undefined;
}

export function clearMessageCache(): void {
  cache.clear();
}
