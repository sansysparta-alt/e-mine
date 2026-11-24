import { ECRANS, type EntreeConfig, type Profil, visible } from "./config";

/**
 * Règle d'accès aux écrans, déduite de la configuration unique de la navigation : une adresse
 * relève de l'écran (rubrique, action principale, notifications) dont elle est le plus long préfixe (« /workflows/EXPORT/modifier » →
 * « Workflows ») et n'est ouverte qu'aux profils qui voient cette entrée. Une adresse hors du menu
 * est laissée à l'écran lui-même (contrôles propres à la page, puis à l'API).
 */
export function entreeDuChemin(chemin: string, ecrans = ECRANS): EntreeConfig | undefined {
  return ecrans
    .filter((e) => chemin === e.href || chemin.startsWith(`${e.href}/`))
    .sort((a, b) => b.href.length - a.href.length)[0];
}

/** L'entrée impose-t-elle une restriction (droit, interne ou externe) ? */
export function estReservee(entree: EntreeConfig): boolean {
  return (
    entree.permission !== undefined ||
    entree.auMoinsUnDroit !== undefined ||
    entree.interne !== undefined
  );
}

/**
 * Le profil peut-il ouvrir cette adresse ? `true` pour une adresse hors du menu ou sans
 * restriction ; sinon, la même règle que l'affichage de l'entrée dans le menu.
 */
export function cheminAutorise(chemin: string, profil: Profil, ecrans = ECRANS): boolean {
  const entree = entreeDuChemin(chemin, ecrans);
  return !entree || !estReservee(entree) || visible(entree, profil);
}
