import type { TonBadge } from "@emine/ui";

/**
 * Libellé et ton du statut d'une demande **selon le profil qui consulte** (correctifs ergonomie) :
 * le statut technique ne change pas, le libellé dit à chacun ce qui le concerne. Table unique,
 * utilisée partout (listes, fiches, tableaux de bord) ; libellés FR/EN dans
 * `statutsAffiches.demandeur.*` et `statutsAffiches.agent.*`.
 *
 * Code couleur commun : gris = rien à faire pour vous (ou en attente de l'autre partie), bleu = en
 * cours, orange = **action attendue de vous** (donnée par l'API : `aTraiterParMoi`), vert = abouti,
 * rouge = rejeté. Un seul badge par ligne.
 */
export type PresentationStatut = { profil: "demandeur" | "agent"; cle: string; ton: TonBadge };

export type DemandeAffichee = { statut: string; fraisPayes: boolean; aTraiterParMoi: boolean };

const INSTRUCTION = new Set(["recevable", "en_instruction", "avis_rendu"]);

function demandeur({ statut, fraisPayes }: DemandeAffichee): [string, TonBadge] {
  if (statut === "brouillon") return ["aCompleter", "neutre"];
  if (statut === "deposee")
    return fraisPayes ? ["verification", "info"] : ["fraisAPayer", "avertissement"];
  if (statut === "complement_requis") return ["complementAFournir", "avertissement"];
  if (INSTRUCTION.has(statut)) return ["instruction", "info"];
  if (statut === "decision") return ["droitsAPayer", "avertissement"];
  if (statut === "droits_payes") return ["preparation", "info"];
  if (statut === "delivree") return ["delivre", "succes"];
  if (statut === "rejetee") return ["rejetee", "danger"];
  return ["archivee", "neutre"];
}

function agent({ statut, fraisPayes, aTraiterParMoi: moi }: DemandeAffichee): [string, TonBadge] {
  if (statut === "deposee") {
    if (!fraisPayes) return ["fraisAttendus", "neutre"];
    return moi ? ["aExaminer", "avertissement"] : ["auGuichet", "info"];
  }
  if (statut === "complement_requis") return ["complementAttendu", "neutre"];
  if (statut === "recevable")
    return moi ? ["aDecider", "avertissement"] : ["attenteDecision", "info"];
  if (statut === "en_instruction" || statut === "avis_rendu") return ["instruction", "info"];
  if (statut === "decision") return ["droitsAttendus", "neutre"];
  if (statut === "droits_payes")
    return moi ? ["aSigner", "avertissement"] : ["attenteSignature", "info"];
  if (statut === "delivree") return ["delivree", "succes"];
  if (statut === "rejetee") return ["rejetee", "danger"];
  return ["archivee", "neutre"];
}

/** Présentation du statut pour un profil externe (demandeur) ou interne (agent, décideur). */
export function presentationStatut(d: DemandeAffichee, interne: boolean): PresentationStatut {
  const [cle, ton] = interne ? agent(d) : demandeur(d);
  return { profil: interne ? "agent" : "demandeur", cle, ton };
}
