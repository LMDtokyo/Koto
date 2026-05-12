/**
 * Settings overlay logic — open/close, scroll-spy, search-filter, hooks для тех же
 * действий, что были в pane (theme toggle, focus mode, copy ID, sign out, health check).
 */

import { $theme, getTheme, setTheme } from "@/shared/state/themeStore";
import { syncFocusSettingsDetail, toggleDesktopFocusMode } from "@/features/sidebar/userbarDrawer";
import { performSignOut } from "@/features/sidebar/sidebarConnectivity";
import { mainNav, Screen } from "@/shared/state/navStore";

const LS_PREFIX = "koto.settings.";
const LS_FOCUS = "koto.desktop.focusMode";

function readPref(key: string, fallback: string): string {
  try {
    return localStorage.getItem(LS_PREFIX + key) ?? fallback;
  } catch {
    return fallback;
  }
}

function writePref(key: string, value: string): void {
  try {
    localStorage.setItem(LS_PREFIX + key, value);
  } catch {
    /* ignore */
  }
}

function readBoolPref(key: string, fallback: boolean): boolean {
  const v = readPref(key, fallback ? "1" : "0");
  return v === "1";
}

function writeBoolPref(key: string, value: boolean): void {
  writePref(key, value ? "1" : "0");
}

function $(id: string): HTMLElement | null {
  return document.getElementById(id);
}

function isOpen(): boolean {
  const el = $("settings-overlay");
  return Boolean(el && !el.hasAttribute("hidden"));
}

const DEFAULT_SECTION = "profile";

export function openSettingsOverlay(initialSection?: string): void {
  const el = $("settings-overlay");
  if (!el) return;
  el.removeAttribute("hidden");
  syncDynamicData();
  syncFocusSettingsDetail();
  const target = initialSection || getActiveSection() || DEFAULT_SECTION;
  showSection(target);
  // фокус в поиск через rAF, чтобы анимация открытия не съела focus-ring
  requestAnimationFrame(() => {
    ($("settings-overlay-search") as HTMLInputElement | null)?.focus();
  });
}

export function closeSettingsOverlay(): void {
  const el = $("settings-overlay");
  if (!el) return;
  el.setAttribute("hidden", "");
}

function getActiveSection(): string {
  const active = document.querySelector<HTMLButtonElement>(
    ".settings-overlay__nav-item--active[data-section]"
  );
  return active?.dataset.section || "";
}

function showSection(id: string): void {
  if (!id) return;
  let matched = false;
  document.querySelectorAll<HTMLElement>(".settings-overlay__section[data-section-target]").forEach(
    (sec) => {
      const on = sec.dataset.sectionTarget === id;
      if (on) matched = true;
      sec.toggleAttribute("hidden", !on);
    }
  );
  if (!matched) {
    // Если запрошенная категория исчезла из DOM — fallback на первую видимую.
    const first = document.querySelector<HTMLElement>(
      ".settings-overlay__section[data-section-target]"
    );
    if (first) {
      const fallback = first.dataset.sectionTarget || "";
      first.removeAttribute("hidden");
      updateActiveCategory(fallback);
      return;
    }
  }
  updateActiveCategory(id);
  const scroll = $("settings-overlay-scroll");
  if (scroll) scroll.scrollTop = 0;
}

function updateActiveCategory(id: string): void {
  document.querySelectorAll<HTMLButtonElement>(".settings-overlay__nav-item[data-section]").forEach((btn) => {
    btn.classList.toggle("settings-overlay__nav-item--active", btn.dataset.section === id);
  });

  const item = document.querySelector<HTMLButtonElement>(
    `.settings-overlay__nav-item[data-section="${id}"]`
  );
  if (!item) return;
  const title = item.dataset.sectionTitle || item.textContent?.trim() || "";
  const group =
    (item.parentElement as HTMLElement | null)?.dataset.navGroup ||
    (item.closest("[data-nav-group]") as HTMLElement | null)?.dataset.navGroup ||
    "";
  const titleEl = $("settings-overlay-title");
  const crumbEl = $("settings-overlay-crumb");
  if (titleEl) titleEl.textContent = title;
  if (crumbEl) crumbEl.textContent = group;
}

