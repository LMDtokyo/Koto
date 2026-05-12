/**
 * Profile editor: загрузка/просмотр/правка профиля + загрузка banner/avatar.
 * Discord-style UX: dirty action-bar появляется при изменениях, save обновляет
 * сервер, кэшируем превью изображений по file_id.
 */

import {
  checkUsernameAvailable,
  getMyProfile,
  resolveMediaImageSrc,
  setUsername,
  updateMyProfile,
  uploadProfileImage,
  type ProfileDto,
} from "@/shared/services/profileService";
import { loadSession } from "@/shared/state/sessionStore";

let pristine: ProfileDto | null = null;
let draft: ProfileDto | null = null;
let inFlight = false;
let usernameDraft = "";
let usernameStatus: "idle" | "checking" | "available" | "taken" | "invalid" = "idle";
let usernameCheckTimer: ReturnType<typeof setTimeout> | null = null;
let usernameReqSeq = 0;

const imageCache = new Map<string, string>();

function $(id: string): HTMLElement | null {
  return document.getElementById(id);
}

function setStatus(message: string, kind: "" | "ok" | "error" = ""): void {
  const el = $("profile-editor-status");
  if (!el) return;
  el.textContent = message;
  el.classList.remove("profile-editor__status--ok", "profile-editor__status--error");
  if (kind === "ok") el.classList.add("profile-editor__status--ok");
  if (kind === "error") el.classList.add("profile-editor__status--error");
}

function fallbackInitials(p: ProfileDto): string {
  const name = (p.display_name || "").trim();
  if (name) {
    const parts = name.split(/\s+/);
    return (
      (parts[0]?.[0] || "") + (parts[1]?.[0] || "")
    ).toUpperCase() || name.slice(0, 2).toUpperCase();
  }
  return (p.account_id || "?").slice(0, 2).toUpperCase();
}

function applyDraftToInputs(): void {
  if (!draft) return;
  const dn = $("profile-editor-display-name") as HTMLInputElement | null;
  const bio = $("profile-editor-bio") as HTMLTextAreaElement | null;
  const counter = $("profile-editor-bio-counter");
  const usernameInput = $("profile-editor-username-input") as HTMLInputElement | null;
  if (dn) dn.value = draft.display_name || "";
  if (bio) bio.value = draft.bio || "";
  if (counter) counter.textContent = String((draft.bio || "").length);
  if (usernameInput) usernameInput.value = draft.username || "";
  usernameDraft = (draft.username || "").trim();
  usernameStatus = "idle";
  syncUsernameHint();

  const id = $("profile-editor-id");
  if (id) id.textContent = draft.account_id || "—";

  const fallback = $("profile-editor-avatar-fallback");
  if (fallback) fallback.textContent = fallbackInitials(draft);

  applyAvatarSrc(draft.avatar_url);
  applyBannerSrc(draft.banner_url);
}

function isValidUsername(u: string): boolean {
  return /^[a-z0-9_]{3,32}$/i.test(u);
}

function syncUsernameHint(): void {
  const input = $("profile-editor-username-input") as HTMLInputElement | null;
  if (!input) return;
  const wrap = input.closest(".profile-editor__username") as HTMLElement | null;
  const hint = wrap?.parentElement?.querySelector<HTMLElement>(".profile-editor__field-hint");
  if (!hint) return;
  const trimmed = usernameDraft.trim();
  if (!trimmed) {
    hint.textContent = "Латиница, цифры и подчёркивания. Можно сменить позже.";
    hint.dataset.state = "idle";
    return;
  }
  if (!isValidUsername(trimmed)) {
    hint.textContent = "Только латиница, цифры и подчёркивания, 3–32 символа.";
    hint.dataset.state = "invalid";
    return;
  }
  switch (usernameStatus) {
    case "checking":
      hint.textContent = "Проверяем доступность…";
      hint.dataset.state = "checking";
      break;
    case "available":
      hint.textContent = `@${trimmed} свободен.`;
      hint.dataset.state = "available";
      break;
    case "taken":
      hint.textContent = `@${trimmed} уже занят.`;
      hint.dataset.state = "taken";
      break;
    default:
      hint.textContent = "Латиница, цифры и подчёркивания. Можно сменить позже.";
      hint.dataset.state = "idle";
  }
}

