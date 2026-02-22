import { secureStorage } from "@/shared/lib/secure-storage";
import { immer } from "zustand/middleware/immer";
import { createStore } from "zustand/vanilla";

type Theme = "dark" | "light";

interface UiState {
  theme: Theme;
  sidebarCollapsed: boolean;
  memberListVisible: boolean;

  setTheme: (theme: Theme) => void;
  toggleTheme: () => void;
  toggleSidebar: () => void;
  toggleMemberList: () => void;
}

function applyTheme(theme: Theme): void {
  document.documentElement.setAttribute("data-theme", theme);
  secureStorage.set("theme", theme);
}

function getInitialTheme(): Theme {
  const stored = secureStorage.get("theme");
  const theme = stored === "dark" || stored === "light" ? stored : "dark";
  applyTheme(theme);
  return theme;
}

export const uiStore = createStore<UiState>()(
  immer((set) => ({
    theme: getInitialTheme(),
    sidebarCollapsed: false,
    memberListVisible: true,

    setTheme: (theme) =>
      set((state) => {
        state.theme = theme;
        applyTheme(theme);
      }),

    toggleTheme: () =>
      set((state) => {
        const next = state.theme === "dark" ? "light" : "dark";
        state.theme = next;
        applyTheme(next);
      }),

    toggleSidebar: () =>
      set((state) => {
        state.sidebarCollapsed = !state.sidebarCollapsed;
      }),

    toggleMemberList: () =>
      set((state) => {
        state.memberListVisible = !state.memberListVisible;
      }),
  })),
);
