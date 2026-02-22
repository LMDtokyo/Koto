import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { defineConfig } from "@solidjs/start/config";
import { vanillaExtractPlugin } from "@vanilla-extract/vite-plugin";

const __dirname = dirname(fileURLToPath(import.meta.url));

export default defineConfig({
  ssr: false,
  vite: {
    plugins: [vanillaExtractPlugin()],
    resolve: {
      alias: { "@": resolve(__dirname, "src") },
    },
  },
});
