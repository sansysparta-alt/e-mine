"use client";

import { Bouton, Dialogue, formaterDate } from "@emine/ui";
import { Download } from "@emine/ui/icones";
import { useTranslations } from "next-intl";
import { useState } from "react";

const classesAction =
  "inline-flex min-h-11 items-center gap-1 rounded-lg px-2 text-sm font-semibold text-royal hover:bg-ice";

/**
 * Téléchargement du PDF signé d'un document délivré. Un document révoqué n'est pas modifiable (sa
 * signature serait rompue) : on avertit avant le téléchargement (date et motif) et le fichier
 * porte « REVOQUE » dans son nom.
 */
export function TelechargerDocument({
  demandeId,
  numero,
  revocation,
}: {
  demandeId: number;
  /** Numéro du document (nom du fichier) ; absent : le nom donné par l'API. */
  numero?: string;
  revocation?: { le?: string | null; motif?: string | null } | null;
}) {
  const t = useTranslations("mesDocuments.telechargement");
  const [ouvert, setOuvert] = useState(false);
  const url = `/api/demandes/${demandeId}/document/contenu`;
  const nom = numero ? `${numero}${revocation ? "-REVOQUE" : ""}.pdf` : true;
  if (!revocation) {
    return (
      <a href={url} download={nom} className={classesAction}>
        <Download aria-hidden="true" className="size-4" />
        {t("libelle")}
        {numero ? <span className="sr-only"> {numero}</span> : null}
      </a>
    );
  }
  return (
    <>
      <button type="button" onClick={() => setOuvert(true)} className={classesAction}>
        <Download aria-hidden="true" className="size-4" />
        {t("libelle")}
        {numero ? <span className="sr-only"> {numero}</span> : null}
      </button>
      <Dialogue
        ouvert={ouvert}
        titre={t("revoque.titre")}
        surFermeture={() => setOuvert(false)}
        actions={
          <>
            <Bouton type="button" variante="secondaire" onClick={() => setOuvert(false)}>
              {t("revoque.annuler")}
            </Bouton>
            <Bouton asChild>
              <a href={url} download={nom} onClick={() => setOuvert(false)}>
                <Download aria-hidden="true" className="size-4" />
                {t("revoque.confirmer")}
              </a>
            </Bouton>
          </>
        }
      >
        <div className="flex flex-col gap-3">
          <p>
            {revocation.le
              ? t("revoque.texteDate", { date: formaterDate(revocation.le) })
              : t("revoque.texte")}
          </p>
          {revocation.motif ? (
            <p>
              <span className="font-semibold">{t("revoque.motif")}</span>{" "}
              <span className="whitespace-pre-line">{revocation.motif}</span>
            </p>
          ) : null}
          <p className="text-sm text-texte-doux">{t("revoque.mention")}</p>
        </div>
      </Dialogue>
    </>
  );
}
