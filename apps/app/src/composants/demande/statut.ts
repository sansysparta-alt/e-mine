import type { TonBadge } from "@emine/ui";

/**
 * Ton du badge de chaque statut de demande, selon le code couleur commun du socle (le libellé vient
 * des traductions `statuts.*`) : gris = rien en cours, bleu = en cours, orange = action attendue
 * du demandeur, vert = abouti, rouge = rejeté.
 */
export const TON_PAR_STATUT: Record<string, TonBadge> = {
  brouillon: "neutre",
  deposee: "info",
  complement_requis: "avertissement",
  recevable: "info",
  en_instruction: "info",
  avis_rendu: "info",
  decision: "avertissement",
  droits_payes: "info",
  delivree: "succes",
  archivee: "neutre",
  rejetee: "danger",
};

export function tonStatut(statut: string): TonBadge {
  return TON_PAR_STATUT[statut] ?? "neutre";
}