function setupSearch(): void {
  const input = $("settings-overlay-search") as HTMLInputElement | null;
  if (!input) return;
  input.addEventListener("input", () => {
    const q = input.value.trim().toLowerCase();
    const navItems = Array.from(
      document.querySelectorAll<HTMLButtonElement>(".settings-overlay__nav-item[data-section]")
    );

    if (!q) {
      navItems.forEach((btn) => btn.removeAttribute("hidden"));
      document.querySelectorAll<HTMLElement>(".settings-overlay__nav-group").forEach((g) =>
        g.removeAttribute("hidden")
      );
      return;
    }

    // Поиск только по пунктам в sidebar — секции продолжают работать в режиме «одна за раз».
    navItems.forEach((btn) => {
      const id = btn.dataset.section || "";
      const txt = (btn.textContent || "").toLowerCase();
      const sec = document.getElementById(`sec-${id}`);
      const secTxt = (sec?.textContent || "").toLowerCase();
      const hit = txt.includes(q) || secTxt.includes(q);
      btn.toggleAttribute("hidden", !hit);
    });

    document.querySelectorAll<HTMLElement>(".settings-overlay__nav-group").forEach((group) => {
      const visible = group.querySelectorAll(".settings-overlay__nav-item:not([hidden])").length > 0;
      group.toggleAttribute("hidden", !visible);
    });
  });

  // Enter в поиске — открыть первую найденную категорию.
  input.addEventListener("keydown", (ev) => {
    if (ev.key !== "Enter") return;
    const first = document.querySelector<HTMLButtonElement>(
      ".settings-overlay__nav-item[data-section]:not([hidden])"
    );
    if (first?.dataset.section) {
      showSection(first.dataset.section);
    }
  });
}

function syncThemeSegmented(): void {
  const t = getTheme();
  document.querySelectorAll<HTMLButtonElement>("[data-theme-pick]").forEach((btn) => {
    btn.classList.toggle("settings-overlay__segmented-opt--active", btn.dataset.themePick === t);
  });
}

function syncFocusToggle(): void {
  const cb = $("settings-overlay-focus-toggle") as HTMLInputElement | null;
  const detail = $("settings-overlay-focus-detail");
  if (!cb) return;
  let on = false;
  try {
    on = localStorage.getItem(LS_FOCUS) === "1";
  } catch {
    /* ignore */
  }
  cb.checked = on;
  if (detail) detail.textContent = on ? "Включено" : "Выключено";
}

function syncSegmentedFromPref(attr: string, prefKey: string, fallback: string): void {
  const value = readPref(prefKey, fallback);
  document.querySelectorAll<HTMLButtonElement>(`[${attr}]`).forEach((btn) => {
    btn.classList.toggle(
      "settings-overlay__segmented-opt--active",
      btn.getAttribute(attr) === value
    );
  });
}

function syncToggleFromPref(attr: string, prefKey: string, fallback: boolean): void {
  const cb = document.querySelector<HTMLInputElement>(`input[${attr}]`);
  if (!cb) return;
  cb.checked = readBoolPref(prefKey, fallback);
}

function applyFontPreset(value: string): void {
  const map: Record<string, string> = { sm: "13px", md: "15px", lg: "17px", xl: "19px" };
  document.documentElement.style.setProperty("--font-preview-size", map[value] || "15px");
}

function syncDynamicData(): void {
  // Старая secret-section скрыта; ID идёт через profile editor.
  syncThemeSegmented();
  syncFocusToggle();
  syncSegmentedFromPref("data-ephemeral", "ephemeral", "0");
  syncSegmentedFromPref("data-priv-msg", "priv.msg", "all");
  syncSegmentedFromPref("data-font", "font", "md");
  syncToggleFromPref("data-priv-toggle=\"online\"", "priv.online", true);
  syncToggleFromPref("data-priv-toggle=\"receipts\"", "priv.receipts", true);
  syncToggleFromPref("data-net-toggle=\"saver\"", "net.saver", false);
  syncToggleFromPref("data-net-toggle=\"proxy\"", "net.proxy", false);
  syncToggleFromPref("data-auto-toggle=\"wifi\"", "auto.wifi", true);
  syncToggleFromPref("data-auto-toggle=\"cell\"", "auto.cell", false);
  syncToggleFromPref("data-auto-toggle=\"roaming\"", "auto.roaming", false);
  applyFontPreset(readPref("font", "md"));
}

