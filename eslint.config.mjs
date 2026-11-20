// ESLint (configuration « flat » unique pour tout le monorepo)
import js from "@eslint/js";
import nextVitals from "eslint-config-next/core-web-vitals";
import tseslint from "typescript-eslint";

export default tseslint.config(
  {
    ignores: [
      "**/node_modules/**",
      "**/.next/**",
      "**/.turbo/**",
      "**/coverage/**",
      "**/next-env.d.ts",
      "packages/api-client/src/generated/**",
      "api/**",
      "_client/**",
    ],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  ...nextVitals,
  {
    settings: { next: { rootDir: ["apps/web/", "apps/app/"] } },
  },
  {
    files: ["**/*.ts", "**/*.tsx"],
    rules: {
      "@typescript-eslint/no-unused-vars": ["error", { argsIgnorePattern: "^_" }],
      "@typescript-eslint/consistent-type-imports": "error",
    },
  },
  {
    // Fichiers JS : parseur typescript-eslint (celui de Next exige next/babel, introuvable hors des apps)
    files: ["**/*.js", "**/*.mjs"],
    languageOptions: {
      parser: tseslint.parser,
      globals: { process: "readonly", console: "readonly" },
    },
  },
);
