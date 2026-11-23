import type { TonBadge } from "@emine/ui";

/** Statut technique d'une version de fiche (V18). */
export type StatutVersion = "a_valider" | "publiee" | "remplacee" | "refusee" | "retiree";

/** Action attendue du profil connecté sur une proposition (calculée par l'API). */
export type ActionFiche = "DECIDER" | "ATTENDRE" | "AUCUNE";

/** Badge d'une version : le libellé dit à chacun ce qui le concerne. */
export type PresentationVersion = { ton: TonBadge; cle: string };

/**
 * Présentation unique des versions de fiche (US-02.2), selon le profil :
 * - proposition en attente : orange « À approuver » pour le Ministre (action attendue de lui),
 *   gris « En attente du Ministre » pour l'administrateur (attente de l'autre partie) ;
 * - en vigueur : vert ; refusée : rouge ; remplacée ou retirée : gris.
 */
export function presentationVersion(statut: string, action: ActionFiche): PresentationVersion {
  switch (statut) {
    case "a_valider":
      return action === "DECIDER"
        ? { ton: "avertissement", cle: "aApprouver" }
        : { ton: "neutre", cle: "enAttenteMinistre" };
    case "publiee":
      return { ton: "succes", cle: "enVigueur" };
    case "refusee":
      return { ton: "danger", cle: "refusee" };
    case "retiree":
      return { ton: "neutre", cle: "retiree" };
    default:
      return { ton: "neutre", cle: "remplacee" };
  }
}

/**
 * Badge d'une ligne du catalogue (un seul) : la proposition en attente prime, puis le retrait du
 * catalogue, sinon « En vigueur ».
 */
export function presentationLigne(ligne: {
  actif: boolean;
  numeroProposee?: number | null;
  action: string;
}): PresentationVersion {
  if (ligne.numeroProposee != null) {
    return presentationVersion("a_valider", ligne.action as ActionFiche);
  }
  return ligne.actif ? { ton: "succes", cle: "enVigueur" } : { ton: "neutre", cle: "inactif" };
}

/** Filtres de la liste du catalogue (identiques à ceux de l'API). */
export const FILTRES_CATALOGUE = ["TOUS", "A_VALIDER", "INACTIFS"] as const;
export type FiltreCatalogue = (typeof FILTRES_CATALOGUE)[number];

export type ParametresCatalogue = {
  filtre: FiltreCatalogue;
  q?: string;
  page: number;
  taille: number;
};

/** Paramètres d'adresse de la liste, validés (valeurs inconnues ignorées). */
export function lireParametresCatalogue(
  brut: Record<string, string | string[] | undefined>,
): ParametresCatalogue {
  const un = (v: string | string[] | undefined) => (Array.isArray(v) ? v[0] : v);
  const filtre = un(brut["filtre"]);
  const q = un(brut["q"])?.trim().slice(0, 100);
  const page = Number.parseInt(un(brut["page"]) ?? "", 10);
  const taille = Number.parseInt(un(brut["taille"]) ?? "", 10);
  return {
    filtre: (FILTRES_CATALOGUE as readonly string[]).includes(filtre ?? "")
      ? (filtre as FiltreCatalogue)
      : "TOUS",
    q: q || undefined,
    page: Number.isFinite(page) && page > 0 ? page : 1,
    taille: [10, 20, 50, 100].includes(taille) ? taille : 20,
  };
}
