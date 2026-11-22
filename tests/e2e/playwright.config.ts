import { existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { defineConfig, devices } from "@playwright/test";

// Variables de démonstration (mot de passe des personas, adresses) : .env à la racine, jamais versionné.
const envRacine = fileURLToPath(new URL("../../.env", import.meta.url));
if (existsSync(envRacine)) {
  process.loadEnvFile(envRacine);
}

/**
 * Test de bout en bout du parcours V1 (US-07.4). Les services tournent déjà (CI : workflow
 * e2e.yml ; poste : pnpm infra:up, pnpm dev, pnpm db:seed, pnpm signature:certificat).
 * Adresses surchargeables par APP_URL et PUBLIC_WEB_URL.
 */
export default defineConfig({
  testDir: "./specs",
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 5 * 60 * 1000,
  expect: { timeout: 20_000 },
  forbidOnly: Boolean(process.env.CI),
  reporter: process.env.CI ? [["list"], ["html", { open: "never" }]] : [["list"]],
  use: {
    baseURL: process.env.APP_URL ?? "http://localhost:3001",
    locale: "fr-FR",
    timezoneId: "Africa/Conakry",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    actionTimeout: 20_000,
    navigationTimeout: 60_000,
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
