/**
 * Espace du signataire (US-05.4, US-06.3) : les demandes dont il est le signataire au catalogue
 * (D4), en deux onglets — recevables à décider, droits payés à signer et délivrer — de la plus
 * ancienne à la plus récente.
 */
const TAILLES = [10, 20, 50, 100];
const NUMERO = /^DEM-\d{4}-\d{6}$/;
const NUMERO_DOCUMENT = /^[A-Z]{3}-\d{4}-\d{4,}$/;
const RESULTATS = ["decision", "droits_payes", "rejetee", "delivree"] as const;
export type ResultatDecision = (typeof RESULTATS)[number];

export const ONGLETS_SIGNATAIRE = ["a-decider", "a-signer"] as const;
export type OngletSignataire = (typeof ONGLETS_SIGNATAIRE)[number];

/** Statut des demandes de chaque onglet. */
export const STATUT_PAR_ONGLET: Record<OngletSignataire, string> = {
  "a-decider": "recevable",
  "a-signer": "droits_payes",
};

export type ParametresDecisions = {
  onglet?: string;
  q?: string;
  page?: string;
  taille?: string;
  traitee?: string;
  resultat?: string;
  document?: string;
};

/** Paramètres d'adresse validés (toute valeur inconnue est ignorée). */
export function lireParametresDecisions(p: ParametresDecisions) {
  const onglet: OngletSignataire = ONGLETS_SIGNATAIRE.find((o) => o === p.onglet) ?? "a-decider";
  const page = Math.max(1, Number.parseInt(p.page ?? "1", 10) || 1);
  const tailleDemandee = Number.parseInt(p.taille ?? "", 10);
  const taille = TAILLES.includes(tailleDemandee) ? tailleDemandee : 20;
  const q = p.q?.trim().slice(0, 100) || undefined;
  const resultat = RESULTATS.find((r) => r === p.resultat);
  /** Demande qui vient d'être traitée (message de confirmation en haut de l'espace). */
  const traitee =
    p.traitee && NUMERO.test(p.traitee) && resultat
      ? {
          numero: p.traitee,
          resultat,
          document: p.document && NUMERO_DOCUMENT.test(p.document) ? p.document : undefined,
        }
      : undefined;
  return { onglet, page, taille, q, traitee };
}
