import type { TonBadge } from "@emine/ui";

/**
 * Qui consulte un remboursement (US-04.18, décision V1-13) : le Directeur valide ou refuse les
 * propositions ; le Responsable fiscal propose, puis enregistre l'ordre du Trésor ; l'usager suit
 * son remboursement.
 */
export type ProfilRemboursement = "directeur" | "fiscal" | "usager";

/** Profil d'après les droits : le droit de valider prime (Directeur). */
export function profilRemboursement(permissions: string[]): ProfilRemboursement {
  if (permissions.includes("VALIDER_REMBOURSEMENT")) return "directeur";
  if (permissions.includes("ENCAISSER")) return "fiscal";
  return "usager";
}

/**
 * Libellé et ton du statut d'un remboursement selon le profil, même règle que les demandes et les
 * paiements : le statut technique ne change pas, le libellé dit à chacun ce qui le concerne ;
 * l'orange suit `aTraiterParMoi`, calculé par l'API. Libellés dans `remboursements.statuts.*`.
 */
export function presentationStatutRemboursement(
  statut: string,
  profil: ProfilRemboursement,
  aTraiterParMoi = false,
): { cle: string; ton: TonBadge } {
  const aMoi = (cle: string, sinon: string) =>
    aTraiterParMoi
      ? { cle, ton: "avertissement" as const }
      : { cle: sinon, ton: "neutre" as const };
  switch (statut) {
    case "demande":
      if (profil === "directeur") return aMoi("aValider", "proposeParVous");
      if (profil === "fiscal") return { cle: "attenteDirecteur", ton: "neutre" };
      return { cle: "enCours", ton: "info" };
    case "valide":
      if (profil === "fiscal") return aMoi("ordreAEnregistrer", "ordreAEnregistrer");
      if (profil === "directeur") return { cle: "ordreAttendu", ton: "neutre" };
      return { cle: "enCours", ton: "info" };
    case "execute":
      return { cle: "rembourse", ton: "succes" };
    case "refuse":
      return { cle: "refuse", ton: "danger" };
    default:
      return { cle: "enCours", ton: "neutre" };
  }
}

/** Filtres de la liste, dans l'ordre du circuit (libellés propres à chaque profil). */
export const FILTRES_REMBOURSEMENTS = ["demande", "valide", "execute", "refuse"] as const;
