#!/usr/bin/env node
// Lance le wrapper Maven du dépôt quel que soit le système (mvnw.cmd sous Windows, mvnw ailleurs).
// Usage : node scripts/mvnw.mjs -pl api verify
import { spawnSync } from "node:child_process";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const racine = join(dirname(fileURLToPath(import.meta.url)), "..");
const windows = process.platform === "win32";
const commande = join(racine, windows ? "mvnw.cmd" : "mvnw");

const resultat = spawnSync(commande, process.argv.slice(2), {
  cwd: racine,
  stdio: "inherit",
  shell: windows,
});
process.exit(resultat.status ?? 1);
