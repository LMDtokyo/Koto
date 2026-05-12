import path from "node:path";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

const host = process.env.TAURI_DEV_HOST;

// https://v2.tauri.app/start/frontend/vite/
export default defineConfig(({ command }) => ({
  plugins: [react()],
  root: path.resolve(__dirname, "src"),
  publicDir: path.resolve(__dirname, "public"),
  /** В build — относительные URL для tauri://; в dev — абсолютные для Vite HMR. */
  base: command === "build" ? "./" : "/",
  envPrefix: ["VITE_", "TAURI_ENV_"],
  clearScreen: false,
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "src"),
    },
  },
  server: {
    /* Только для ручного `vite` / `vite preview` — `tauri dev` не поднимает dev-server (см. tauri.conf). */
    port: 1420,
    strictPort: true,
    host: host || false,
    hmr: host
      ? { protocol: "ws", host, port: 1421 }
      : undefined,
    watch: {
      ignored: ["**/src-tauri/**"],
    },
  },
  build: {
    outDir: path.resolve(__dirname, "dist"),
    emptyOutDir: true,
    target:
      process.env.TAURI_ENV_PLATFORM === "windows" ? "chrome105" : "safari13",
    minify: process.env.TAURI_ENV_DEBUG ? false : "esbuild",
    sourcemap: !!process.env.TAURI_ENV_DEBUG,
    rollupOptions: {
      input: path.resolve(__dirname, "src/index.html"),
    },
  },
}));