async function debounceCheckUsername(value: string): Promise<void> {
  if (usernameCheckTimer) clearTimeout(usernameCheckTimer);
  usernameDraft = value.trim();
  if (!usernameDraft) {
    usernameStatus = "idle";
    syncUsernameHint();
    return;
  }
  if (usernameDraft === (pristine?.username || "")) {
    usernameStatus = "idle";
    syncUsernameHint();
    return;
  }
  if (!isValidUsername(usernameDraft)) {
    usernameStatus = "invalid";
    syncUsernameHint();
    return;
  }
  usernameStatus = "checking";
  syncUsernameHint();
  usernameCheckTimer = setTimeout(async () => {
    const reqId = ++usernameReqSeq;
    try {
      const res = await checkUsernameAvailable(usernameDraft);
      if (reqId !== usernameReqSeq) return;
      usernameStatus = res.available ? "available" : "taken";
    } catch {
      if (reqId !== usernameReqSeq) return;
      usernameStatus = "idle";
    }
    syncUsernameHint();
    syncActionBar();
  }, 360);
}

async function applyAvatarSrc(fileId: string): Promise<void> {
  const fb = $("profile-editor-avatar-fallback");
  const img = $("profile-editor-avatar-img") as HTMLImageElement | null;
  if (!fileId) {
    if (img) {
      img.src = "";
      img.setAttribute("hidden", "");
    }
    if (fb) fb.removeAttribute("hidden");
    syncSidebarAvatar("");
    return;
  }
  try {
    const src = await resolveImage(fileId);
    if (img) {
      img.src = src;
      img.removeAttribute("hidden");
    }
    if (fb) fb.setAttribute("hidden", "");
    syncSidebarAvatar(src);
  } catch (e) {
    console.error("avatar resolve failed", e);
  }
}

async function applyBannerSrc(fileId: string): Promise<void> {
  const wrap = $("profile-editor-banner");
  const img = $("profile-editor-banner-img") as HTMLImageElement | null;
  const remove = $("profile-editor-banner-remove");
  if (!wrap) return;
  if (!fileId) {
    wrap.dataset.state = "empty";
    if (img) {
      img.src = "";
      img.setAttribute("hidden", "");
    }
    remove?.setAttribute("hidden", "");
    return;
  }
  try {
    const src = await resolveImage(fileId);
    if (img) {
      img.src = src;
      img.removeAttribute("hidden");
    }
    wrap.dataset.state = "filled";
    remove?.removeAttribute("hidden");
  } catch (e) {
    console.error("banner resolve failed", e);
  }
}

async function resolveImage(fileId: string): Promise<string> {
  const hit = imageCache.get(fileId);
  if (hit) return hit;
  const src = await resolveMediaImageSrc(fileId);
  imageCache.set(fileId, src);
  return src;
}

function syncSidebarAvatar(src: string): void {
  const sidebar = $("sidebar-user-avatar");
  if (!sidebar) return;
  if (src) {
    sidebar.style.backgroundImage = `url("${src}")`;
    sidebar.style.backgroundSize = "cover";
    sidebar.style.backgroundPosition = "center";
    sidebar.textContent = "";
  } else {
    sidebar.style.backgroundImage = "";
  }
}

function detectDirty(): boolean {
  if (!pristine || !draft) return false;
  const usernameChanged =
    usernameDraft !== (pristine.username || "") &&
    usernameDraft !== "" &&
    usernameStatus !== "invalid";
  return (
    (pristine.display_name || "") !== (draft.display_name || "") ||
    (pristine.bio || "") !== (draft.bio || "") ||
    (pristine.avatar_url || "") !== (draft.avatar_url || "") ||
    (pristine.banner_url || "") !== (draft.banner_url || "") ||
    usernameChanged
  );
}

function canSaveUsername(): boolean {
  if (!pristine) return false;
  if (usernameDraft === (pristine.username || "")) return true; // не меняли — ок
  return isValidUsername(usernameDraft) && usernameStatus !== "taken" && usernameStatus !== "invalid";
}

