import { getAppConfig } from "@/shared/services/configService";
import { gatewayHealth } from "@/shared/services/gatewayService";
import { loadSession } from "@/shared/state/sessionStore";
import { performSignOut } from "@/features/sidebar/sidebarConnectivity";
import { syncFocusSettingsDetail, toggleDesktopFocusMode } from "@/features/sidebar/userbarDrawer";
import { $theme, getTheme } from "@/shared/state/themeStore";
import { mainNav, Screen, type AppScreen } from "@/shared/state/navStore";

function $(id: string): HTMLElement | null {
  return document.getElementById(id);
}

/** Mirrors `settingsSubTitle` in desktop `KotoApp.kt`. */
const SETTINGS_SUB_TITLE: Record<string, string> = {
  profile: "Профиль",
  kotoid: "Мой Koto ID",
  seed: "Фраза восстановления",
  devices: "Связанные устройства",
  username: "Имя пользователя",
  privacy: "Приватность",
  ephemeral: "Исчезающие сообщения",
  screenlock: "Блокировка экрана",
  safety: "Проверка безопасности",
  sealed: "Закрытые отправители",
  storage: "Хранилище",
  network: "Использование сети",
  auto: "Автозагрузка",
  theme: "Тема и цвет",
  focus: "Режим фокуса",
  font: "Размер шрифта",
  wallpaper: "Обои чатов",
  notifications: "Уведомления",
  calls: "Звонки",
  help: "Справка",
  about: "О Koto",
  search: "Поиск",
};

function placeholderBody(section: string): string {
  const lines: Record<string, string> = {
    profile: "Редактирование профиля — заглушка UI; данные с сервера подключатся позже.",
    seed: "Просмотр фразы восстановления только на устройстве — перенос логики из desktop.",
    devices: "Список связанных устройств — заглушка.",
    username: "Имя пользователя @… — заглушка.",
    privacy: "Пресеты приватности — заглушка.",
    ephemeral: "Таймер исчезающих сообщений — заглушка.",
    storage: "Хранилище и кэш — заглушка.",
    network: "Статистика сети — заглушка.",
    auto: "Автозагрузка медиа — заглушка.",
    safety: "Проверка безопасности чатов — заглушка.",
    search: "Поиск по сообщениям в чате — заглушка.",
    notifications: "Настройки уведомлений — заглушка.",
    focus: "Упрощает интерфейс чатов на этом устройстве; переключение — строкой выше.",
  };
  return lines[section] || `Раздел «${section}» — каркас подэкрана; контент как в desktop позже.`;
}

export function applySettingsSubSection(section: string): void {
  const titleEl = $("settings-sub-title");
  if (titleEl) titleEl.textContent = SETTINGS_SUB_TITLE[section] || section;
  const body = $("settings-sub-body");
  if (body) {
    body.innerHTML = `<p class="settings-sub-placeholder">${escapeHtml(placeholderBody(section))}</p>`;
  }
}

