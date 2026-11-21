import { STATUTS } from "./parametres-liste";

/**
 * Filtres métier de la liste des demandes, selon le profil (règle du socle : les filtres proposés
 * dépendent du profil). Chaque groupe réunit des statuts de la base ; le détail par statut reste
 * accessible dans « Plus de filtres ».
 */
export type GroupeFiltre = { cle: string; statuts: readonly string[] };

/** Demandeur : où en sont mes demandes, et lesquelles attendent une action de ma part. */
export const GROUPES_DEMANDEUR: GroupeFiltre[] = [
  { cle: "brouillons", statuts: ["brouillon"] },
  {
    cle: "enCours",
    statuts: ["deposee", "recevable", "en_instruction", "avis_rendu", "droits_payes"],
  },
  { cle: "actionRequise", statuts: ["complement_requis", "decision"] },
  { cle: "terminees", statuts: ["delivree", "archivee", "rejetee"] },
];

/** Agent : ce qui est à traiter, ce qui attend le demandeur, ce qui avance, ce qui est clos. */
export const GROUPES_AGENT: GroupeFiltre[] = [
  { cle: "aTraiter", statuts: ["deposee"] },
  { cle: "attenteDemandeur", statuts: ["complement_requis", "decision"] },
  { cle: "enCours", statuts: ["recevable", "en_instruction", "avis_rendu", "droits_payes"] },
  { cle: "terminees", statuts: ["delivree", "archivee"] },
  { cle: "rejetees", statuts: ["rejetee"] },
];

/**
 * Statuts visibles par profil : un agent ne voit jamais les brouillons (ils restent privés au
 * demandeur jusqu'au dépôt).
 */
export function statutsVisibles(interne: boolean): readonly string[] {
  return interne ? STATUTS.filter((s) => s !== "brouillon") : STATUTS;
}

export function groupesPour(interne: boolean): GroupeFiltre[] {
  return interne ? GROUPES_AGENT : GROUPES_DEMANDEUR;
}

/**
 * Statuts à demander à l'API : statut détaillé s'il est choisi, sinon ceux du groupe, sinon tous
 * les statuts visibles par le profil.
 */
export function statutsFiltres(
  interne: boolean,
  groupe: string | undefined,
  statut: string | undefined,
): string[] {
  const visibles = statutsVisibles(interne);
  if (statut && visibles.includes(statut)) return [statut];
  const g = groupesPour(interne).find((x) => x.cle === groupe);
  return [...(g ? g.statuts : visibles)];
}

/** Nombre de demandes d'un ensemble de statuts, d'après les compteurs de l'API. */
export function compter(compteurs: Record<string, number>, statuts: readonly string[]): number {
  return statuts.reduce((total, s) => total + (compteurs[s] ?? 0), 0);
}