export function initSettingsOverlay(): void {
  if (document.body.dataset.kotoSettingsOverlay === "1") return;
  document.body.dataset.kotoSettingsOverlay = "1";

  const overlay = $("settings-overlay");
  if (!overlay) return;

  // Открытие через старый custom event и через openSettings()
  window.addEventListener("koto:open-settings", ((ev: Event) => {
    const section = (ev as CustomEvent<{ section?: string }>).detail?.section?.trim();
    openSettingsOverlay(section);
  }) as EventListener);

  // Закрытие
  $("settings-overlay-close")?.addEventListener("click", closeSettingsOverlay);
  overlay.querySelector<HTMLElement>("[data-settings-backdrop]")?.addEventListener("click", () => {
    closeSettingsOverlay();
  });
  window.addEventListener("keydown", (ev) => {
    if (ev.key === "Escape" && isOpen()) {
      ev.stopPropagation();
      closeSettingsOverlay();
    }
  });

  // Категории → показать одну секцию
  document.querySelectorAll<HTMLButtonElement>(".settings-overlay__nav-item[data-section]").forEach((btn) => {
    btn.addEventListener("click", () => {
      const id = btn.dataset.section;
      if (id) showSection(id);
    });
  });

  // Sign-out
  $("settings-overlay-sign-out")?.addEventListener("click", () => {
    closeSettingsOverlay();
    mainNav.resetTo(Screen.empty());
    performSignOut();
  });

  // Theme: segmented с двумя опциями, прямое переключение
  document.querySelectorAll<HTMLButtonElement>("[data-theme-pick]").forEach((btn) => {
    btn.addEventListener("click", () => {
      const v = btn.dataset.themePick;
      if (v === "light" || v === "dark") setTheme(v);
    });
  });
  $theme.subscribe(() => {
    if (isOpen()) syncThemeSegmented();
  });

  // Focus mode toggle (switch)
  $("settings-overlay-focus-toggle")?.addEventListener("change", (ev) => {
    const cb = ev.target as HTMLInputElement;
    const wantOn = cb.checked;
    let isOn = false;
    try {
      isOn = localStorage.getItem(LS_FOCUS) === "1";
    } catch {
      /* ignore */
    }
    if (wantOn !== isOn) toggleDesktopFocusMode();
    syncFocusToggle();
    syncFocusSettingsDetail();
  });

  // Ephemeral / Privacy / Font — segmented controls c local-state
  bindSegmented("data-ephemeral", "ephemeral");
  bindSegmented("data-priv-msg", "priv.msg");
  bindSegmented("data-font", "font", (v) => applyFontPreset(v));

  // Privacy / Network / Auto — toggles
  bindToggle("data-priv-toggle=\"online\"", "priv.online");
  bindToggle("data-priv-toggle=\"receipts\"", "priv.receipts");
  bindToggle("data-net-toggle=\"saver\"", "net.saver");
  bindToggle("data-net-toggle=\"proxy\"", "net.proxy");
  bindToggle("data-auto-toggle=\"wifi\"", "auto.wifi");
  bindToggle("data-auto-toggle=\"cell\"", "auto.cell");
  bindToggle("data-auto-toggle=\"roaming\"", "auto.roaming");

  // Focus detail sync через storage (если меняется в другом компоненте)
  window.addEventListener("storage", (ev) => {
    if (ev.key === LS_FOCUS && isOpen()) {
      syncFocusToggle();
      syncFocusSettingsDetail();
    }
  });

  setupSearch();
}

function bindSegmented(attr: string, prefKey: string, sideEffect?: (v: string) => void): void {
  document.querySelectorAll<HTMLButtonElement>(`[${attr}]`).forEach((btn) => {
    btn.addEventListener("click", () => {
      const v = btn.getAttribute(attr) || "";
      writePref(prefKey, v);
      document.querySelectorAll<HTMLButtonElement>(`[${attr}]`).forEach((b) => {
        b.classList.toggle(
          "settings-overlay__segmented-opt--active",
          b.getAttribute(attr) === v
        );
      });
      sideEffect?.(v);
    });
  });
}

function bindToggle(attrSelector: string, prefKey: string): void {
  const cb = document.querySelector<HTMLInputElement>(`input[${attrSelector}]`);
  if (!cb) return;
  cb.addEventListener("change", () => {
    writeBoolPref(prefKey, cb.checked);
  });
}
