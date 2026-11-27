"use client";

import type { ElementAVerifierDto } from "@emine/api-client";
import {
  BadgeStatut,
  classesAuDessusDuLien,
  cn,
  type ComposantLien,
  formaterDate,
  formaterGnf,
  formaterGnfSigne,
  joursCalendairesDepuis,
  TableauDonnees,
} from "@emine/ui";
import Link from "next/link";
import { useTranslations } from "next-intl";
import { chargerAssociables, deciderEcart } from "@/serveur/actions-encaissements";
import { DecisionsEcart } from "./decision-ecart";

const LienNext: ComposantLien = ({ href, children, ...props }) => (
  <Link href={href} {...props}>
    {children}
  </Link>
);

/** Ce que la ligne ouvre : la ligne dans son relevé, ou la demande d'un paiement en ligne. */
export function lienElement(e: ElementAVerifierDto): string {
  if (e.type === "ligne" && e.releveId != null) return `/releves/${e.releveId}?resultat=a-verifier`;
  return e.demandeId != null ? `/demandes/${e.demandeId}` : "/a-verifier";
}

/**
 * File « À vérifier » du Responsable fiscal (US-04.9) : un élément par ligne, la plus ancienne
 * d'abord ; ce qui a été reçu et ce qui était attendu, la situation en clair (un seul badge orange
 * : la décision lui revient), la demande concernée et les décisions possibles données par l'API.
 */
export function TableauAVerifier({ elements }: { elements: ElementAVerifierDto[] }) {
  const t = useTranslations("aVerifier");
  const tNature = useTranslations("aVerifier.natures");
  const tDates = useTranslations("dates");
  const tCommun = useTranslations("commun");
  return (
    <TableauDonnees
      legende={t("titre")}
      lignes={elements}
      cleLigne={(e) => `${e.type}-${e.ligneId ?? e.referencePaiement}`}
      Lien={LienNext}
      lienLigne={lienElement}
      colonnes={[
        {
          cle: "operation",
          entete: t("colonnes.operation"),
          rendu: (e) => (
            <span className="flex flex-col">
              <span className="font-semibold text-navy">
                {e.type === "ligne" ? e.libelle : t("paiementEnLigne")}
              </span>
              <span className="text-sm text-texte-doux">
                {t("operationDu", { date: formaterDate(e.dateOperation) })}
                {" · "}
                {tDates("attente", { jours: joursCalendairesDepuis(e.signaleLe) })}
              </span>
            </span>
          ),
        },
        {
          cle: "montant",
          entete: t("colonnes.montant"),
          alignement: "droite",
          rendu: (e) => (
            <span className="flex flex-col items-end">
              <span className="font-semibold whitespace-nowrap tabular-nums">
                {formaterGnfSigne(e.montantGnf, e.sens === "D")}
              </span>
              {e.montantAttenduGnf != null && e.nature !== "doublon" && e.nature !== "tardif" ? (
                <span className="text-sm text-texte-doux">
                  {t("attendu", { montant: formaterGnf(e.montantAttenduGnf) })}
                </span>
              ) : null}
            </span>
          ),
        },
        {
          cle: "situation",
          entete: t("colonnes.situation"),
          rendu: (e) => (
            <span className="flex flex-col items-start gap-1">
              <BadgeStatut ton="avertissement">{t("badge")}</BadgeStatut>
              <span className="text-sm text-texte-doux">
                {tNature(e.nature, { ecart: formaterGnf(Math.abs(e.ecartGnf ?? 0)) })}
              </span>
            </span>
          ),
        },
        {
          cle: "demande",
          entete: t("colonnes.demande"),
          interactive: true,
          rendu: (e) =>
            e.referencePaiement ? (
              <span className="flex flex-col">
                <span className="whitespace-nowrap tabular-nums">{e.referencePaiement}</span>
                {e.demandeId != null && e.numeroDemande ? (
                  <Link
                    href={`/demandes/${e.demandeId}`}
                    className={cn(
                      classesAuDessusDuLien,
                      "inline-flex min-h-11 items-center text-sm font-semibold whitespace-nowrap text-royal tabular-nums underline-offset-4 hover:underline",
                    )}
                  >
                    {e.numeroDemande}
                  </Link>
                ) : null}
              </span>
            ) : (
              <span className="text-texte-doux">{t("aucunPaiement")}</span>
            ),
        },
        {
          cle: "actions",
          entete: t("colonnes.actions"),
          interactive: true,
          voirSiVide: tCommun("voirDetail"),
          rendu: (e) =>
            e.decisions.length > 0 ? (
              <DecisionsEcart
                element={e}
                decider={deciderEcart}
                chargerAssociables={chargerAssociables}
              />
            ) : null,
        },
      ]}
    />
  );
}
