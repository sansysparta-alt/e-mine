"use client";

import { useSyncExternalStore } from "react";

/** Nombre de recherches récentes gardées par liste. */
const MAX_RECENTES = 5;
const EVENEMENT = "emine:recherches-recentes";

/**
 * Recherches récentes d'une liste (ou de la recherche globale), gardées dans ce navigateur :
 * simple commodité (stockage absent ou illisible = liste vide), jamais une donnée du service.
 */
export function lireRecentes(cle: string): string[] {
  try {
    const brut = JSON.parse(window.localStorage.getItem(cle) ?? "[]") as unknown;
    return Array.isArray(brut) ? brut.filter((x): x is string => typeof x === "string") : [];
  } catch {
    return [];
  }
}

/**
 * Ajoute une recherche en tête (5 au plus, majuscules ignorées). La saisie progressive ne crée pas
 * d'entrées en série : une recherche qui prolonge ou raccourcit une récente (« DEM » → « DEM-2026 »)
 * la remplace.
 */
export function memoriserRecente(cle: string, texte: string): string[] {
  const t = texte.trim();
  const recentes = lireRecentes(cle);
  if (t.length < 2) return recentes;
  const liste = [
    t,
    ...recentes.filter((r) => {
      const [x, y] = [r.toLowerCase(), t.toLowerCase()];
      return !x.startsWith(y) && !y.startsWith(x);
    }),
  ].slice(0, MAX_RECENTES);
  try {
    window.localStorage.setItem(cle, JSON.stringify(liste));
    window.dispatchEvent(new Event(EVENEMENT));
  } catch {
    // Stockage indisponible (navigation privée) : rien n'est gardé.
  }
  return liste;
}

function abonner(rappel: () => void) {
  window.addEventListener(EVENEMENT, rappel);
  window.addEventListener("storage", rappel);
  return () => {
    window.removeEventListener(EVENEMENT, rappel);
    window.removeEventListener("storage", rappel);
  };
}

/** Recherches récentes, relues quand elles changent ; vide au rendu serveur (pas d'écart d'hydratation). */
export function useRecentes(cle: string): string[] {
  const brut = useSyncExternalStore(
    abonner,
    () => {
      try {
        return window.localStorage.getItem(cle) ?? "[]";
      } catch {
        return "[]";
      }
    },
    () => "[]",
  );
  try {
    const liste = JSON.parse(brut) as unknown;
    return Array.isArray(liste) ? liste.filter((x): x is string => typeof x === "string") : [];
  } catch {
    return [];
  }
}

/** Dossier ouvert récemment, proposé par la recherche globale quand le champ est vide. */
export type DossierRecent = { id: number; numero: string | null; document: string };

const MAX_DOSSIERS = 5;

/** Clé des derniers dossiers ouverts : propre au compte (plusieurs comptes sur un navigateur). */
export const cleDossiersRecents = (compte: number) => `emine.dossiers-recents.${compte}`;

/** Derniers dossiers ouverts par ce compte dans ce navigateur (commodité locale). */
export function lireDossiersRecents(compte: number): DossierRecent[] {
  try {
    const brut = JSON.parse(window.localStorage.getItem(cleDossiersRecents(compte)) ?? "[]");
    return Array.isArray(brut)
      ? brut.filter(
          (x): x is DossierRecent =>
            typeof x === "object" && x !== null && typeof x.id === "number",
        )
      : [];
  } catch {
    return [];
  }
}

/** Met un dossier en tête des derniers ouverts (5 au plus, sans doublon). */
export function memoriserDossierRecent(compte: number, dossier: DossierRecent): void {
  const liste = [dossier, ...lireDossiersRecents(compte).filter((d) => d.id !== dossier.id)].slice(
    0,
    MAX_DOSSIERS,
  );
  try {
    window.localStorage.setItem(cleDossiersRecents(compte), JSON.stringify(liste));
  } catch {
    // Stockage indisponible : rien n'est gardé.
  }
}
