/**
 * Formats d'affichage communs (socle) : à utiliser dans tous les écrans, jamais de formatage local.
 * Français : virgule décimale, espace (insécable) entre milliers, dates JJ/MM/AAAA à l'heure de
 * Conakry. Anglais : point décimal, virgule entre milliers, dates DD/MM/YYYY.
 */

/** Fuseau d'affichage de toutes les dates (stockées en UTC). */
export const FUSEAU = "Africa/Conakry";

const INSECABLE = String.fromCharCode(0xa0);

function locale(langue: string): string {
  return langue === "en" ? "en-GB" : "fr-FR";
}

/** Nombre : 125,5 → « 125,5 » ; avec `decimales`, nombre exact de décimales (« 125,50 »). */
export function formaterNombre(valeur: number, langue = "fr", decimales?: number): string {
  return new Intl.NumberFormat(locale(langue), {
    minimumFractionDigits: decimales ?? 0,
    maximumFractionDigits: decimales ?? 2,
  }).format(valeur);
}

/**
 * Montant en francs guinéens entiers (convention E-DEMANDES : `long` en GNF, jamais de décimales),
 * avec séparateur de milliers : 5 000 000 GNF.
 */
export function formaterGnf(montant: number, langue = "fr"): string {
  return `${formaterNombre(Math.round(montant), langue, 0)}${INSECABLE}GNF`;
}

/** Superficie en hectares, toujours deux décimales : « 125,50 ha ». */
export function formaterSuperficie(hectares: number, langue = "fr"): string {
  return `${formaterNombre(hectares, langue, 2)}${INSECABLE}ha`;
}

function enDate(valeur: string | Date): Date {
  return typeof valeur === "string" ? new Date(valeur) : valeur;
}

/** Date JJ/MM/AAAA à l'heure de Conakry. */
export function formaterDate(valeur: string | Date, langue = "fr"): string {
  return new Intl.DateTimeFormat(locale(langue), {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    timeZone: FUSEAU,
  }).format(enDate(valeur));
}

/** Heure HH:MM à l'heure de Conakry. */
export function formaterHeure(valeur: string | Date, langue = "fr"): string {
  return new Intl.DateTimeFormat(locale(langue), {
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
    timeZone: FUSEAU,
  }).format(enDate(valeur));
}

/** Date et heure : « 20/11/2026 à 10:30 » (« 20/11/2026 at 10:30 » en anglais). */
export function formaterDateHeure(valeur: string | Date, langue = "fr"): string {
  const lien = langue === "en" ? "at" : "à";
  return `${formaterDate(valeur, langue)} ${lien} ${formaterHeure(valeur, langue)}`;
}

/** Taille de fichier : « 850 o », « 12 Ko », « 2,4 Mo ». */
export function formaterTaille(octets: number, langue = "fr"): string {
  const [o, k, m] = langue === "en" ? ["B", "KB", "MB"] : ["o", "Ko", "Mo"];
  if (octets < 1024) return `${octets}${INSECABLE}${o}`;
  if (octets < 1024 * 1024)
    return `${formaterNombre(Math.round(octets / 1024), langue)}${INSECABLE}${k}`;
  return `${formaterNombre(octets / (1024 * 1024), langue, 1)}${INSECABLE}${m}`;
}

/** Formes d'un nom compté : « demande » / « demandes ». */
export type FormesAccord = { un: string; plusieurs: string };

/**
 * Accord d'un nom avec un nombre (socle, US-11.8) : règle de la langue (français : 0 et 1 au
 * singulier, « 1 élément », « 0 demande » ; anglais : 1 seul au singulier). Toute quantité
 * affichée avec un nom passe par cette fonction ou par un message ICU `plural`.
 */
export function accorder(nombre: number, formes: FormesAccord, langue = "fr"): string {
  return new Intl.PluralRules(locale(langue)).select(nombre) === "one"
    ? formes.un
    : formes.plusieurs;
}

/** Références (`DEM-…`, `PAY-…`, `RMB-…`) : jamais coupées sur deux lignes, chiffres alignés. */
export const classesReference = "whitespace-nowrap tabular-nums";

/** Date du jour (année, mois, jour) à l'heure de Conakry. */
function jourConakry(valeur: string | Date): number {
  const parties = new Intl.DateTimeFormat("en-CA", {
    timeZone: FUSEAU,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(typeof valeur === "string" ? new Date(valeur) : valeur);
  const v = (type: string) => Number(parties.find((p) => p.type === type)?.value);
  return Date.UTC(v("year"), v("month") - 1, v("day"));
}

/**
 * Date du jour à l'heure de Conakry au format ISO `AAAA-MM-JJ` (borne d'un champ de date, date
 * d'effet d'un tarif).
 */
export function dateDuJour(maintenant: Date = new Date()): string {
  return new Date(jourConakry(maintenant)).toISOString().slice(0, 10);
}

/**
 * Date (ISO `AAAA-MM-JJ`, heure de Conakry) dans `jours` jours calendaires : échéance d'un délai
 * à partir des jours restants donnés par l'API (« réponse attendue d'ici le… »).
 */
export function dateDansJours(jours: number, maintenant: Date = new Date()): string {
  return new Date(jourConakry(maintenant) + jours * 86_400_000).toISOString().slice(0, 10);
}

/**
 * Nombre de **jours calendaires** écoulés depuis une date, à l'heure de Conakry (0 = le jour même,
 * 1 = la veille). Base des temps d'attente : « En attente depuis 1 jour » pour une demande déposée
 * hier soir, même moins de 24 heures plus tard.
 */
export function joursCalendairesDepuis(
  depuis: string | Date,
  maintenant: Date = new Date(),
): number {
  return Math.max(0, Math.round((jourConakry(maintenant) - jourConakry(depuis)) / 86_400_000));
}
