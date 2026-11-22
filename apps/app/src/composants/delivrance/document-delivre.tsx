"use client";

import type { DocumentDelivreDto } from "@emine/api-client";
import { BadgeStatut, Bouton, type DocumentAffiche, formaterDateHeure } from "@emine/ui";
import { ExternalLink, Eye } from "@emine/ui/icones";
import { useTranslations } from "next-intl";
import type { ReactNode } from "react";
import { useVisualiseurDocument } from "../documents/visualiseur";

/** Adresses du document délivré par le relais BFF : affichage en ligne et téléchargement. */
export function documentDelivreAffiche(demandeId: number, numero: string): DocumentAffiche {
  const base = `/api/demandes/${demandeId}/document/contenu`;
  return {
    nomFichier: `${numero}.pdf`,
    typeMime: "application/pdf",
    urlAffichage: `${base}?inline=1`,
    urlTelechargement: base,
  };
}

/**
 * Document délivré (US-06.4) : numéro, date, signataire, validité (« Valide » ou « Non valide »
 * avec le motif), consultation dans le visualiseur (téléchargement depuis le panneau) et page
 * publique de vérification. `actions` accueille la révocation pour les profils autorisés.
 */
export function DocumentDelivre({
  demandeId,
  document,
  actions,
}: {
  demandeId: number;
  document: DocumentDelivreDto;
  actions?: ReactNode;
}) {
  const t = useTranslations("delivrance.document");
  const { ouvrir, visualiseur } = useVisualiseurDocument();
  return (
    <div className="flex flex-col gap-4">
      <dl className="grid grid-cols-1 gap-x-6 gap-y-2 sm:grid-cols-[auto_1fr]">
        <dt className="font-semibold">{t("numero")}</dt>
        <dd className="font-semibold">{document.numero}</dd>
        <dt className="font-semibold">{t("validite")}</dt>
        <dd>
          <BadgeStatut ton={document.valide ? "succes" : "danger"}>
            {document.valide ? t("valide") : t("nonValide")}
          </BadgeStatut>
        </dd>
        {!document.valide && document.motifInvalidation ? (
          <>
            <dt className="font-semibold">{t("motif")}</dt>
            <dd className="whitespace-pre-line">{document.motifInvalidation}</dd>
          </>
        ) : null}
        <dt className="font-semibold">{t("delivreLe")}</dt>
        <dd>{formaterDateHeure(document.delivreLe)}</dd>
        <dt className="font-semibold">{t("signePar")}</dt>
        <dd>{document.signataire}</dd>
        <dt className="font-semibold">{t("empreinte")}</dt>
        <dd className="text-sm break-all text-texte-doux" title={document.empreinteSha256}>
          {document.empreinteSha256.slice(0, 16)}…
        </dd>
      </dl>
      <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap">
        <Bouton
          type="button"
          onClick={() => ouvrir(documentDelivreAffiche(demandeId, document.numero))}
        >
          <Eye aria-hidden="true" className="size-4" />
          {t("voir")}
        </Bouton>
        <Bouton asChild variante="secondaire">
          <a href={document.urlVerification} target="_blank" rel="noopener noreferrer">
            <ExternalLink aria-hidden="true" className="size-4" />
            {t("verifier")}
            <span className="sr-only"> {t("nouvelOnglet")}</span>
          </a>
        </Bouton>
        {actions}
      </div>
      {visualiseur}
    </div>
  );
}
