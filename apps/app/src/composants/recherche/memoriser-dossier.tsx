"use client";

import { useEffect } from "react";
import { type DossierRecent, memoriserDossierRecent } from "./recentes";

/** Note l'ouverture d'un dossier (fiche d'une demande) pour la recherche globale (Ctrl+K). */
export function MemoriserDossier({ compte, ...dossier }: DossierRecent & { compte: number }) {
  const { id, numero, document } = dossier;
  useEffect(() => {
    memoriserDossierRecent(compte, { id, numero, document });
  }, [compte, id, numero, document]);
  return null;
}
