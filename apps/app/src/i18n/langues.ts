/**
 * Langues de l'interface. Cahier des charges : « Bilingue FR / EN : chaque page et chaque
 * e-Service existent dans les deux langues » ; le français, langue officielle, par défaut.
 */
export const LANGUES = ["fr", "en"] as const;
export type Langue = (typeof LANGUES)[number];

export const LANGUE_PAR_DEFAUT: Langue = "fr";

/** Cookie de la langue choisie (lu par next-intl côté serveur, transmis à Keycloak). */
export const COOKIE_LANGUE = "NEXT_LOCALE";

/** Langue reconnue, sinon la langue par défaut. */
export function langueValide(valeur: string | null | undefined): Langue {
  return (LANGUES as readonly string[]).includes(valeur ?? "")
    ? (valeur as Langue)
    : LANGUE_PAR_DEFAUT;
}
