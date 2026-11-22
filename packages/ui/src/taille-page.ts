/**
 * Tailles de page acceptées par l'API (`shared.pagination` : 10, 20, 50 ou 100, VAL-001 sinon).
 * Une seule définition pour les écrans et la pagination.
 */
export const TAILLES_PAGE = [10, 20, 50, 100] as const;
const TAILLE_PAGE_MAX = 100;

/**
 * Plus petite taille de page autorisée qui contient `nombre` éléments : pour une courte liste
 * (5 dossiers d'un tableau de bord), on demande une page de 10 et on n'en montre que 5.
 */
export function tailleDePagePour(nombre: number): number {
  return TAILLES_PAGE.find((t) => t >= nombre) ?? TAILLE_PAGE_MAX;
}

/** Taille lue dans l'adresse si elle est autorisée, sinon `parDefaut`. */
export function tailleAutorisee(demandee: number, parDefaut = 20): number {
  return (TAILLES_PAGE as readonly number[]).includes(demandee) ? demandee : parDefaut;
}
