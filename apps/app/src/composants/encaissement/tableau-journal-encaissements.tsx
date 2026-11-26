"use client";

import type { LigneJournalEncaissementsDto } from "@emine/api-client";
import {
  classesAuDessusDuLien,
  cn,
  type ComposantLien,
  formaterDate,
  formaterGnf,
  TableauDonnees,
} from "@emine/ui";
import Link from "next/link";
import { useTranslations } from "next-intl";

const LienNext: ComposantLien = ({ href, children, ...props }) => (
  <Link href={href} {...props}>
    {children}
  </Link>
);

/**
 * Journal des encaissements (US-04.11) : une recette par paiement accepté (montant dû, reçu), un
 * remboursement de frais en négatif (ordre du Trésor). La ligne ouvre la demande concernée.
 */
export function TableauJournalEncaissements({
  lignes,
}: {
  lignes: LigneJournalEncaissementsDto[];
}) {
  const t = useTranslations("journalEncaissements");
  const tObjet = useTranslations("paiement.objet");
  return (
    <TableauDonnees
      legende={t("titre")}
      lignes={lignes}
      cleLigne={(l) => `${l.type}-${l.reference}`}
      Lien={LienNext}
      lienLigne={(l) =>
        l.demandeId != null ? `/demandes/${l.demandeId}` : "/journal-encaissements"
      }
      colonnes={[
        { cle: "date", entete: t("colonnes.date"), rendu: (l) => formaterDate(l.date) },
        {
          cle: "operation",
          entete: t("colonnes.operation"),
          rendu: (l) => (
            <span className="flex flex-col">
              <span className="font-semibold text-navy">{t(`types.${l.type}`)}</span>
              <span className="text-sm text-texte-doux tabular-nums">
                {l.reference}
                {l.piece ? ` · ${l.piece}` : ""}
              </span>
            </span>
          ),
        },
        {
          cle: "demande",
          entete: t("colonnes.demande"),
          interactive: true,
          rendu: (l) => (
            <span className="flex flex-col">
              {l.demandeId != null && l.numeroDemande ? (
                <Link
                  href={`/demandes/${l.demandeId}`}
                  className={cn(
                    classesAuDessusDuLien,
                    "inline-flex min-h-11 items-center font-semibold text-royal tabular-nums underline-offset-4 hover:underline",
                  )}
                >
                  {l.numeroDemande}
                </Link>
              ) : null}
              <span className="text-sm text-texte-doux">
                {tObjet.has(l.objet) ? tObjet(l.objet) : l.objet} · {l.codeDocument}
              </span>
            </span>
          ),
        },
        {
          cle: "payeur",
          entete: t("colonnes.payeur"),
          rendu: (l) => (
            <span className="flex flex-col">
              <span>{l.payeur ?? "—"}</span>
              <span className="text-sm text-texte-doux">{t(`moyens.${l.moyen}`)}</span>
            </span>
          ),
        },
        {
          cle: "montant",
          entete: t("colonnes.montant"),
          alignement: "droite",
          rendu: (l) => (
            <span
              className={cn(
                "font-semibold tabular-nums",
                l.montantGnf < 0 ? "text-danger" : "text-texte",
              )}
            >
              {l.montantGnf < 0 ? "− " : ""}
              {formaterGnf(Math.abs(l.montantGnf))}
            </span>
          ),
        },
      ]}
    />
  );
}
