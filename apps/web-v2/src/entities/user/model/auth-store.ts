import { bindTokenAccessor } from "@/shared/api/client";
import { secureStorage } from "@/shared/lib/secure-storage";
import type { User } from "@/shared/types";
import { immer } from "zustand/middleware/immer";
import { createStore } from "zustand/vanilla";

interface AuthState {
  user: User | null;
  accessToken: string | null;
  isAuthenticated: boolean;

  setAuth: (user: User, token: string) => void;
  setAccessToken: (token: string) => void;
  logout: () => void;
}

function getPersistedToken(): string | null {
  return secureStorage.get("access_token");
}

export const authStore = createStore<AuthState>()(
  immer((set, get) => {
    const store: AuthState = {
      user: null,
      accessToken: getPersistedToken(),
      isAuthenticated: getPersistedToken() !== null,

      setAuth: (user, token) =>
        set((state) => {
          state.user = user;
          state.accessToken = token;
          state.isAuthenticated = true;
          secureStorage.set("access_token", token);
        }),

      setAccessToken: (token) =>
        set((state) => {
          state.accessToken = token;
          secureStorage.set("access_token", token);
        }),

      logout: () =>
        set((state) => {
          state.user = null;
          state.accessToken = null;
          state.isAuthenticated = false;
          secureStorage.remove("access_token");
        }),
    };

    bindTokenAccessor({
      getToken: () => get().accessToken,
      setToken: (token) => get().setAccessToken(token),
      clearAuth: () => get().logout(),
    });

    return store;
  }),
);
