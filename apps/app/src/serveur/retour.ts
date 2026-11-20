/** Page affichée après la connexion quand aucune destination n'est demandée. */
export const PAGE_APRES_CONNEXION = "/tableau-de-bord";

/**
 * Destination après connexion, limitée aux chemins internes de l'application : empêche une
 * redirection ouverte vers un site tiers (« //site », « /\site », « https:… »).
 */
export function cheminDeRetourSur(valeur: string | null | undefined): string {
  if (!valeur || !valeur.startsWith("/") || valeur.startsWith("//") || valeur.startsWith("/\\")) {
    return PAGE_APRES_CONNEXION;
  }
  const controle = [...valeur].some((c) => c.charCodeAt(0) < 0x20 || c.charCodeAt(0) === 0x7f);
  if (controle || valeur.startsWith("/api/auth")) {
    return PAGE_APRES_CONNEXION;
  }
  return valeur;
}
