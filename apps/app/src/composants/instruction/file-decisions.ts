/**
 * File des décisions du signataire (US-05.4) : les demandes recevables dont il est le signataire
 * au catalogue (D4), de la plus ancienne à la plus récente.
 */
const TAILLES = [10, 20, 50, 100];
const NUMERO = /^DEM-\d{4}-\d{6}$/;
const RESULTATS = ["decision", "droits_payes", "rejetee"] as const;
export type ResultatDecision = (typeof RESULTATS)[number];

export type ParametresDecisions = {
  q?: string;
  page?: string;
  taille?: string;
  traitee?: string;
  resultat?: string;
};

/** Paramètres d'adresse validés (toute valeur inconnue est ignorée). */
export function lireParametresDecisions(p: ParametresDecisions) {
  const page = Math.max(1, Number.parseInt(p.page ?? "1", 10) || 1);
  const tailleDemandee = Number.parseInt(p.taille ?? "", 10);
  const taille = TAILLES.includes(tailleDemandee) ? tailleDemandee : 20;
  const q = p.q?.trim().slice(0, 100) || undefined;
  const resultat = RESULTATS.find((r) => r === p.resultat);
  /** Demande qui vient d'être décidée (message de confirmation en haut de la file). */
  const traitee =
    p.traitee && NUMERO.test(p.traitee) && resultat ? { numero: p.traitee, resultat } : undefined;
  return { page, taille, q, traitee };
}
