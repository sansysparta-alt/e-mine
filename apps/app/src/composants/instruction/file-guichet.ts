/**
 * File du guichet unique (US-05.1) : les demandes déposées, de la plus ancienne à la plus récente,
 * réparties en deux onglets selon que les frais de dossier sont payés (à examiner) ou non (frais
 * attendus). La recevabilité exige les frais payés (V8).
 */
export const ONGLETS_FILE = ["a-examiner", "frais-attendus"] as const;
export type OngletFile = (typeof ONGLETS_FILE)[number];

/** Filtre « frais payés » envoyé à l'API pour chaque onglet. */
export const FRAIS_PAYES_PAR_ONGLET: Record<OngletFile, boolean> = {
  "a-examiner": true,
  "frais-attendus": false,
};

const TAILLES = [10, 20, 50, 100];
const NUMERO = /^DEM-\d{4}-\d{6}$/;

export type ParametresFile = {
  onglet?: string;
  q?: string;
  page?: string;
  taille?: string;
  traitee?: string;
  resultat?: string;
};

/** Paramètres d'adresse validés (toute valeur inconnue est ignorée). */
export function lireParametresFile(p: ParametresFile) {
  const onglet: OngletFile = ONGLETS_FILE.find((o) => o === p.onglet) ?? "a-examiner";
  const page = Math.max(1, Number.parseInt(p.page ?? "1", 10) || 1);
  const tailleDemandee = Number.parseInt(p.taille ?? "", 10);
  const taille = TAILLES.includes(tailleDemandee) ? tailleDemandee : 20;
  const q = p.q?.trim().slice(0, 100) || undefined;
  /** Demande que l'agent vient de traiter (message de confirmation en haut de la file). */
  const traitee =
    p.traitee && NUMERO.test(p.traitee) && (p.resultat === "recevable" || p.resultat === "rejetee")
      ? { numero: p.traitee, resultat: p.resultat }
      : undefined;
  return { onglet, page, taille, q, traitee };
}

const JOUR_MS = 24 * 60 * 60 * 1000;

/** Nombre de jours entiers écoulés depuis le dépôt (0 le jour même). */
export function joursAttente(deposeLe: string, maintenant: Date = new Date()): number {
  const ecart = maintenant.getTime() - new Date(deposeLe).getTime();
  return Math.max(0, Math.floor(ecart / JOUR_MS));
}
