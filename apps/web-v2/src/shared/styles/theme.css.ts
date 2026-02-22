import { createGlobalTheme, createThemeContract, keyframes } from "@vanilla-extract/css";

// ---------------------------------------------------------------------------
// Theme Contract — shape of all design tokens.
// CSS variables are defined in dark.css / light.css via [data-theme] selectors.
// This contract maps them to typed TS constants for use in .css.ts files.
// ---------------------------------------------------------------------------

export const vars = createThemeContract({
  color: {
    bgPrimary: null,
    bgSecondary: null,
    bgTertiary: null,
    bgModifierHover: null,
    bgModifierActive: null,

    surface: null,
    surfaceRaised: null,
    surfaceOverlay: null,

    textPrimary: null,
    textSecondary: null,
    textMuted: null,
    textLink: null,

    border: null,
    borderStrong: null,

    accent: null,
    accentHover: null,
    accentText: null,

    success: null,
    warning: null,
    danger: null,
    dangerHover: null,
    info: null,

    statusOnline: null,
    statusIdle: null,
    statusDnd: null,
    statusOffline: null,

    inputBg: null,
    inputBorder: null,

    glowPrimary: null,
    glowSecondary: null,

    overlay: null,

    scrollbarThumb: null,
    scrollbarTrack: null,
  },
  shadow: {
    low: null,
    medium: null,
    high: null,
  },
});

// ---------------------------------------------------------------------------
// Dark theme — default
// ---------------------------------------------------------------------------

createGlobalTheme('[data-theme="dark"]', vars, {
  color: {
    bgPrimary: "#0c0c0f",
    bgSecondary: "#1a1a21",
    bgTertiary: "#22222a",
    bgModifierHover: "rgba(255, 255, 255, 0.06)",
    bgModifierActive: "rgba(255, 255, 255, 0.1)",

    surface: "#27272e",
    surfaceRaised: "#2e2e36",
    surfaceOverlay: "#3a3a44",

    textPrimary: "#f0f0f2",
    textSecondary: "#b0b0bc",
    textMuted: "#8a8a98",
    textLink: "#8dabf7",

    border: "#2a2a32",
    borderStrong: "#44444f",

    accent: "#6272f6",
    accentHover: "#5464e8",
    accentText: "#ffffff",

    success: "#22c55e",
    warning: "#f59e0b",
    danger: "#ef4444",
    dangerHover: "#dc2626",
    info: "#3b82f6",

    statusOnline: "#22c55e",
    statusIdle: "#f59e0b",
    statusDnd: "#ef4444",
    statusOffline: "#8a8a98",

    inputBg: "#111118",
    inputBorder: "#3f3f4d",

    glowPrimary: "rgba(98, 114, 246, 0.07)",
    glowSecondary: "rgba(139, 92, 246, 0.05)",

    overlay: "rgba(0, 0, 0, 0.6)",

    scrollbarThumb: "#3a3a44",
    scrollbarTrack: "transparent",
  },
  shadow: {
    low: "0 1px 3px rgba(0, 0, 0, 0.5)",
    medium: "0 4px 14px rgba(0, 0, 0, 0.6)",
    high: "0 8px 30px rgba(0, 0, 0, 0.7), 0 0 0 1px rgba(255, 255, 255, 0.05)",
  },
});

// ---------------------------------------------------------------------------
// Light theme
// ---------------------------------------------------------------------------

createGlobalTheme('[data-theme="light"]', vars, {
  color: {
    bgPrimary: "#ffffff",
    bgSecondary: "#f4f4f5",
    bgTertiary: "#e4e4e7",
    bgModifierHover: "rgba(0, 0, 0, 0.04)",
    bgModifierActive: "rgba(0, 0, 0, 0.08)",

    surface: "#ffffff",
    surfaceRaised: "#f4f4f5",
    surfaceOverlay: "#e4e4e7",

    textPrimary: "#18181b",
    textSecondary: "#52525b",
    textMuted: "#a1a1aa",
    textLink: "#2563eb",

    border: "#e4e4e7",
    borderStrong: "#d4d4d8",

    accent: "#5b6ef5",
    accentHover: "#4f5de3",
    accentText: "#ffffff",

    success: "#16a34a",
    warning: "#d97706",
    danger: "#dc2626",
    dangerHover: "#b91c1c",
    info: "#2563eb",

    statusOnline: "#16a34a",
    statusIdle: "#d97706",
    statusDnd: "#dc2626",
    statusOffline: "#a1a1aa",

    inputBg: "#ffffff",
    inputBorder: "#d4d4d8",

    glowPrimary: "rgba(91, 110, 245, 0.05)",
    glowSecondary: "rgba(139, 92, 246, 0.03)",

    overlay: "rgba(0, 0, 0, 0.4)",

    scrollbarThumb: "#d4d4d8",
    scrollbarTrack: "transparent",
  },
  shadow: {
    low: "0 1px 2px rgba(0, 0, 0, 0.06)",
    medium: "0 4px 12px rgba(0, 0, 0, 0.1)",
    high: "0 8px 24px rgba(0, 0, 0, 0.15)",
  },
});

// ---------------------------------------------------------------------------
// Design tokens — spacing, radii, typography, transitions
// ---------------------------------------------------------------------------

export const space = {
  0: "0",
  1: "0.25rem",
  2: "0.5rem",
  3: "0.75rem",
  4: "1rem",
  5: "1.25rem",
  6: "1.5rem",
  8: "2rem",
  10: "2.5rem",
  12: "3rem",
  16: "4rem",
  20: "5rem",
};

export const radii = {
  sm: "6px",
  md: "8px",
  lg: "12px",
  xl: "16px",
  full: "9999px",
};

export const fontSizes = {
  xs: "0.6875rem",
  sm: "0.875rem",
  base: "1rem",
  lg: "1.125rem",
  xl: "1.25rem",
  "2xl": "1.5rem",
  "3xl": "1.875rem",
};

export const fontWeights = {
  normal: "400",
  medium: "500",
  semibold: "600",
  bold: "700",
};

export const transitions = {
  fast: "150ms ease",
  base: "200ms ease",
  slow: "350ms ease",
};

export const breakpoints = {
  sm: "640px",
  md: "768px",
  lg: "1024px",
  xl: "1280px",
};

// ---------------------------------------------------------------------------
// Keyframes
// ---------------------------------------------------------------------------

export const skeletonShimmer = keyframes({
  "0%": { backgroundPosition: "-400px 0" },
  "100%": { backgroundPosition: "400px 0" },
});

export const cardIn = keyframes({
  from: { opacity: "0", transform: "translateY(12px)" },
  to: { opacity: "1", transform: "translateY(0)" },
});

export const fadeIn = keyframes({
  from: { opacity: "0" },
  to: { opacity: "1" },
});

export const shake = keyframes({
  "0%, 100%": { transform: "translateX(0)" },
  "20%": { transform: "translateX(-5px)" },
  "40%": { transform: "translateX(5px)" },
  "60%": { transform: "translateX(-3px)" },
  "80%": { transform: "translateX(3px)" },
});

export const spin = keyframes({
  from: { transform: "rotate(0deg)" },
  to: { transform: "rotate(360deg)" },
});

// ---------------------------------------------------------------------------
// Re-export utility helper from non-css.ts file so VE child compiler can parse it
// ---------------------------------------------------------------------------

export { withAlpha } from "./utils";
