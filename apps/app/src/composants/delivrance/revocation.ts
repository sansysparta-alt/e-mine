/**
 * Profils autorisés à révoquer un document délivré (US-06.6) : indication d'interface, l'API fait
 * foi. Module sans « use client » : un composant serveur peut lire la valeur (une constante exportée
 * d'un module client n'est, côté serveur, qu'une référence).
 */
export const PROFILS_REVOCATION: readonly string[] = ["DIRECTEUR", "BRIGADE"];

export function peutRevoquer(profil: { interne: boolean; profil: { code: string } }): boolean {
  return profil.interne && PROFILS_REVOCATION.includes(profil.profil.code);
}
