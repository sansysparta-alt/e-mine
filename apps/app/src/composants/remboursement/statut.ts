import type { TonBadge } from "@emine/ui";

/**
 * Libellé et ton du statut d'un remboursement selon le profil (US-04.10), même règle que les
 * demandes et les paiements : orange = action attendue **du Responsable fiscal** (valider, puis
 * enregistrer l'ordre du Trésor) ; pour l'usager, un remboursement en cours n'attend rien de lui.
 * Libellés dans `remboursements.statuts.*`.
 */
export function presentationStatutRemboursement(
  statut: string,
  fiscal: boolean,
): { cle: string; ton: TonBadge } {
  switch (statut) {
    case "demande":
      return fiscal ? { cle: "aValider", ton: "avertissement" } : { cle: "enCours", ton: "info" };
    case "valide":
      return fiscal
        ? { cle: "ordreAEnregistrer", ton: "avertissement" }
        : { cle: "enCours", ton: "info" };
    case "execute":
      return { cle: "rembourse", ton: "succes" };
    case "refuse":
      return { cle: "refuse", ton: "danger" };
    default:
      return { cle: "enCours", ton: "neutre" };
  }
}

/** Filtres de la liste du Responsable fiscal, dans l'ordre du circuit. */
export const FILTRES_REMBOURSEMENTS = ["demande", "valide", "execute", "refuse"] as const;
