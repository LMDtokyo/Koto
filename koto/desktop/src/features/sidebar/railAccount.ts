/**
 * Rail-аккаунт меню (TG Desktop-стиль): avatar внизу rail → popover c
 * пунктами Профиль / Настройки / Выход. Заменяет нижнюю Discord-плашку.
 */

import { loadSession } from "@/shared/state/sessionStore";
import { mainNav, Screen } from "@/shared/state/navStore";
import { performSignOut } from "@/features/sidebar/sidebarConnectivity";

function $(id: string): HTMLElement | null {
  return document.getElementById(id);
}

function isOpen(): boolean {
  const pop = $("rail-account-popover");
  return Boolean(pop && !pop.hasAttribute("hidden"));
}

function setOpen(open: boolean): void {
  const btn = $("rail-account-btn");
  const pop = $("rail-account-popover");
  if (!btn || !pop) return;
  if (open) {
    pop.removeAttribute("hidden");
    btn.setAttribute("aria-expanded", "true");
  } else {
    pop.setAttribute("hidden", "");
    btn.setAttribute("aria-expanded", "false");
  }
}

function syncPopoverHeader(): void {
  const session = loadSession();
  const name = $("sidebar-user-name");
  const sub = $("sidebar-user-status");
  const popAv = $("rail-account-popover-avatar-text");
  const railAv = $("sidebar-user-avatar");
  if (session?.accessToken) {
    const id = session.accountId || "";
    const short = id ? `${id.slice(0, 6)}…` : "Koto ID";
    if (name) name.textContent = short;
    const initials = id ? id.slice(0, 2).toUpperCase() : "ID";
    if (popAv) popAv.textContent = initials;
    if (railAv) railAv.textContent = initials;
  } else {
    if (name) name.textContent = "Гость";
    if (sub) sub.textContent = "Войдите в аккаунт";
    if (popAv) popAv.textContent = "?";
    if (railAv) railAv.textContent = "?";
  }
}

export function initRailAccount(): void {
  if (document.body.dataset.kotoRailAccount === "1") return;
  document.body.dataset.kotoRailAccount = "1";

  const btn = $("rail-account-btn");
  const pop = $("rail-account-popover");
  if (!btn || !pop) return;

  syncPopoverHeader();

  btn.addEventListener("click", (ev) => {
    ev.stopPropagation();
    setOpen(!isOpen());
    if (isOpen()) syncPopoverHeader();
  });

  // Click outside → close
  document.addEventListener("click", (ev) => {
    if (!isOpen()) return;
    const target = ev.target as Node | null;
    if (!target) return;
    if (pop.contains(target) || btn.contains(target)) return;
    setOpen(false);
  });

  // Esc → close
  window.addEventListener("keydown", (ev) => {
    if (ev.key === "Escape" && isOpen()) {
      setOpen(false);
      ev.stopPropagation();
    }
  });

  // Закрываем popover, когда пункт выбран — фактический handler пунктов
  // (settings, profile) живёт в settingsPane.ts / sidebarConnectivity.ts,
  // мы только закрываем меню после клика.
  pop.addEventListener("click", (ev) => {
    const t = ev.target as HTMLElement | null;
    if (!t) return;
    const row = t.closest(".rail-account-popover__row");
    if (row) setOpen(false);
  });

  $("rail-account-sign-out")?.addEventListener("click", () => {
    mainNav.resetTo(Screen.empty());
    performSignOut();
  });
}
