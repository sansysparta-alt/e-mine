"use client";

import type { PieceDto } from "@emine/api-client";
import { type DocumentAffiche, VisualiseurDocument } from "@emine/ui";
import { useTranslations } from "next-intl";
import { type ReactNode, useCallback, useState } from "react";

/** Adresses d'une pièce par le relais BFF : affichage en ligne et téléchargement. */
export function documentDePiece(demandeId: number, piece: PieceDto): DocumentAffiche {
  const base = `/api/demandes/${demandeId}/pieces/${piece.id}/contenu`;
  return {
    nomFichier: piece.nomFichier,
    typeMime: piece.typeMime,
    urlAffichage: `${base}?inline=1`,
    urlTelechargement: base,
  };
}

/**
 * Visualiseur de documents de l'application (socle D9) : à utiliser partout où l'on consulte une
 * pièce ou un document. Renvoie la fonction d'ouverture et l'élément à placer dans la page.
 */
export function useVisualiseurDocument(): {
  ouvrir: (document: DocumentAffiche) => void;
  visualiseur: ReactNode;
} {
  const t = useTranslations("visualiseur");
  const [document, setDocument] = useState<DocumentAffiche | null>(null);
  const fermer = useCallback(() => setDocument(null), []);
  return {
    ouvrir: setDocument,
    visualiseur: (
      <VisualiseurDocument
        document={document}
        surFermeture={fermer}
        libelles={{
          telecharger: t("telecharger"),
          imprimer: t("imprimer"),
          zoomAvant: t("zoomAvant"),
          zoomArriere: t("zoomArriere"),
          ajuster: t("ajuster"),
          pleinEcran: t("pleinEcran"),
          quitterPleinEcran: t("quitterPleinEcran"),
          fermer: t("fermer"),
          formatNonAffichable: t("formatNonAffichable"),
        }}
      />
    ),
  };
}
