#!/usr/bin/env node
// Hook Claude Code (PostToolUse sur Edit|Write) — docs/05 §9.3
// Formate et linte le fichier que Claude vient de modifier. Sort avec le code 2 s'il reste des
// erreurs de lint : Claude les reçoit alors sur la sortie d'erreur et les corrige.
import { spawnSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import { extname, isAbsolute, join, relative, sep } from "node:path";

const racine = process.env.CLAUDE_PROJECT_DIR ?? process.cwd();

let fichier;
try {
  const entree = JSON.parse(readFileSync(0, "utf8"));
  fichier = entree?.tool_input?.file_path;
} catch {
  process.exit(0);
}
if (!fichier) process.exit(0);

const absolu = isAbsolute(fichier) ? fichier : join(racine, fichier);
const chemin = relative(racine, absolu).split(sep).join("/");

const ignores = [
  /^_client\//,
  /(^|\/)node_modules\//,
  /(^|\/)\.next\//,
  /(^|\/)target\//,
  /^packages\/api-client\/src\/generated\//,
  /^api\/src\/main\/generated\//,
  /^\.\./,
];
if (!existsSync(absolu) || ignores.some((r) => r.test(chemin))) process.exit(0);

const ext = extname(chemin).toLowerCase();
const prettier = join(racine, "node_modules", "prettier", "bin", "prettier.cjs");
const eslint = join(racine, "node_modules", "eslint", "bin", "eslint.js");

function lancer(commande, args) {
  return spawnSync(commande, args, { cwd: racine, encoding: "utf8", shell: false });
}

if ([".ts", ".tsx", ".js", ".mjs"].includes(ext)) {
  if (!existsSync(prettier) || !existsSync(eslint)) process.exit(0); // outils pas encore installés
  lancer(process.execPath, [prettier, "--write", "--log-level", "warn", absolu]);
  const r = lancer(process.execPath, [eslint, "--fix", absolu]);
  if (r.status !== 0) {
    process.stderr.write(`ESLint signale des erreurs dans ${chemin} :\n${r.stdout}${r.stderr}`);
    process.exit(2);
  }
} else if ([".json", ".css", ".yml", ".yaml"].includes(ext)) {
  if (!existsSync(prettier)) process.exit(0);
  lancer(process.execPath, [
    prettier,
    "--write",
    "--ignore-unknown",
    "--log-level",
    "warn",
    absolu,
  ]);
} else if (ext === ".java" && chemin.startsWith("api/")) {
  // Spotless (google-java-format) limité au fichier modifié, hors ligne pour aller plus vite.
  const windows = process.platform === "win32";
  const mvnw = join(racine, windows ? "mvnw.cmd" : "mvnw");
  const motif = `.*${chemin.split("/").pop().replace(/[.$]/g, "\\$&")}`;
  const r = spawnSync(
    mvnw,
    ["-q", "-o", "-pl", "api", "spotless:apply", `-DspotlessFiles=${motif}`],
    { cwd: racine, encoding: "utf8", shell: windows },
  );
  if (r.status !== 0 && /BUILD FAILURE|ERROR/.test(`${r.stdout}${r.stderr}`)) {
    process.stderr.write(`Spotless n'a pas pu formater ${chemin} :\n${r.stdout}${r.stderr}`);
    process.exit(2);
  }
}
process.exit(0);
