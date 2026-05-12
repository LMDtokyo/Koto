/**
 * E2EE-конверт медиа-сообщения. JSON в plaintext'е сообщения вида:
 *   { "v":1, "type":"image", "fileId":"...", "mime":"image/png", "name":"x.png", "size":12345 }
 *
 * Парсер общий для thread-bubble (рендер картинки) и chat-list (превью «📷 Фото»).
 */

export interface MediaEnvelope {
  type: "image";
  fileId: string;
  mime: string;
  name?: string;
  size?: number;
}

export function tryParseMediaEnvelope(plain: string): MediaEnvelope | null {
  if (!plain || plain.length > 2000 || plain[0] !== "{") return null;
  try {
    const o = JSON.parse(plain) as {
      v?: number;
      type?: string;
      fileId?: string;
      mime?: string;
      name?: string;
      size?: number;
    };
    if (o.v !== 1 || o.type !== "image" || !o.fileId) return null;
    return {
      type: o.type,
      fileId: String(o.fileId),
      mime: String(o.mime || "image/png"),
      name: o.name,
      size: o.size,
    };
  } catch {
    return null;
  }
}

/** Короткое превью для чат-листа (TG-style: «📷 Изображение»). Имя файла
 * сознательно НЕ показываем — это доп. метаданные о пользователе. */
export function mediaPreview(env: MediaEnvelope): string {
  if (env.type === "image") return "📷 Изображение";
  return "📎 Файл";
}
