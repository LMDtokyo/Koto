import js from "@eslint/js";
import boundaries from "eslint-plugin-boundaries";
import tseslint from "typescript-eslint";

export default tseslint.config(
  js.configs.recommended,
  ...tseslint.configs.strict,
  {
    plugins: { boundaries },
    settings: {
      "boundaries/elements": [
        { type: "app", pattern: "src/app/*" },
        { type: "pages", pattern: "src/pages/*" },
        { type: "widgets", pattern: "src/widgets/*" },
        { type: "features", pattern: "src/features/*" },
        { type: "entities", pattern: "src/entities/*" },
        { type: "shared", pattern: "src/shared/*" },
      ],
      "boundaries/ignore": ["**/*.test.*", "**/*.spec.*", "**/*.css.ts"],
    },
    rules: {
      "boundaries/element-types": [
        "error",
        {
          default: "disallow",
          rules: [
            { from: "app", allow: ["pages", "widgets", "features", "entities", "shared"] },
            { from: "pages", allow: ["widgets", "features", "entities", "shared"] },
            { from: "widgets", allow: ["features", "entities", "shared"] },
            { from: "features", allow: ["entities", "shared"] },
            { from: "entities", allow: ["shared"] },
            { from: "shared", allow: ["shared"] },
          ],
        },
      ],
      // Mirroring Rust backend: no .unwrap() / .expect() style unsafe access
      "no-restricted-syntax": [
        "error",
        {
          selector: "MemberExpression[property.name='unwrap']",
          message: "FORBIDDEN: .unwrap() is not allowed. Handle errors explicitly.",
        },
        {
          selector: "TSNonNullExpression",
          message: "FORBIDDEN: Non-null assertion (!) is not allowed. Use optional chaining or explicit checks.",
        },
      ],
    },
  },
  {
    ignores: ["dist/**", "node_modules/**", "*.config.*", "**/*.d.ts"],
  },
);
