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
  recevable: "agentInstruire",
  en_instruction: "agentInstruire",
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

export function prochaineEtape(statut: string, profil: ProfilConsultation): ProchaineEtape | null {
  if (profil.interne) {
    if (!profil.permissions.includes("INSTRUIRE")) return null;
    const cle = ETAPES_AGENT[statut];
    return cle ? { cle, pour: "agent" } : null;
  }
  const cle = ETAPES_DEMANDEUR[statut];
  return cle ? { cle, pour: "demandeur" } : null;
}
