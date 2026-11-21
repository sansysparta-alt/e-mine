import type { TonBadge } from "@emine/ui";

/**
 * Ton du badge de chaque statut de paiement, selon le code couleur commun du socle (libellé dans
 * `statutsPaiement.*`) : bleu = en cours, vert = payé, orange = action ou contrôle attendu, rouge =
 * refusé, gris = sans suite.
 */
export const TON_PAR_STATUT_PAIEMENT: Record<string, TonBadge> = {
  initie: "info",
  en_attente: "info",
  paye: "succes",
  a_verifier: "avertissement",
  echoue: "danger",
  rejete: "danger",
  expire: "neutre",
  annule: "neutre",
  remboursement_demande: "avertissement",
  rembourse: "neutre",
};

export function tonStatutPaiement(statut: string): TonBadge {
  return TON_PAR_STATUT_PAIEMENT[statut] ?? "neutre";
}

/** Paiement encore en attente de la notification de TrésorPay. */
export function enAttenteDeConfirmation(statut: string): boolean {
  return statut === "initie" || statut === "en_attente";
}

/** Issue après laquelle l'usager peut relancer un paiement. */
export function peutReessayer(statut: string): boolean {
  return statut === "echoue" || statut === "annule" || statut === "expire";
}
