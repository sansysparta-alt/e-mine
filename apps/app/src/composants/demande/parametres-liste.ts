import type { SensTri } from "@emine/ui";

/** Statuts proposés dans le filtre (cycle de vie de la base). */
export const STATUTS = [
  "brouillon",
  "deposee",
  "complement_requis",
  "recevable",
  "en_instruction",
  "avis_rendu",
  "decision",
  "droits_payes",
  "delivree",
  "archivee",
  "rejetee",
] as const;

export const TRIS = ["numero", "date", "statut"] as const;
const TAILLES = [10, 20, 50, 100];

export type Parametres = {
  groupe?: string;
  statut?: string;
  q?: string;
  tri?: string;
  sens?: string;
  page?: string;
  taille?: string;
};

/** Paramètres d'adresse validés (toute valeur inconnue est ignorée). */
export function lireParametres(p: Parametres) {
  const statut = STATUTS.find((s) => s === p.statut);
  const groupe = p.groupe && /^[a-zA-Z]{1,30}$/.test(p.groupe) ? p.groupe : undefined;
  const tri = TRIS.find((s) => s === p.tri) ?? "date";
  const sens: SensTri =
    p.sens === "asc" || p.sens === "desc" ? p.sens : tri === "date" ? "desc" : "asc";
  const page = Math.max(1, Number.parseInt(p.page ?? "1", 10) || 1);
  const tailleDemandee = Number.parseInt(p.taille ?? "", 10);
  const taille = TAILLES.includes(tailleDemandee) ? tailleDemandee : 20;
  const q = p.q?.trim().slice(0, 100) || undefined;
  return { groupe, statut, tri, sens, page, taille, q };
}
