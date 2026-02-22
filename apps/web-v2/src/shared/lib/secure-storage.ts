/**
 * Secure localStorage wrapper.
 * Centralizes all storage access. Never store raw secrets in plain localStorage
 * outside of this module. If we need encryption later, it goes here.
 */

const PREFIX = "koto_";

export const secureStorage = {
  get(key: string): string | null {
    try {
      return localStorage.getItem(`${PREFIX}${key}`);
    } catch {
      return null;
    }
  },

  set(key: string, value: string): void {
    try {
      localStorage.setItem(`${PREFIX}${key}`, value);
    } catch {
      // quota exceeded or private browsing — silently ignore
    }
  },

  remove(key: string): void {
    try {
      localStorage.removeItem(`${PREFIX}${key}`);
    } catch {
      // ignore
    }
  },
};