function syncActionBar(): void {
  const bar = $("profile-editor-actions");
  const save = $("profile-editor-save") as HTMLButtonElement | null;
  if (!bar) return;
  const dirty = detectDirty();
  bar.dataset.dirty = dirty ? "true" : "false";
  if (save) save.disabled = !dirty || inFlight || !canSaveUsername();
}

async function loadProfile(): Promise<void> {
  if (!loadSession()?.accessToken) {
    setStatus("Войдите, чтобы редактировать профиль.");
    return;
  }
  setStatus("");
  try {
    const p = await getMyProfile();
    pristine = p;
    draft = { ...p };
    applyDraftToInputs();
    syncActionBar();
  } catch (e: unknown) {
    setStatus(formatErr(e), "error");
  }
}

async function saveProfile(): Promise<void> {
  if (!draft || inFlight) return;
  if (!canSaveUsername()) {
    setStatus("Имя пользователя недоступно — выберите другое.", "error");
    return;
  }
  inFlight = true;
  setStatus("Сохраняем…");
  syncActionBar();
  try {
    let updated = await updateMyProfile({
      displayName: draft.display_name,
      avatarUrl: draft.avatar_url,
      bannerUrl: draft.banner_url,
      bio: draft.bio,
    });
    if (usernameDraft && usernameDraft !== (pristine?.username || "")) {
      updated = await setUsername(usernameDraft);
    }
    pristine = updated;
    draft = { ...updated };
    applyDraftToInputs();
    setStatus("Сохранено.", "ok");
    setTimeout(() => {
      if ($("profile-editor-status")?.textContent === "Сохранено.") setStatus("");
    }, 2200);
  } catch (e: unknown) {
    setStatus(formatErr(e), "error");
  } finally {
    inFlight = false;
    syncActionBar();
  }
}

function resetDraft(): void {
  if (!pristine) return;
  draft = { ...pristine };
  applyDraftToInputs();
  syncActionBar();
  setStatus("");
}

function setLoader(target: "avatar" | "banner", on: boolean): void {
  const id = target === "avatar" ? "profile-editor-avatar-loader" : "profile-editor-banner-loader";
  const el = $(id);
  if (el) el.dataset.on = on ? "true" : "false";
}

async function uploadFile(target: "avatar" | "banner", file: File): Promise<void> {
  if (!draft) return;
  if (!file.type.startsWith("image/")) {
    setStatus("Поддерживаются только изображения.", "error");
    return;
  }
  const previousId = target === "avatar" ? draft.avatar_url : draft.banner_url;
  setLoader(target, true);
  setStatus(target === "avatar" ? "Загружаем аватар…" : "Загружаем баннер…");
  try {
    const fileId = await uploadProfileImage(file);
    if (!draft) return;
    if (target === "avatar") {
      draft.avatar_url = fileId;
      await applyAvatarSrc(fileId);
    } else {
      draft.banner_url = fileId;
      await applyBannerSrc(fileId);
    }
    setStatus("Готово, не забудьте сохранить.");
    syncActionBar();
  } catch (e: unknown) {
    if (draft) {
      // Откат: возвращаем то, что было до попытки.
      if (target === "avatar") {
        draft.avatar_url = previousId;
        await applyAvatarSrc(previousId);
      } else {
        draft.banner_url = previousId;
        await applyBannerSrc(previousId);
      }
    }
    setStatus(formatErr(e), "error");
    syncActionBar();
  } finally {
    setLoader(target, false);
  }
}

async function pickAndUpload(target: "avatar" | "banner"): Promise<void> {
  const inputId = target === "avatar" ? "profile-editor-avatar-input" : "profile-editor-banner-input";
  const input = $(inputId) as HTMLInputElement | null;
  if (!input || !draft) return;
  await new Promise<void>((resolve) => {
    const onChange = async () => {
      input.removeEventListener("change", onChange);
      const file = input.files?.[0];
      input.value = "";
      if (!file) return resolve();
      await uploadFile(target, file);
      resolve();
    };
    input.addEventListener("change", onChange);
    input.click();
  });
}

