#!/usr/bin/env node
// Lance le wrapper Maven du dépôt quel que soit le système (mvnw.cmd sous Windows, mvnw ailleurs).
// Usage : node scripts/mvnw.mjs -pl api verify
import { spawnSync } from "node:child_process";
import { existsSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const racine = join(dirname(fileURLToPath(import.meta.url)), "..");
const windows = process.platform === "win32";
const commande = join(racine, windows ? "mvnw.cmd" : "mvnw");

/**
 * Sous Windows, un terminal ouvert avant l'installation du JDK (VS Code lancé avant, par exemple)
 * n'a pas JAVA_HOME : on le relit dans les variables système puis utilisateur de Windows.
 */
function javaHomeWindows() {
  for (const cle of [
    "HKLM\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment",
    "HKCU\\Environment",
  ]) {
    const r = spawnSync("reg", ["query", cle, "/v", "JAVA_HOME"], { encoding: "utf8" });
    const valeur = r.stdout?.match(/JAVA_HOME\s+REG_(?:EXPAND_)?SZ\s+(.+)/)?.[1]?.trim();
    if (valeur && existsSync(join(valeur, "bin", "java.exe"))) {
      return valeur.replace(/\\$/, "");
    }
  }
  return undefined;
}

const env = { ...process.env };
if (windows && !env.JAVA_HOME) {
  const trouve = javaHomeWindows();
  if (trouve) {
    env.JAVA_HOME = trouve;
  } else {
    console.error(
      "JDK introuvable : installer Temurin 25 (winget install EclipseAdoptium.Temurin.25.JDK), " +
        "puis fermer et rouvrir VS Code.",
    );
    process.exit(1);
  }
}

const resultat = spawnSync(commande, process.argv.slice(2), {
  cwd: racine,
  stdio: "inherit",
  shell: windows,
  env,
});
process.exit(resultat.status ?? 1);
