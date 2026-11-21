/** Espace insécable entre le montant et la devise. */
const INSECABLE = String.fromCharCode(0xa0);

/**
 * Montant en francs guinéens entiers (convention E-DEMANDES : `long` en GNF, jamais de décimales),
 * avec séparateur de milliers : 5 000 000 GNF.
 */
export function formaterGnf(montant: number, langue: string = "fr"): string {
  const nombre = new Intl.NumberFormat(langue === "en" ? "en-GB" : "fr-FR", {
    maximumFractionDigits: 0,
  }).format(montant);
  return `${nombre}${INSECABLE}GNF`;
}