function escapeHtml(s: string): string {
  return String(s)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function syncThemeRowLabel(): void {
  const el = $("settings-theme-label");
  if (!el) return;
  el.textContent = getTheme() === "light" ? "Светлая" : "Тёмная";
}

export async function refreshSettingsPane(): Promise<void> {
  const feedback = $("settings-copy-feedback");
  const profileSub = $("settings-profile-sub");
  const idDetail = $("settings-id-detail");
  const restEl = $("settings-rest-url");
  const wsEl = $("settings-ws-hint");
  const healthEl = $("settings-health");

  if (feedback) feedback.textContent = "";

  const session = loadSession();
  const id = session?.accountId?.trim() || "";

  if (profileSub) {
    profileSub.textContent = id ? `${id.slice(0, 14)}${id.length > 14 ? "…" : ""}` : "Нет активного аккаунта";
  }
  if (idDetail) {
    idDetail.textContent = id
      ? `${id.slice(0, 20)}${id.length > 20 ? "…" : ""} · нажмите, чтобы скопировать`
      : "Войдите, чтобы увидеть ID";
  }

  try {
    const cfg = await getAppConfig();
    if (restEl) restEl.textContent = cfg.restBaseUrl || "—";
    if (wsEl) wsEl.textContent = cfg.wsBaseUrl ? `WebSocket: ${cfg.wsBaseUrl}` : "";
  } catch (e) {
    if (restEl) restEl.textContent = String(e);
    if (wsEl) wsEl.textContent = "";
  }

  if (healthEl) healthEl.textContent = "—";
  syncThemeRowLabel();
  syncFocusSettingsDetail();
}

async function runHealthInSettings(): Promise<void> {
  const healthEl = $("settings-health");
  if (healthEl) healthEl.textContent = "…";
  try {
    const body = await gatewayHealth();
    if (healthEl) healthEl.textContent = body || "ok";
  } catch (e) {
    if (healthEl) healthEl.textContent = String(e);
  }
}

export function openSettings(section?: string): void {
  window.dispatchEvent(
    new CustomEvent("koto:open-settings", { detail: section ? { section } : undefined })
  );
}

let lastNonSettingsScreen: AppScreen = Screen.empty();

function syncSettingsSidebarNav(): void {
  const root = document.getElementById("chatlist-settings-nav");
  const bar = document.getElementById("chatlist-userbar");
  const top = document.querySelector(".chatlist-top");
  const scroll = document.querySelector(".chatlist-scroll");
  const sidebarMain = document.querySelector(".sidebar-main");
  const railBack = document.getElementById("chatlist-settings-back-home");
  if (!root || !bar || !top || !scroll || !sidebarMain || !railBack) return;

  const s = mainNav.current;
  if (s.type !== "Settings" && s.type !== "SettingsSub") {
    lastNonSettingsScreen = s;
  }
  const settingsMode = s.type === "Settings" || s.type === "SettingsSub";
  const friendsMode = s.type === "Friends";
  const hideChatsColumn = settingsMode || friendsMode;
  const friendsStack = document.getElementById("friends-sidebar-stack");
  sidebarMain.classList.toggle("sidebar-main--settings-mode", settingsMode);
  sidebarMain.classList.toggle("sidebar-main--friends-mode", friendsMode && !settingsMode);
  root.toggleAttribute("hidden", !settingsMode);
  railBack.toggleAttribute("hidden", !settingsMode);
  bar.toggleAttribute("hidden", settingsMode);
  (top as HTMLElement).toggleAttribute("hidden", hideChatsColumn);
  (scroll as HTMLElement).toggleAttribute("hidden", hideChatsColumn);
  friendsStack?.toggleAttribute("hidden", settingsMode || !friendsMode);

  const activeSection = s.type === "SettingsSub" ? s.section : "profile";
  root.querySelectorAll<HTMLButtonElement>(".chatlist-settings-nav__item[data-settings-sidebar-section]").forEach((btn) => {
    const section = btn.dataset.settingsSidebarSection || "";
    btn.classList.toggle("chatlist-settings-nav__item--active", section === activeSection);
    btn.setAttribute("aria-current", section === activeSection ? "page" : "false");
  });
}

export function initSettingsPane(): void {
  if (document.body.dataset.kotoSettingsPaneInit === "1") return;
  document.body.dataset.kotoSettingsPaneInit = "1";

  // Settings overlay сам слушает `koto:open-settings`; pane больше не открывается через mainNav.
  mainNav.subscribe(() => syncSettingsSidebarNav());
  syncSettingsSidebarNav();

  $theme.subscribe(() => syncThemeRowLabel());
  syncThemeRowLabel();

  $("settings-pane-close")?.addEventListener("click", () => {
    mainNav.resetTo(Screen.empty());
  });

  $("settings-pane-theme")?.addEventListener("click", () => {
    $("theme-toggle")?.click();
  });

  $("settings-theme-row")?.addEventListener("click", () => {
    $("theme-toggle")?.click();
  });

  $("settings-focus-row")?.addEventListener("click", () => {
    toggleDesktopFocusMode();
  });

  $("settings-copy-row")?.addEventListener("click", async () => {
    const id = loadSession()?.accountId?.trim() || "";
    const fb = $("settings-copy-feedback");
    if (!id) {
      if (fb) fb.textContent = "Нечего копировать.";
      return;
    }
    try {
      await navigator.clipboard.writeText(id);
      if (fb) fb.textContent = "Скопировано";
      const det = $("settings-id-detail");
      if (det) det.classList.add("settings-row__detail--accent");
      window.setTimeout(() => {
        if (fb?.textContent === "Скопировано") fb.textContent = "";
        $("settings-id-detail")?.classList.remove("settings-row__detail--accent");
      }, 1600);
    } catch {
      if (fb) fb.textContent = "Не удалось скопировать.";
    }
  });

  $("settings-health-btn")?.addEventListener("click", () => {
    void runHealthInSettings();
  });

  $("settings-sign-out")?.addEventListener("click", () => {
    mainNav.resetTo(Screen.empty());
    performSignOut();
  });

  $("settings-profile-card")?.addEventListener("click", () => {
    mainNav.push(Screen.settingsSub("profile"));
  });

  document.querySelectorAll("[data-settings-section]").forEach((el) => {
    el.addEventListener("click", () => {
      const key = el.getAttribute("data-settings-section");
      if (key) mainNav.push(Screen.settingsSub(key));
    });
  });

  document.querySelectorAll<HTMLButtonElement>("[data-settings-sidebar-section]").forEach((el) => {
    el.addEventListener("click", () => {
      const key = el.dataset.settingsSidebarSection;
      if (!key) return;
      mainNav.push(Screen.settingsSub(key));
    });
  });

  $("settings-sub-back")?.addEventListener("click", () => {
    mainNav.pop();
  });

  document.getElementById("sidebar-open-settings")?.addEventListener("click", () => openSettings());
  document.getElementById("chatlist-settings-back-home")?.addEventListener("click", () => {
    mainNav.resetTo(lastNonSettingsScreen ?? Screen.empty());
  });

  syncFocusSettingsDetail();
  syncSettingsSidebarNav();
}
