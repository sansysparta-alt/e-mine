import type { ValeursBrouillon } from "./schema";

/** Préfixe de toutes les données e-mine gardées dans le navigateur (effacées à la déconnexion). */
export const PREFIXE_STOCKAGE = "emine:";

/**
 * Clé de la saisie en cours d'un brouillon : propre à l'utilisateur (un autre compte ouvert dans
 * le même onglet ne la retrouve pas) et à la demande (« nouveau » avant le premier enregistrement).
 */
export function cleSaisie(utilisateur: string, demandeId: number | null): string {
  return `${PREFIXE_STOCKAGE}${utilisateur}:brouillon:${demandeId ?? "nouveau"}`;
}

function stockage(): Storage | null {
  try {
    return typeof window === "undefined" ? null : window.sessionStorage;
  } catch {
    return null;
  }
}

export function lireSaisie(cle: string): ValeursBrouillon | null {
  try {
    const brut = stockage()?.getItem(cle);
    if (!brut) return null;
    const v = JSON.parse(brut) as Partial<ValeursBrouillon>;
    if (typeof v.typeDocumentCode !== "string") return null;
    return {
      typeDocumentCode: v.typeDocumentCode,
      prefectureId: String(v.prefectureId ?? ""),
      substanceCode: String(v.substanceCode ?? ""),
      superficieHa: String(v.superficieHa ?? ""),
    };
  } catch {
    return null;
  }
}

export function ecrireSaisie(cle: string, valeurs: ValeursBrouillon): void {
  try {
    stockage()?.setItem(cle, JSON.stringify(valeurs));
  } catch {
    // Stockage plein ou refusé (navigation privée) : la sauvegarde serveur reste la référence.
  }
}

export function effacerSaisie(...cles: string[]): void {
  try {
    for (const c of cles) stockage()?.removeItem(c);
  } catch {
    // ignoré
  }
}

/** Efface toutes les données e-mine du navigateur (déconnexion volontaire, poste partagé). */
export function effacerToutesLesSaisies(): void {
  const s = stockage();
  if (!s) return;
  try {
    const cles = Array.from({ length: s.length }, (_, i) => s.key(i)).filter(
      (c): c is string => c !== null && c.startsWith(PREFIXE_STOCKAGE),
    );
    for (const c of cles) s.removeItem(c);
  } catch {
    // ignoré
  }
}

export function memesValeurs(a: ValeursBrouillon, b: ValeursBrouillon): boolean {
  return (
    a.typeDocumentCode === b.typeDocumentCode &&
    a.prefectureId === b.prefectureId &&
    a.substanceCode === b.substanceCode &&
    a.superficieHa === b.superficieHa
  );
}
