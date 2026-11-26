/**
 * Prochaine étape d'une demande, selon le profil de la personne qui la consulte et le statut
 * (correctifs J2) : l'agent qui instruit voit l'action attendue de lui, le demandeur ce qu'il doit
 * faire ou attendre. Renvoie la clé de traduction (`detail.etape.<cle>`) ou `null` (aucun bloc).
 */
export type ProfilConsultation = { interne: boolean; permissions: string[] };

export type ProchaineEtape = {
  cle: string;
  /** Destinataire du message : l'agent (action à mener) ou le demandeur. */
  pour: "agent" | "demandeur";
};

const ETAPES_AGENT: Record<string, string> = {
  deposee: "agentVerifier",
  complement_requis: "agentAttenteComplement",
  recevable: "agentAttenteDecision",
  en_instruction: "agentAttenteAvis",
  avis_rendu: "agentDecision",
  decision: "agentAttenteDroits",
  droits_payes: "agentDelivrer",
};

const ETAPES_DEMANDEUR: Record<string, string> = {
  deposee: "demandeurPayerFrais",
  complement_requis: "demandeurCompleter",
  recevable: "demandeurInstruction",
  en_instruction: "demandeurInstruction",
  avis_rendu: "demandeurInstruction",
  decision: "demandeurPayerDroits",
  droits_payes: "demandeurDelivrance",
  delivree: "demandeurDelivree",
  rejetee: "demandeurRejetee",
};

/**
 * État de la demande utile au calcul (J3) : frais de dossier payés ou non ; paiement attendu en
 * cours de vérification (virement déclaré, US-04.7).
 */
export type EtatDemande = { fraisPayes?: boolean; paiementEnVerification?: boolean };

export function prochaineEtape(
  statut: string,
  profil: ProfilConsultation,
  etat: EtatDemande = {},
): ProchaineEtape | null {
  const fraisPayes = etat.fraisPayes === true;
  const enVerification =
    etat.paiementEnVerification === true &&
    ((statut === "deposee" && !fraisPayes) || statut === "decision");
  if (profil.interne) {
    if (!profil.permissions.includes("INSTRUIRE")) return null;
    if (enVerification)
      return {
        cle: statut === "decision" ? "agentDroitsVerification" : "agentPaiementVerification",
        pour: "agent",
      };
    // V8 : la recevabilité exige les frais payés ; l'agent sait s'il peut déjà la déclarer.
    if (statut === "deposee" && !fraisPayes) return { cle: "agentFraisAttendus", pour: "agent" };
    const cle = ETAPES_AGENT[statut];
    return cle ? { cle, pour: "agent" } : null;
  }
  if (enVerification) return { cle: "demandeurPaiementVerification", pour: "demandeur" };
  if (statut === "deposee" && fraisPayes)
    return { cle: "demandeurAttenteGuichet", pour: "demandeur" };
  const cle = ETAPES_DEMANDEUR[statut];
  return cle ? { cle, pour: "demandeur" } : null;
}
