/**
 * Profile + media: тонкие обёртки над Rust IPC. Все вызовы аутентифицируются
 * access-token из локальной сессии.
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

export interface ProfileDto {
  account_id: string;
  display_name: string;
  avatar_url: string;
  banner_url: string;
  bio: string;
  username: string;
}

export async function getMyProfile(): Promise<ProfileDto> {
  return requireInvoke()<ProfileDto>("get_my_profile", { accessToken: requireToken() });
}

export interface UpdateProfileInput {
  displayName?: string;
  avatarUrl?: string;
  bannerUrl?: string;
  bio?: string;
}

export async function updateMyProfile(input: UpdateProfileInput): Promise<ProfileDto> {
  return requireInvoke()<ProfileDto>("update_my_profile", {
    accessToken: requireToken(),
    displayName: input.displayName ?? "",
    avatarUrl: input.avatarUrl ?? "",
    bannerUrl: input.bannerUrl ?? "",
    bio: input.bio ?? "",
  });
}

export interface UploadUrlDto {
  file_id: string;
  upload_url: string;
}

export async function requestMediaUploadUrl(
  contentType: string,
  sizeBytes: number,
  isPublic = true
): Promise<UploadUrlDto> {
  return requireInvoke()<UploadUrlDto>("request_media_upload_url", {
    accessToken: requireToken(),
    contentType,
    sizeBytes,
    isPublic,
  });
}

export interface MediaUrlDto {
  file_id: string;
  download_url: string;
  content_type?: string;
}

export async function getMediaDownloadUrl(fileId: string): Promise<MediaUrlDto> {
  return requireInvoke()<MediaUrlDto>("get_media_download_url", {
    accessToken: requireToken(),
    fileId,
  });
}

/**
 * Загружает файл (≤ ~10MB) в MinIO через пресайнед URL: bytes → base64 → Rust PUT.
 * Возвращает file_id, который потом сохраняется в `avatar_url` / `banner_url`.
 */
export async function uploadProfileImage(file: File): Promise<string> {
  if (file.size > 10 * 1024 * 1024) {
    throw new Error("Файл слишком большой (максимум 10 МБ)");
  }
  const contentType = file.type || "application/octet-stream";
  const presigned = await requestMediaUploadUrl(contentType, file.size, true);
  const buffer = await file.arrayBuffer();
  const base64 = arrayBufferToBase64(buffer);
  await requireInvoke()<void>("upload_to_presigned", {
    uploadUrl: presigned.upload_url,
    contentType,
    bodyBase64: base64,
  });
  return presigned.file_id;
}

function arrayBufferToBase64(buf: ArrayBuffer): string {
  const bytes = new Uint8Array(buf);
  // Большие файлы частями, иначе stack overflow в String.fromCharCode.apply
  const chunk = 0x8000;
  let bin = "";
  for (let i = 0; i < bytes.length; i += chunk) {
    bin += String.fromCharCode.apply(null, Array.from(bytes.subarray(i, i + chunk)));
  }
  return btoa(bin);
}

/**
 * Вспомогательная: оборачивает download_url в полный URL для img.src.
 * MinIO возвращает абсолютный URL — отдаём как есть, но если он относительный
 * (case с локальным docker-mapping) — дополняем хост из window.KOTO_BASE_URL.
 */
export async function resolveMediaImageSrc(fileId: string): Promise<string> {
  if (!fileId) return "";
  const dto = await getMediaDownloadUrl(fileId);
  return dto.download_url;
}

export interface UsernameAvailableDto {
  available: boolean;
  reason?: string;
}

export async function checkUsernameAvailable(username: string): Promise<UsernameAvailableDto> {
  return requireInvoke()<UsernameAvailableDto>("user_username_available", {
    accessToken: requireToken(),
    username,
  });
}

export async function setUsername(username: string): Promise<ProfileDto> {
  return requireInvoke()<ProfileDto>("user_set_username", {
    accessToken: requireToken(),
    username,
  });
}
