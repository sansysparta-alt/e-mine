"use client";

import type { PieceDto } from "@emine/api-client";
import {
  classesLienEtire,
  classesLigneCliquable,
  cn,
  formaterDate,
  formaterTaille,
} from "@emine/ui";
import { Eye } from "@emine/ui/icones";
import { useTranslations } from "next-intl";
import { documentDePiece, useVisualiseurDocument } from "./visualiseur";

/**
 * Liste de pièces en lecture seule, chacune consultable dans le visualiseur (fiche de demande). Une
 * pièce remplacée en réponse à un complément (V14) n'est pas mêlée aux pièces en vigueur : elle
 * apparaît sous sa remplaçante, « Version remplacée le JJ/MM/AAAA », toujours consultable.
 */
export function ListePieces({ demandeId, pieces }: { demandeId: number; pieces: PieceDto[] }) {
  const t = useTranslations("visualiseur");
  const tPieces = useTranslations("detail.piecesVersions");
  const { ouvrir, visualiseur } = useVisualiseurDocument();
  const enVigueur = pieces.filter((p) => p.remplaceeLe == null);
  const anciennes = (id: number) => pieces.filter((p) => p.remplaceeParId === id);

  function boutonConsulter(p: PieceDto) {
    return (
      <button
        type="button"
        onClick={() => ouvrir(documentDePiece(demandeId, p))}
        className={cn(
          "inline-flex min-h-11 items-center gap-2 rounded-lg px-3 font-semibold text-royal",
          classesLienEtire,
        )}
      >
        <Eye aria-hidden="true" className="size-5" />
        {t("consulter")}
        <span className="sr-only">{p.nomFichier}</span>
      </button>
    );
  }

  return (
    <>
      <ul className="flex flex-col divide-y divide-bordure">
        {enVigueur.map((p) => {
          const versions = anciennes(p.id);
          return (
            <li key={p.id} className="flex flex-col">
              <div
                className={cn(
                  "flex flex-wrap items-center justify-between gap-2 px-2 py-2",
                  classesLigneCliquable,
                )}
              >
                <span className="min-w-0">
                  <span className="block font-semibold">{p.typePiece}</span>
                  <span className="block text-sm break-all text-texte-doux">
                    {p.nomFichier} · {formaterTaille(p.tailleOctets)}
                    {versions.length > 0
                      ? ` · ${tPieces("nouvelleVersion", { date: formaterDate(p.deposeLe) })}`
                      : ""}
                  </span>
                </span>
                {boutonConsulter(p)}
              </div>
              {versions.length > 0 ? (
                <ul aria-label={tPieces("anciennes", { piece: p.typePiece })} className="pb-2 pl-6">
                  {versions.map((v) => (
                    <li
                      key={v.id}
                      className={cn(
                        "flex flex-wrap items-center justify-between gap-2 border-l-2 border-bordure px-2 py-1",
                        classesLigneCliquable,
                      )}
                    >
                      <span className="min-w-0 text-sm text-texte-doux">
                        <span className="block">
                          {tPieces("remplacee", {
                            date: v.remplaceeLe ? formaterDate(v.remplaceeLe) : "",
                          })}
                        </span>
                        <span className="block break-all">
                          {v.nomFichier} · {formaterTaille(v.tailleOctets)}
                        </span>
                      </span>
                      {boutonConsulter(v)}
                    </li>
                  ))}
                </ul>
              ) : null}
            </li>
          );
        })}
      </ul>
      {visualiseur}
    </>
  );
}
