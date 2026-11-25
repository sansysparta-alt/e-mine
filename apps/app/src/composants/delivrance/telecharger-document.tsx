"use client";

import { formaterDate } from "@emine/ui";
import { Download, Eye } from "@emine/ui/icones";
import { useTranslations } from "next-intl";
import { useVisualiseurDocument } from "../documents/visualiseur";
import { documentDelivreAffiche } from "./document-delivre";

const classesAction =
  "inline-flex min-h-11 items-center gap-1 rounded-lg px-2 text-sm font-semibold text-royal hover:bg-ice";

/** Révocation d'un document : date, autorité (dans la langue de l'écran) et motif. */
export type Revocation = { le?: string | null; autorite?: string | null; motif?: string | null };

/**
 * Action d'un document délivré dans une liste. Valide : « Télécharger » le PDF signé. Révoqué :
 * « Voir » (correction 6 du lot 4) — il s'ouvre dans le visualiseur sous un avertissement (date,
 * autorité, motif) ; on ne propose pas d'emblée le téléchargement d'un document qui n'est plus
 * valide (le fichier, qui ne peut être modifié sans rompre sa signature, porte « REVOQUE »).
 */
export function TelechargerDocument({
  demandeId,
  numero,
  revocation,
}: {
  demandeId: number;
  /** Numéro du document (nom du fichier) ; absent : le nom donné par l'API. */
  numero?: string;
  revocation?: Revocation | null;
}) {
  const t = useTranslations("mesDocuments.telechargement");
  const { ouvrir, visualiseur } = useVisualiseurDocument();
  const url = `/api/demandes/${demandeId}/document/contenu`;
  if (!revocation) {
    return (
      <a href={url} download={numero ? `${numero}.pdf` : true} className={classesAction}>
        <Download aria-hidden="true" className="size-4" />
        {t("libelle")}
        {numero ? <span className="sr-only"> {numero}</span> : null}
      </a>
    );
  }
  const avertissement = revocation.le
    ? t("revoque.titreDate", { date: formaterDate(revocation.le) })
    : t("revoque.titre");
  const detail = [
    revocation.autorite ? t("revoque.autorite", { autorite: revocation.autorite }) : null,
    revocation.motif ? t("revoque.motif", { motif: revocation.motif }) : null,
    t("revoque.mention"),
  ]
    .filter(Boolean)
    .join("\n");
  return (
    <>
      <button
        type="button"
        onClick={() =>
          ouvrir({
            ...documentDelivreAffiche(demandeId, numero ?? "document", avertissement),
            avertissementDetail: detail,
          })
        }
        className={classesAction}
      >
        <Eye aria-hidden="true" className="size-4" />
        {t("voir")}
        {numero ? <span className="sr-only"> {numero}</span> : null}
      </button>
      {visualiseur}
    </>
  );
}
