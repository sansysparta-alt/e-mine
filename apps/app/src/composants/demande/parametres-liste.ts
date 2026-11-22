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

/** Périodes des indicateurs du tableau de bord (clés de l'API). */
export const PERIODES_LISTE = ["mois", "30-jours", "annee"] as const;

export type Parametres = {
  groupe?: string;
  statut?: string;
  /** Filtres ouverts depuis une carte du tableau de bord. */
  enRetard?: string;
  traiteParMoi?: string;
  periode?: string;
  fraisPayes?: string;
  document?: string;
  q?: string;
  tri?: string;
  sens?: string;
  page?: string;
  taille?: string;
};

/** Paramètres d'adresse validés (toute valeur inconnue est ignorée). */
export function lireParametres(p: Parametres) {
  const statut = STATUTS.find((s) => s === p.statut);
  // Vue de la liste (« a-traiter », « en-cours »…), validée ensuite contre les vues du profil.
  const groupe = p.groupe && /^[a-z-]{1,20}$/.test(p.groupe) ? p.groupe : undefined;
  const tri = TRIS.find((s) => s === p.tri) ?? "date";
  const sens: SensTri =
    p.sens === "asc" || p.sens === "desc" ? p.sens : tri === "date" ? "desc" : "asc";
  const page = Math.max(1, Number.parseInt(p.page ?? "1", 10) || 1);
  const tailleDemandee = Number.parseInt(p.taille ?? "", 10);
  const taille = TAILLES.includes(tailleDemandee) ? tailleDemandee : 20;
  const q = p.q?.trim().slice(0, 100) || undefined;
  const enRetard = p.enRetard === "1" || p.enRetard === "true" ? "1" : undefined;
  const traiteParMoi = p.traiteParMoi === "1" || p.traiteParMoi === "true" ? "1" : undefined;
  const periode = PERIODES_LISTE.find((x) => x === p.periode);
  const fraisPayes = p.fraisPayes === "true" || p.fraisPayes === "false" ? p.fraisPayes : undefined;
  const document = p.document && /^[A-Z]{3}$/.test(p.document) ? p.document : undefined;
  return {
    groupe,
    statut,
    tri,
    sens,
    page,
    taille,
    q,
    enRetard,
    traiteParMoi,
    periode,
    fraisPayes,
    document,
  };
}
