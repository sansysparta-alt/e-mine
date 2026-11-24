import type { TonBadge } from "@emine/ui";

/**
 * Libellé et ton du statut d'une demande **selon le profil qui consulte** (correctifs ergonomie) :
 * le statut technique ne change pas, le libellé dit à chacun ce qui le concerne. Table unique,
 * utilisée partout (listes, fiches, tableaux de bord) ; libellés FR/EN dans
 * `statutsAffiches.demandeur.*` et `statutsAffiches.agent.*`.
 *
 * Code couleur commun : gris = rien à faire pour vous (ou en attente de l'autre partie), bleu = en
 * cours, orange = **action attendue de vous** (donnée par l'API : `aTraiterParMoi`), vert = abouti,
 * rouge = rejeté ou document révoqué. Un seul badge par ligne.
 */
export type PresentationStatut = { profil: "demandeur" | "agent"; cle: string; ton: TonBadge };

export type DemandeAffichee = {
  statut: string;
  fraisPayes: boolean;
  aTraiterParMoi: boolean;
  /** Document délivré puis révoqué : rouge partout, comme dans « Documents délivrés ». */
  documentRevoque?: boolean;
};

const INSTRUCTION = new Set(["recevable", "en_instruction", "avis_rendu"]);

function demandeur({ statut, fraisPayes, documentRevoque }: DemandeAffichee): [string, TonBadge] {
  if (statut === "brouillon") return ["aCompleter", "neutre"];
  if (statut === "deposee")
    return fraisPayes ? ["verification", "info"] : ["fraisAPayer", "avertissement"];
  if (statut === "complement_requis") return ["complementAFournir", "avertissement"];
  if (INSTRUCTION.has(statut)) return ["instruction", "info"];
  if (statut === "decision") return ["droitsAPayer", "avertissement"];
  if (statut === "droits_payes") return ["preparation", "info"];
  if (statut === "delivree")
    return documentRevoque ? ["documentRevoque", "danger"] : ["delivre", "succes"];
  if (statut === "rejetee") return ["rejetee", "danger"];
  return ["archivee", "neutre"];
}

function agent({
  statut,
  fraisPayes,
  aTraiterParMoi: moi,
  documentRevoque,
}: DemandeAffichee): [string, TonBadge] {
  if (statut === "deposee") {
    if (!fraisPayes) return ["fraisAttendus", "neutre"];
    return moi ? ["aExaminer", "avertissement"] : ["auGuichet", "info"];
  }
  // US-05.2 : sans réponse dans le délai, l'agent qui l'a demandé relance ou clôt (orange).
  if (statut === "complement_requis")
    return moi ? ["sansReponse", "avertissement"] : ["complementAttendu", "neutre"];
  if (statut === "recevable" || statut === "avis_rendu")
    return moi ? ["aDecider", "avertissement"] : ["attenteDecision", "info"];
  // US-05.3 : l'avis du profil connecté est attendu (orange), sinon l'instruction suit son cours.
  if (statut === "en_instruction")
    return moi ? ["avisARendre", "avertissement"] : ["instruction", "info"];
  if (statut === "decision") return ["droitsAttendus", "neutre"];
  if (statut === "droits_payes")
    return moi ? ["aSigner", "avertissement"] : ["attenteSignature", "info"];
  if (statut === "delivree")
    return documentRevoque ? ["documentRevoque", "danger"] : ["delivree", "succes"];
  if (statut === "rejetee") return ["rejetee", "danger"];
  return ["archivee", "neutre"];
}

/** Présentation du statut pour un profil externe (demandeur) ou interne (agent, décideur). */
export function presentationStatut(d: DemandeAffichee, interne: boolean): PresentationStatut {
  const [cle, ton] = interne ? agent(d) : demandeur(d);
  return { profil: interne ? "agent" : "demandeur", cle, ton };
}

/** Statuts où la main est au demandeur : payer les frais ou les droits, compléter. */
function chezLeDemandeur({ statut, fraisPayes }: DemandeAffichee): boolean {
  return (
    statut === "brouillon" ||
    (statut === "deposee" && !fraisPayes) ||
    statut === "complement_requis" ||
    statut === "decision"
  );
}

/**
 * Libellé du temps d'attente à l'étape, qui dit **qui attend** (retours ergonomie) : pour
 * l'usager, « Au ministère depuis… » ou « À vous d'agir depuis… » ; pour un agent, « Chez le
 * demandeur depuis… » ou « En attente depuis… ». Clés de `dates.*`.
 */
export function cleAttente(
  d: DemandeAffichee,
  interne: boolean,
): "attenteMinistere" | "attenteVous" | "attenteDemandeur" | "attente" {
  const usager = chezLeDemandeur(d);
  if (!interne) return usager ? "attenteVous" : "attenteMinistere";
  return usager ? "attenteDemandeur" : "attente";
}
