import type { LigneFicheDto } from "@emine/api-client";
import { BadgeStatut, type ComposantLien, TableauDonnees } from "@emine/ui";
import Link from "next/link";
import { useTranslations } from "next-intl";
import { libelleSignataire, libelleWorkflowParNom } from "../referentiels";
import { presentationLigne } from "./presentation";

const LienNext: ComposantLien = ({ href, children, ...props }) => (
  <Link href={href} {...props}>
    {children}
  </Link>
);

/**
 * Liste des fiches du catalogue (US-02.2) : code, document et famille, délai, signataire, version
 * en vigueur et un seul badge (proposition en attente, retrait du catalogue ou « En vigueur ») ; la
 * ligne entière ouvre la fiche.
 */
export function TableauFiches({ lignes, legende }: { lignes: LigneFicheDto[]; legende: string }) {
  const t = useTranslations("catalogue");
  const tReferentiels = useTranslations("referentiels");
  return (
    <TableauDonnees
      legende={legende}
      lignes={lignes}
      cleLigne={(l) => l.code}
      Lien={LienNext}
      lienLigne={(l) => `/catalogue/${l.code}`}
      colonnes={[
        {
          cle: "document",
          entete: t("colonnes.document"),
          rendu: (l) => (
            <span className="flex flex-col">
              <span className="font-semibold text-navy">
                <span className="font-mono">{l.code}</span> · {l.libelleFr}
              </span>
              <span className="text-sm text-texte-doux">
                {libelleWorkflowParNom(l.famille, tReferentiels)}
              </span>
            </span>
          ),
        },
        {
          cle: "delai",
          entete: t("colonnes.delai"),
          rendu: (l) => (
            <span className="whitespace-nowrap">{t("jours", { n: l.delaiJours })}</span>
          ),
        },
        {
          cle: "signataire",
          entete: t("colonnes.signataire"),
          rendu: (l) => libelleSignataire(l.signataire, tReferentiels),
        },
        {
          cle: "version",
          entete: t("colonnes.version"),
          rendu: (l) => (
            <span className="whitespace-nowrap">{t("version", { n: l.numeroEnVigueur })}</span>
          ),
        },
        {
          cle: "statut",
          entete: t("colonnes.statut"),
          rendu: (l) => {
            const p = presentationLigne(l);
            return <BadgeStatut ton={p.ton}>{t(`statuts.${p.cle}`)}</BadgeStatut>;
          },
        },
      ]}
    />
  );
}
