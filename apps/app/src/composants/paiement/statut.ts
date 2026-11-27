import type { TonBadge } from "@emine/ui";

/**
 * Ton du badge de chaque statut **technique** de paiement (journal d'audit, où le statut de la base
 * est affiché tel quel, libellé `statutsPaiement.*`).
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

/**
 * Profil qui consulte un paiement : l'usager qui paie, le Responsable fiscal (droit
 * « encaisser ») qui vérifie et rapproche, ou un autre agent (guichet, signataire) qui suit.
 */
export type ProfilPaiement = "usager" | "fiscal" | "agent";

export type PaiementAffiche = {
  statut: string;
  moyen: string;
  /** Remboursements du paiement (US-07.5) : un paiement rejeté dit si la somme a été rendue. */
  remboursements?: { statut: string }[] | null;
};

/** Ce qu'il est advenu de la somme reçue d'un paiement rejeté (US-07.5). */
export function etatRemboursementDuRejet(
  p: Pick<PaiementAffiche, "remboursements">,
): "effectue" | "enCours" | null {
  const r = p.remboursements ?? [];
  if (r.some((x) => x.statut === "execute")) return "effectue";
  if (r.some((x) => x.statut === "demande" || x.statut === "valide")) return "enCours";
  return null;
}

/**
 * Libellé et ton du statut d'un paiement **selon le profil qui consulte** (lot 5, même règle que
 * `presentationStatut` des demandes) : le statut technique ne change pas, le libellé dit à chacun
 * ce qui le concerne ; orange = action attendue **de vous**, gris = en attente de l'autre partie.
 * Libellés dans `statutsPaiementAffiches.*`.
 *
 * - Virement choisi, pas encore déclaré : l'usager doit l'effectuer (orange) ; les agents
 *   l'attendent (gris).
 * - Virement déclaré : l'usager attend (gris « En cours de vérification ») ; le fiscal l'attend
 *   sur son prochain relevé (bleu).
 * - Montant à vérifier : action du fiscal (orange) ; l'usager et les autres agents attendent.
 */
export function presentationStatutPaiement(
  p: PaiementAffiche,
  profil: ProfilPaiement,
): { cle: string; ton: TonBadge } {
  const virement = p.moyen === "VIREMENT";
  const usager = profil === "usager";
  switch (p.statut) {
    case "initie":
      if (virement)
        return usager
          ? { cle: "virementAEffectuer", ton: "avertissement" }
          : { cle: "virementAttendu", ton: "neutre" };
      return { cle: "enCours", ton: "info" };
    case "en_attente":
      if (virement)
        return profil === "fiscal"
          ? { cle: "attenduSurReleve", ton: "info" }
          : { cle: "enVerification", ton: "neutre" };
      return { cle: "attenteConfirmation", ton: "info" };
    case "a_verifier":
      return profil === "fiscal"
        ? { cle: "aVerifier", ton: "avertissement" }
        : { cle: "enVerification", ton: "neutre" };
    case "paye":
      return { cle: "paye", ton: "succes" };
    case "echoue":
      return { cle: "refuse", ton: "danger" };
    case "rejete": {
      // Un seul badge : « Rejeté · remboursé », « Rejeté · remboursement en cours » ou « Rejeté ».
      const remb = etatRemboursementDuRejet(p);
      return {
        cle:
          remb === "effectue"
            ? "rejeteRembourse"
            : remb === "enCours"
              ? "rejeteRemboursementEnCours"
              : "rejete",
        ton: "danger",
      };
    }
    case "expire":
      return { cle: "expire", ton: "neutre" };
    case "annule":
      return { cle: "annule", ton: "neutre" };
    case "remboursement_demande":
      return profil === "fiscal"
        ? { cle: "remboursementAValider", ton: "avertissement" }
        : { cle: "remboursementEnCours", ton: "neutre" };
    case "rembourse":
      return { cle: "rembourse", ton: "neutre" };
    default:
      return { cle: "enCours", ton: "neutre" };
  }
}

/** Profil de consultation d'un paiement, d'après le profil connecté. */
export function profilPaiement(moi: { interne: boolean; permissions: string[] }): ProfilPaiement {
  if (!moi.interne) return "usager";
  return moi.permissions.includes("ENCAISSER") ? "fiscal" : "agent";
}

/** Paiement en ligne encore en attente de la notification de TrésorPay. */
export function enAttenteDeConfirmation(p: PaiementAffiche): boolean {
  return p.moyen !== "VIREMENT" && (p.statut === "initie" || p.statut === "en_attente");
}

/**
 * Virement pas encore déclaré dont la date limite est passée (US-04.7) : la référence n'est plus
 * valable, même avant que la tâche planifiée ne la passe à « expiré ».
 */
export function referenceVirementExpiree(
  p: PaiementAffiche & { dateExpiration?: string | null },
  maintenant: Date = new Date(),
): boolean {
  return (
    p.moyen === "VIREMENT" &&
    p.statut === "initie" &&
    p.dateExpiration != null &&
    new Date(p.dateExpiration).getTime() <= maintenant.getTime()
  );
}

/** Issue après laquelle l'usager peut relancer un paiement. */
export function peutReessayer(statut: string): boolean {
  return statut === "echoue" || statut === "annule" || statut === "expire";
}