function bindDragAndDrop(target: "avatar" | "banner", el: HTMLElement): void {
  let depth = 0;
  el.addEventListener("dragenter", (ev) => {
    ev.preventDefault();
    depth += 1;
    el.dataset.drag = "over";
  });
  el.addEventListener("dragover", (ev) => {
    ev.preventDefault();
    if (ev.dataTransfer) ev.dataTransfer.dropEffect = "copy";
  });
  el.addEventListener("dragleave", () => {
    depth = Math.max(0, depth - 1);
    if (depth === 0) el.removeAttribute("data-drag");
  });
  el.addEventListener("drop", (ev) => {
    ev.preventDefault();
    depth = 0;
    el.removeAttribute("data-drag");
    const file = ev.dataTransfer?.files?.[0];
    if (file) void uploadFile(target, file);
  });
}

function formatErr(e: unknown): string {
  if (e && typeof e === "object" && "message" in e) {
    const msg = (e as { message?: unknown }).message;
    if (typeof msg === "string") return msg;
  }
  if (e instanceof Error) return e.message;
  return String(e);
}

export function initProfileEditor(): void {
  if (document.body.dataset.kotoProfileEditor === "1") return;
  document.body.dataset.kotoProfileEditor = "1";

  // Inputs → draft
  $("profile-editor-display-name")?.addEventListener("input", (ev) => {
    const v = (ev.target as HTMLInputElement).value;
    if (draft) draft.display_name = v;
    syncActionBar();
  });

  $("profile-editor-bio")?.addEventListener("input", (ev) => {
    const v = (ev.target as HTMLTextAreaElement).value;
    if (draft) draft.bio = v;
    const counter = $("profile-editor-bio-counter");
    if (counter) counter.textContent = String(v.length);
    syncActionBar();
  });

  $("profile-editor-username-input")?.addEventListener("input", (ev) => {
    const v = (ev.target as HTMLInputElement).value;
    void debounceCheckUsername(v);
    syncActionBar();
  });

  $("profile-editor-save")?.addEventListener("click", () => {
    void saveProfile();
  });

  $("profile-editor-reset")?.addEventListener("click", () => {
    resetDraft();
  });

  $("profile-editor-avatar")?.addEventListener("click", () => {
    void pickAndUpload("avatar");
  });

  // Клик по самому баннеру открывает picker; кнопки внутри overlay тоже остаются.
  const banner = $("profile-editor-banner");
  banner?.addEventListener("click", (ev) => {
    const t = ev.target as HTMLElement | null;
    if (t?.closest("#profile-editor-banner-remove")) return; // remove handled отдельно
    void pickAndUpload("banner");
  });
  banner?.addEventListener("keydown", (ev) => {
    if ((ev as KeyboardEvent).key === "Enter" || (ev as KeyboardEvent).key === " ") {
      ev.preventDefault();
      void pickAndUpload("banner");
    }
  });

  $("profile-editor-banner-pick")?.addEventListener("click", (ev) => {
    ev.stopPropagation();
    void pickAndUpload("banner");
  });

  $("profile-editor-banner-remove")?.addEventListener("click", (ev) => {
    ev.stopPropagation();
    if (!draft) return;
    draft.banner_url = "";
    void applyBannerSrc("");
    syncActionBar();
    setStatus("Баннер будет удалён после сохранения.");
  });

  const avatar = $("profile-editor-avatar");
  if (avatar) bindDragAndDrop("avatar", avatar);
  if (banner) bindDragAndDrop("banner", banner);

  $("profile-editor-copy-id")?.addEventListener("click", async () => {
    const id = (draft?.account_id || loadSession()?.accountId || "").trim();
    if (!id) return;
    try {
      await navigator.clipboard.writeText(id);
      setStatus("ID скопирован.", "ok");
      setTimeout(() => {
        if ($("profile-editor-status")?.textContent === "ID скопирован.") setStatus("");
      }, 1600);
    } catch {
      setStatus("Не удалось скопировать.", "error");
    }
  });

  // Перезагружаем профиль при открытии overlay (через тот же event)
  window.addEventListener("koto:open-settings", () => {
    if (loadSession()?.accessToken) void loadProfile();
  });

  if (loadSession()?.accessToken) void loadProfile();
}
