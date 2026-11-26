import { formaterDate, formaterDateHeure, formaterGnf, tailleAutorisee } from "@emine/ui";

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

const DATE = /^\d{4}-\d{2}-\d{2}$/;

/** Espace insécable (« 20 % »). */
const INSECABLE = " ";

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
  const taille = tailleAutorisee(tailleDemandee);
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
  t: {
    statuts: Traduire;
    statutsPaiement: Traduire;
    oui: string;
    non: string;
    /** Valeurs particulières (départ du délai). */
    valeurs?: Traduire;
    /** « 1 jour », « 60 jours ». */
    jours?: (n: number) => string;
  },
): string {
  if (valeur == null || valeur === "") return "—";
  const v = t.valeurs;
  if (t.jours && CHAMPS_JOURS.has(champ) && /^\d+$/.test(valeur)) return t.jours(Number(valeur));
  if (champ === "seuil_proche_pourcent" && /^\d+$/.test(valeur)) return `${valeur}${INSECABLE}%`;
  if (v && champ === "depart" && v.has(`depart.${valeur}`)) return v(`depart.${valeur}`);
  if (valeur === "true") return t.oui;
  if (valeur === "false") return t.non;
  if (champ === "statut") {
    const source =
      table === "paiement" ? t.statutsPaiement : table === "demande" ? t.statuts : null;
    if (source?.has(valeur)) return source(valeur);
  }
  if (/_gnf$/.test(champ) && /^\d+$/.test(valeur)) return formaterGnf(Number(valeur));
  if (DATE.test(valeur)) return formaterDate(valeur);
  if (HORODATAGE.test(valeur)) {
    const d = new Date(valeur);
    if (!Number.isNaN(d.getTime())) return formaterDateHeure(valeur);
  }
  return valeur;
}

/** Champs exprimés en jours (délais). */
const CHAMPS_JOURS = new Set([
  "delai_jours",
  "delai_min_jours",
  "delai_max_jours",
  "seuil_proche_min_jours",
]);

/** Champs techniques (identifiants, empreintes) : montrés en dernier, repliés dans la liste. */
export function estTechnique(champ: string): boolean {
  return /_id$|^id$|empreinte|mime_type|keycloak|taille_octets|^ordre$/.test(champ);
}

/** Rang d'affichage : le statut, puis l'essentiel métier, puis le reste, la technique en dernier. */
function rang(champ: string): number {
  if (champ === "statut") return 0;
  if (/motif|sens|delai|montant|_gnf$|actif|signe_par|sms|email|interne|depart/.test(champ)) {
    return 1;
  }
  return estTechnique(champ) ? 3 : 2;
}

/** Changements dans l'ordre utile à la lecture (essentiel d'abord), ordre d'origine sinon. */
export function trierChangements<T extends { champ: string }>(changements: T[]): T[] {
  return changements
    .map((c, i) => ({ c, i }))
    .sort((a, b) => rang(a.c.champ) - rang(b.c.champ) || a.i - b.i)
    .map((x) => x.c);
}

/**
 * Libellé d'un champ : sa traduction, sinon son nom rendu lisible (« regle_paiement » →
 * « Regle paiement ») plutôt que le nom technique brut.
 */
export function libelleChamp(champ: string, t: Traduire): string {
  if (t.has(`champs.${champ}`)) return t(`champs.${champ}`);
  const texte = champ.replace(/_/g, " ");
  return texte.charAt(0).toUpperCase() + texte.slice(1);
}

/**
 * Libellé de l'objet d'une entrée : celui de l'API dans la langue de l'écran (nom de l'événement,
 * fiche et version, numéro…), sinon le nom de l'objet unique (« Règles de calcul des délais »),
 * sinon son identifiant.
 */
export function libelleObjet(
  e: {
    table: string;
    objetId?: string | null;
    objetLibelle?: string | null;
    objetLibelleEn?: string | null;
  },
  anglais: boolean,
  t: Traduire,
): string {
  const libelle = anglais ? (e.objetLibelleEn ?? e.objetLibelle) : e.objetLibelle;
  if (libelle) return libelle;
  if (t.has(`objets.${e.table}`)) return t(`objets.${e.table}`);
  return e.objetId ? `#${e.objetId}` : "—";
}
