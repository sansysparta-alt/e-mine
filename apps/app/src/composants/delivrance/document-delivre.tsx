"use client";

import type { DocumentDelivreDto } from "@emine/api-client";
import {
  Alerte,
  BadgeStatut,
  Bouton,
  type DocumentAffiche,
  formaterDate,
  formaterDateHeure,
} from "@emine/ui";
import { ExternalLink, Eye } from "@emine/ui/icones";
import { useLocale, useTranslations } from "next-intl";
import { libelleSignataire } from "../referentiels";
import type { ReactNode } from "react";
import { useVisualiseurDocument } from "../documents/visualiseur";

/**
 * Adresses du document délivré par le relais BFF : affichage en ligne et téléchargement. Un
 * document révoqué porte « REVOQUE » dans son nom et un avertissement dans le visualiseur.
 */
export function documentDelivreAffiche(
  demandeId: number,
  numero: string,
  avertissement?: string,
): DocumentAffiche {
  const base = `/api/demandes/${demandeId}/document/contenu`;
  return {
    nomFichier: `${numero}${avertissement ? "-REVOQUE" : ""}.pdf`,
    typeMime: "application/pdf",
    urlAffichage: `${base}?inline=1`,
    urlTelechargement: base,
    avertissement,
  };
}

/**
 * Document délivré (US-06.4) : numéro, date, signataire, validité (« Valide » ou « Révoqué »
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
  const tReferentiels = useTranslations("referentiels");
  const { ouvrir, visualiseur } = useVisualiseurDocument();
  const revoqueLe = document.valide ? null : (document.revoqueLe ?? null);
  const anglais = useLocale() === "en";
  const autorite = document.valide
    ? null
    : ((anglais ? document.revoqueAutoriteEn : null) ?? document.revoqueAutorite ?? null);
  return (
    <div className="flex flex-col gap-4">
      {document.valide ? null : (
        <Alerte nature="erreur" titre={t("alerteRevoque.titre")}>
          {t("alerteRevoque.texte")}
        </Alerte>
      )}
      <dl className="grid grid-cols-1 gap-x-6 gap-y-2 sm:grid-cols-[auto_1fr]">
        <dt className="font-semibold">{t("numero")}</dt>
        <dd className="font-semibold">{document.numero}</dd>
        <dt className="font-semibold">{t("validite")}</dt>
        <dd>
          <BadgeStatut ton={document.valide ? "succes" : "danger"}>
            {document.valide ? t("valide") : t("nonValide")}
          </BadgeStatut>
        </dd>
        {revoqueLe ? (
          <>
            <dt className="font-semibold">{t("revoqueLe")}</dt>
            <dd>{formaterDateHeure(revoqueLe)}</dd>
          </>
        ) : null}
        {autorite ? (
          <>
            <dt className="font-semibold">{t("revoquePar")}</dt>
            <dd>{autorite}</dd>
          </>
        ) : null}
        {!document.valide && document.motifInvalidation ? (
          <>
            <dt className="font-semibold">{t("motif")}</dt>
            <dd className="whitespace-pre-line">{document.motifInvalidation}</dd>
          </>
        ) : null}
        <dt className="font-semibold">{t("delivreLe")}</dt>
        <dd>{formaterDateHeure(document.delivreLe)}</dd>
        <dt className="font-semibold">{t("signePar")}</dt>
        <dd>{libelleSignataire(document.signataire, tReferentiels)}</dd>
        <dt className="font-semibold">{t("empreinte")}</dt>
        <dd className="text-sm break-all text-texte-doux" title={document.empreinteSha256}>
          {document.empreinteSha256.slice(0, 16)}…
        </dd>
      </dl>
      <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap">
        <Bouton
          type="button"
          onClick={() =>
            ouvrir({
              ...documentDelivreAffiche(
                demandeId,
                document.numero,
                document.valide
                  ? undefined
                  : revoqueLe
                    ? t("alerteRevoque.visualiseur", { date: formaterDate(revoqueLe) })
                    : t("alerteRevoque.titre"),
              ),
              avertissementDetail: document.valide
                ? undefined
                : [
                    autorite ? t("detailAutorite", { autorite }) : null,
                    document.motifInvalidation
                      ? t("detailMotif", { motif: document.motifInvalidation })
                      : null,
                  ]
                    .filter(Boolean)
                    .join("\n") || undefined,
            })
          }
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
