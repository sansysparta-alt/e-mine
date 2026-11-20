#!/usr/bin/env node
// Injecte les fichiers db/seed/*.sql (ordre alphabétique) dans la base de dev, via le conteneur PostgreSQL.
// Usage : pnpm db:seed   (prérequis : pnpm infra:up, puis un premier démarrage de l'API pour les migrations)
import { spawnSync } from "node:child_process";
import { readdirSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const racine = join(dirname(fileURLToPath(import.meta.url)), "..");
const dossier = join(racine, "db", "seed");
const fichiers = readdirSync(dossier).filter((f) => f.endsWith(".sql")).sort();

for (const fichier of fichiers) {
  process.stdout.write(`seed : ${fichier}… `);
  const r = spawnSync(
    "docker",
    ["compose", "exec", "-T", "postgres", "psql", "-q", "-v", "ON_ERROR_STOP=1", "-U", "postgres", "-d", "emine"],
    { cwd: racine, input: readFileSync(join(dossier, fichier)), stdio: ["pipe", "inherit", "inherit"] },
  );
  if (r.status !== 0) {
    console.error(`\nÉchec du seed ${fichier}. La base est-elle démarrée (pnpm infra:up) et migrée (pnpm api:dev) ?`);
    process.exit(r.status ?? 1);
  }
  console.log("ok");
}
