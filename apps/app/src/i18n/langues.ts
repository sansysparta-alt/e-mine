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

/** Le cookie de la langue dure un an : c'est la langue de ce navigateur. */
export const OPTIONS_COOKIE_LANGUE = {
  path: "/",
  maxAge: 365 * 24 * 60 * 60,
  sameSite: "lax" as const,
  httpOnly: true,
};

/**
 * Langue préférée du navigateur (en-tête Accept-Language) parmi celles de l'interface, au premier
 * accès ; le français sinon.
 */
export function langueDuNavigateur(entete: string | null | undefined): Langue {
  const preferences = (entete ?? "")
    .split(",")
    .map((p) => {
      const [code, ...params] = p.trim().split(";");
      const q = params.find((x) => x.trim().startsWith("q="));
      return {
        code: (code ?? "").toLowerCase().split("-")[0] ?? "",
        q: q ? Number(q.split("=")[1]) : 1,
      };
    })
    .filter((p) => p.code && !Number.isNaN(p.q))
    .sort((a, b) => b.q - a.q);
  const trouvee = preferences.find((p) => (LANGUES as readonly string[]).includes(p.code));
  return trouvee ? (trouvee.code as Langue) : LANGUE_PAR_DEFAUT;
}
