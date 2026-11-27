"use client";

import type { SignalementDto } from "@emine/api-client";
import {
  BadgeStatut,
  classesReference,
  type ComposantLien,
  formaterDate,
  TableauDonnees,
} from "@emine/ui";
import Link from "next/link";
import { useTranslations } from "next-intl";
import { presentationSignalement } from "./signalements";

const LienNext: ComposantLien = ({ href, children, ...props }) => (
  <Link href={href} {...props}>
    {children}
  </Link>
);

/**
 * Signalements que le signataire doit décider (US-06.8), les plus anciens d'abord. La ligne ouvre
 * la fiche de la demande, à la section des signalements, où il révoque ou classe.
 */
export function TableauSignalements({ signalements }: { signalements: SignalementDto[] }) {
  const t = useTranslations("signalements");
  const tStatut = useTranslations("delivrance.signalements.statuts");
  return (
    <TableauDonnees
      legende={t("titre")}
      lignes={signalements}
      cleLigne={(s) => s.id}
      Lien={LienNext}
      lienLigne={(s) => `/demandes/${s.demandeId}#signalements`}
      colonnes={[
        {
          cle: "document",
          entete: t("colonnes.document"),
          rendu: (s) => (
            <span className="flex flex-col">
              <span className={`font-semibold text-navy tabular-nums ${classesReference}`}>
                {s.numeroDocument}
              </span>
              {s.numeroDemande ? (
                <span className={`text-sm text-texte-doux ${classesReference}`}>
                  {s.numeroDemande}
                </span>
              ) : null}
            </span>
          ),
        },
        {
          cle: "signalement",
          entete: t("colonnes.signalement"),
          rendu: (s) => (
            <span className="flex flex-col">
              <span>{s.auteur ?? "—"}</span>
              <span className="text-sm text-texte-doux">
                {t("signaleLe", { date: formaterDate(s.signaleLe) })}
              </span>
            </span>
          ),
        },
        {
          cle: "motif",
          entete: t("colonnes.motif"),
          rendu: (s) => <span className="line-clamp-2">{s.motif}</span>,
        },
        {
          cle: "statut",
          entete: t("colonnes.statut"),
          rendu: (s) => {
            const p = presentationSignalement(s);
            return <BadgeStatut ton={p.ton}>{tStatut(p.cle)}</BadgeStatut>;
          },
        },
        {
          cle: "actions",
          entete: t("colonnes.actions"),
          voirSiVide: t("decider"),
          rendu: () => null,
        },
      ]}
    />
  );
}
