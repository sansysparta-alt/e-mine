import { STATUTS } from "./parametres-liste";

/**
 * Filtres de la liste des demandes (règle du socle : filtres métier par profil). Depuis les
 * correctifs J5, les pastilles sont les **vues** calculées par l'API (exclusives, leur somme est
 * « Toutes ») : À traiter (« À faire » pour le demandeur), En attente du demandeur (agents), En
 * cours, Brouillons (demandeur), Terminées, Rejetées. Le détail par statut reste dans « Plus de
 * filtres ».
 */

/**
 * Statuts visibles par profil : un agent ne voit jamais les brouillons (ils restent privés au
 * demandeur jusqu'au dépôt ; l'API applique aussi la règle).
 */
export function statutsVisibles(interne: boolean): readonly string[] {
  return interne ? STATUTS.filter((s) => s !== "brouillon") : STATUTS;
}

/** Clé du libellé d'une vue : « À faire » pour le demandeur, « À traiter » pour l'agent. */
export function cleLibelleVue(cle: string, interne: boolean): string {
  return cle === "a-traiter" && !interne ? "a-faire" : cle;
}
