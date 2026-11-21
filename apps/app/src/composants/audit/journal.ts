import { formaterDateHeure, formaterGnf } from "@emine/ui";

/**
 * Journal d'audit (US-11.7) : paramètres d'adresse de la liste et mise en forme des valeurs
 * enregistrées par la base (dates, montants, booléens, statuts) pour qu'un humain les lise.
 */
export const CATEGORIES = [
  "demandes",
  "paiements",
  "decisions",
  "pieces",
  "comptes",
  "catalogue",
] as const;
export type Categorie = (typeof CATEGORIES)[number];

const TAILLES = [10, 20, 50, 100];
const DATE = /^\d{4}-\d{2}-\d{2}$/;

export type ParametresJournal = {
  categorie?: string;
  q?: string;
  du?: string;
  au?: string;
  page?: string;
  taille?: string;
};

/** Paramètres validés ; `periodeInversee` si la fin précède le début (l'API n'est pas appelée). */
export function lireParametresJournal(p: ParametresJournal) {
  const categorie = CATEGORIES.find((c) => c === p.categorie);
  const q = p.q?.trim().slice(0, 100) || undefined;
  const du = p.du && DATE.test(p.du) ? p.du : undefined;
  const au = p.au && DATE.test(p.au) ? p.au : undefined;
  const page = Math.max(1, Number.parseInt(p.page ?? "1", 10) || 1);
  const tailleDemandee = Number.parseInt(p.taille ?? "", 10);
  const taille = TAILLES.includes(tailleDemandee) ? tailleDemandee : 20;
  const periodeInversee = du !== undefined && au !== undefined && du > au;
  return { categorie, q, du, au, page, taille, periodeInversee };
}

/** Traductions utiles à la mise en forme (fonction `t` de next-intl, réduite à l'essentiel). */
export type Traduire = {
  (cle: string): string;
  has(cle: string): boolean;
};

const HORODATAGE = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d+)?)?([+-]\d{2}:?\d{2}|Z)?$/;

/**
 * Valeur lisible : « — » si absente, Oui/Non, date et heure de Conakry, montant en GNF pour les
 * champs `montant…_gnf`, libellé du statut d'une demande ou d'un paiement ; sinon la valeur telle
 * qu'enregistrée.
 */
export function formaterValeur(
  table: string,
  champ: string,
  valeur: string | null,
  t: { statuts: Traduire; statutsPaiement: Traduire; oui: string; non: string },
): string {
  if (valeur == null || valeur === "") return "—";
  if (valeur === "true") return t.oui;
  if (valeur === "false") return t.non;
  if (champ === "statut") {
    const source =
      table === "paiement" ? t.statutsPaiement : table === "demande" ? t.statuts : null;
    if (source?.has(valeur)) return source(valeur);
  }
  if (/montant.*_gnf$/.test(champ) && /^\d+$/.test(valeur)) return formaterGnf(Number(valeur));
  if (HORODATAGE.test(valeur)) {
    const d = new Date(valeur);
    if (!Number.isNaN(d.getTime())) return formaterDateHeure(valeur);
  }
  return valeur;
}
