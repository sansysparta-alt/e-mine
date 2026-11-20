#!/usr/bin/env node
// Appelé par .githooks/pre-commit : vérifie le formatage (Prettier) et le lint (ESLint) des
// fichiers indexés. Ne modifie rien : en cas d'écart, le commit est bloqué avec la commande à lancer.
import { spawnSync } from "node:child_process";
import { existsSync } from "node:fs";
import { join } from "node:path";

const racine = process.cwd();
const prettier = join(racine, "node_modules", "prettier", "bin", "prettier.cjs");
const eslint = join(racine, "node_modules", "eslint", "bin", "eslint.js");
if (!existsSync(prettier) || !existsSync(eslint)) process.exit(0);

const indexes = spawnSync("git", ["diff", "--cached", "--name-only", "--diff-filter=ACMR"], {
  encoding: "utf8",
})
  .stdout.split("\n")
  .map((f) => f.trim())
  .filter(Boolean);

const aFormater = indexes.filter((f) => /\.(ts|tsx|js|mjs|json|css|ya?ml)$/.test(f));
const aLinter = indexes.filter(
  (f) => /\.(ts|tsx|js|mjs)$/.test(f) && !f.startsWith("api/") && !f.includes("/generated/"),
);

let echec = false;
if (aFormater.length) {
  const r = spawnSync(
    process.execPath,
    [prettier, "--check", "--ignore-unknown", "--log-level", "warn", ...aFormater],
    { stdio: "inherit" },
  );
  if (r.status !== 0) {
    console.error("pre-commit : fichiers mal formatés. Lancer : pnpm format, puis git add.");
    echec = true;
  }
}
if (aLinter.length) {
  const r = spawnSync(process.execPath, [eslint, "--no-warn-ignored", ...aLinter], {
    stdio: "inherit",
  });
  if (r.status !== 0) {
    console.error("pre-commit : erreurs ESLint à corriger avant de commiter.");
    echec = true;
  }
}
process.exit(echec ? 1 : 0);
