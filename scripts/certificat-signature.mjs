#!/usr/bin/env node
// Génère le certificat de signature de DÉMONSTRATION (US-06.2) : auto-signé, sans valeur légale,
// au format PKCS#12, avec le keytool du JDK (aucune dépendance). Chemin et mot de passe viennent du
// fichier .env (SIGNATURE_PKCS12_PATH, SIGNATURE_PKCS12_PASSWORD) ; le mot de passe n'est jamais
// affiché. Le fichier produit est exclu de Git (*.p12).
// Usage : pnpm signature:certificat          (ne remplace pas un certificat existant)
//         pnpm signature:certificat --force  (le régénère)
import { spawnSync } from "node:child_process";
import { existsSync, mkdirSync, readFileSync, rmSync } from "node:fs";
import { dirname, isAbsolute, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const racine = join(dirname(fileURLToPath(import.meta.url)), "..");

/** Variables du fichier .env (format CLE=valeur, commentaires # ignorés). */
function lireEnv() {
  const fichier = join(racine, ".env");
  if (!existsSync(fichier)) {
    console.error("Fichier .env introuvable : copiez .env.example en .env et complétez-le.");
    process.exit(1);
  }
  const valeurs = {};
  for (const ligne of readFileSync(fichier, "utf8").split(/\r?\n/)) {
    const m = /^\s*([A-Z0-9_]+)\s*=\s*(.*)\s*$/.exec(ligne);
    if (m) valeurs[m[1]] = m[2].replace(/^["']|["']$/g, "");
  }
  return valeurs;
}

const env = lireEnv();
const chemin = env.SIGNATURE_PKCS12_PATH || "./infra/signature/certificat-test.p12";
const motDePasse = env.SIGNATURE_PKCS12_PASSWORD;
if (!motDePasse || motDePasse.length < 6) {
  console.error("SIGNATURE_PKCS12_PASSWORD doit être renseigné dans .env (6 caractères au moins).");
  process.exit(1);
}
const cible = isAbsolute(chemin) ? chemin : resolve(racine, chemin);
const forcer = process.argv.includes("--force");
if (existsSync(cible)) {
  if (!forcer) {
    console.log(`Certificat déjà présent : ${chemin} (--force pour le régénérer).`);
    process.exit(0);
  }
  rmSync(cible);
}
mkdirSync(dirname(cible), { recursive: true });

const javaHome = process.env.JAVA_HOME;
const keytool = javaHome
  ? join(javaHome, "bin", process.platform === "win32" ? "keytool.exe" : "keytool")
  : "keytool";
const r = spawnSync(
  keytool,
  [
    "-genkeypair",
    "-alias",
    "emine-demo",
    "-keyalg",
    "RSA",
    "-keysize",
    "3072",
    "-sigalg",
    "SHA256withRSA",
    "-validity",
    "825",
    "-dname",
    "CN=e-mine DEMO - sans valeur legale, O=Plateforme e-mine (demonstration), C=GN",
    "-ext",
    "KU=digitalSignature,nonRepudiation",
    "-storetype",
    "PKCS12",
    "-keystore",
    cible,
    "-storepass:env",
    "EMINE_P12",
    "-keypass:env",
    "EMINE_P12",
  ],
  { stdio: ["ignore", "inherit", "inherit"], env: { ...process.env, EMINE_P12: motDePasse } },
);
if (r.status !== 0) {
  console.error(
    "keytool a échoué. Vérifiez que le JDK 25 est installé (JAVA_HOME) puis relancez la commande.",
  );
  process.exit(r.status ?? 1);
}
console.log(`Certificat de démonstration créé : ${chemin} (exclu de Git, sans valeur légale).`);
