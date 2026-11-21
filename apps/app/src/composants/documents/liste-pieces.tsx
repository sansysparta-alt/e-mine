"use client";

import type { PieceDto } from "@emine/api-client";
import { formaterTaille } from "@emine/ui";
import { Eye } from "@emine/ui/icones";
import { useTranslations } from "next-intl";
import { documentDePiece, useVisualiseurDocument } from "./visualiseur";

/** Liste de pièces en lecture seule, chacune consultable dans le visualiseur (fiche de demande). */
export function ListePieces({ demandeId, pieces }: { demandeId: number; pieces: PieceDto[] }) {
  const t = useTranslations("visualiseur");
  const { ouvrir, visualiseur } = useVisualiseurDocument();
  return (
    <>
      <ul className="flex flex-col divide-y divide-bordure">
        {pieces.map((p) => (
          <li key={p.id} className="flex flex-wrap items-center justify-between gap-2 py-2">
            <span className="min-w-0">
              <span className="block font-semibold">{p.typePiece}</span>
              <span className="block text-sm break-all text-texte-doux">
                {p.nomFichier} · {formaterTaille(p.tailleOctets)}
              </span>
            </span>
            <button
              type="button"
              onClick={() => ouvrir(documentDePiece(demandeId, p))}
              className="inline-flex min-h-11 items-center gap-2 rounded-lg px-3 font-semibold text-royal hover:bg-ice"
            >
              <Eye aria-hidden="true" className="size-5" />
              {t("consulter")}
              <span className="sr-only">{p.nomFichier}</span>
            </button>
          </li>
        ))}
      </ul>
      {visualiseur}
    </>
  );
}
