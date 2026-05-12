/**
 * Тема light/dark — nanostores + localStorage.
 * Новые экраны могут подписываться через $theme.subscribe / useStore в React.
 */
import { atom } from "nanostores";

const STORAGE_KEY = "koto.desktop.theme";

export type ThemeMode = "dark" | "light";

function readStored(): ThemeMode {
  try {
    const v = localStorage.getItem(STORAGE_KEY);
    return v === "light" ? "light" : "dark";
  } catch {
    return "dark";
  }
}

/** Реактивное значение темы */
export const $theme = atom<ThemeMode>(readStored());

function applyToDocument(mode: ThemeMode): void {
  document.documentElement.dataset.theme = mode;
  try {
    localStorage.setItem(STORAGE_KEY, mode);
  } catch {
    /* приватный режим / запрет storage */
  }
}

let domSubscribed = false;

export function initTheme(): void {
  applyToDocument($theme.get());
  if (!domSubscribed) {
    $theme.subscribe((mode) => {
      applyToDocument(mode);
    });
    domSubscribed = true;
  }
}

export function setTheme(mode: ThemeMode): void {
  $theme.set(mode);
}

export function getTheme(): ThemeMode {
  return $theme.get();
}

export function bindThemeToggle(el: HTMLElement | null): void {
  if (!el) return;
  const sync = () => {
    el.setAttribute("aria-pressed", getTheme() === "light" ? "true" : "false");
    el.title = getTheme() === "dark" ? "Светлая тема" : "Тёмная тема";
  };
  sync();
  $theme.subscribe(() => {
    sync();
  });
  const onPrimaryDown = (e: MouseEvent) => {
    if (e.button !== 0) return;
    e.preventDefault();
    e.stopPropagation();
    setTheme(getTheme() === "dark" ? "light" : "dark");
  };
  el.addEventListener("mousedown", onPrimaryDown, { capture: true });
}
