import { existsSync, readdirSync, readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import fr from "../messages/fr.json";

/**
 * Contrôles statiques du socle, sur le code source : des erreurs que les tests de composants (jsdom)
 * ne voient pas, mais qui cassent une page à l'exécution.
 */
const SRC = resolve(__dirname);

function fichiers(dossier: string): string[] {
  return readdirSync(dossier, { recursive: true, encoding: "utf8" })
    .filter((f) => /\.tsx?$/.test(f) && !/\.test\.tsx?$/.test(f))
    .map((f) => join(dossier, f));
}

const SOURCES = fichiers(SRC).map((chemin) => ({ chemin, texte: readFileSync(chemin, "utf8") }));
const estClient = (texte: string) => /^\s*["']use client["']/.test(texte);
const relatif = (chemin: string) => chemin.slice(SRC.length + 1).replaceAll("\\", "/");

function existe(cle: string): boolean {
  let noeud: unknown = fr;
  for (const partie of cle.split(".")) {
    if (typeof noeud !== "object" || noeud === null || !(partie in noeud)) return false;
    noeud = (noeud as Record<string, unknown>)[partie];
  }
  return true;
}

describe("socle : contrôles du code source", () => {
  it("chaque texte appelé par une clé écrite en toutes lettres existe dans messages/fr.json", () => {
    const manquants: string[] = [];
    for (const { chemin, texte } of SOURCES) {
      // Un fichier peut déclarer plusieurs `t` (une par fonction) : chaque appel se rattache à la
      // déclaration la plus proche qui le précède.
      const declarations = [
        ...texte.matchAll(
          /const (\w+) = (?:await )?(?:useTranslations|getTranslations)\(\s*(?:"([^"]*)")?\s*\)/g,
        ),
      ].map((m) => ({ variable: m[1]!, espace: m[2] ?? "", position: m.index }));
      for (const variable of new Set(declarations.map((d) => d.variable))) {
        for (const m of texte.matchAll(new RegExp(`\\b${variable}\\(\\s*"([\\w.]+)"`, "g"))) {
          const portee = declarations
            .filter((d) => d.variable === variable && d.position < m.index)
            .at(-1);
          if (!portee) continue;
          const cle = portee.espace ? `${portee.espace}.${m[1]}` : m[1]!;
          // Une clé testée par `.has(…)` avant usage peut manquer volontairement.
          if (texte.includes(`${variable}.has("${m[1]}")`)) continue;
          if (!existe(cle)) manquants.push(`${relatif(chemin)} : ${cle}`);
        }
      }
    }
    expect(manquants).toEqual([]);
  });

  it("un module serveur n'appelle aucune fonction exportée par un module client", () => {
    const resoudre = (depuis: string, source: string): string | null => {
      const base = source.startsWith("@/")
        ? join(SRC, source.slice(2))
        : source.startsWith(".")
          ? resolve(dirname(depuis), source)
          : null;
      if (!base) return null;
      for (const suffixe of [".ts", ".tsx", "/index.ts", "/index.tsx"]) {
        if (existsSync(base + suffixe)) return base + suffixe;
      }
      return null;
    };
    const fautifs: string[] = [];
    for (const { chemin, texte } of SOURCES.filter((s) => !estClient(s.texte))) {
      for (const m of texte.matchAll(/import\s*\{([^}]*)\}\s*from\s*"([^"]+)"/g)) {
        const cible = resoudre(chemin, m[2]!);
        if (!cible || !estClient(readFileSync(cible, "utf8"))) continue;
        // Un composant (Majuscule) se rend, un type s'efface ; une fonction ou une constante
        // ordinaire n'est, côté serveur, qu'une référence client inutilisable.
        const valeurs = m[1]!
          .split(",")
          .map((n) => n.trim())
          .filter((n) => n && !n.startsWith("type ") && /^[a-z]/.test(n));
        for (const nom of valeurs) fautifs.push(`${relatif(chemin)} : ${nom} (${relatif(cible)})`);
      }
    }
    expect(fautifs).toEqual([]);
  });
});
