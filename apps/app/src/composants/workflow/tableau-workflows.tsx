import type { WorkflowDto } from "@emine/api-client";
import { type ComposantLien, formaterDate, TableauDonnees } from "@emine/ui";
import Link from "next/link";
import { useTranslations } from "next-intl";

const LienNext: ComposantLien = ({ href, children, ...props }) => (
  <Link href={href} {...props}>
    {children}
  </Link>
);

/** Fourchette de délais : « 2 à 3 jours », ou « 15 jours » si elle est fixe. */
export function useFourchette() {
  const t = useTranslations("workflows");
  return (w: { delaiMinJours: number; delaiMaxJours: number }) =>
    w.delaiMinJours === w.delaiMaxJours
      ? t("jours", { n: w.delaiMinJours })
      : t("fourchette", { min: w.delaiMinJours, max: w.delaiMaxJours });
}

/**
 * Les workflows (US-02.5) : famille, documents, étapes, délais, version ; la ligne entière ouvre
 * le workflow.
 */
export function TableauWorkflows({ lignes, legende }: { lignes: WorkflowDto[]; legende: string }) {
  const t = useTranslations("workflows");
  const fourchette = useFourchette();
  return (
    <TableauDonnees
      legende={legende}
      lignes={lignes}
      cleLigne={(w) => w.code}
      Lien={LienNext}
      lienLigne={(w) => `/workflows/${w.code}`}
      colonnes={[
        {
          cle: "famille",
          entete: t("colonnes.famille"),
          rendu: (w) => (
            <span className="flex flex-col">
              <span className="font-semibold text-navy">{w.enVigueur.libelle}</span>
              <span className="text-sm text-texte-doux">{w.documents.join(" · ")}</span>
            </span>
          ),
        },
        {
          cle: "etapes",
          entete: t("colonnes.etapes"),
          rendu: (w) => (
            <span className="text-sm">{w.enVigueur.etapes.map((e) => e.acteur).join(" → ")}</span>
          ),
        },
        {
          cle: "delai",
          entete: t("colonnes.delai"),
          rendu: (w) => <span className="whitespace-nowrap">{fourchette(w)}</span>,
        },
        {
          cle: "version",
          entete: t("colonnes.version"),
          rendu: (w) => (
            <span className="flex flex-col whitespace-nowrap">
              <span>{t("version", { n: w.enVigueur.version })}</span>
              <span className="text-sm text-texte-doux">
                {t("modifieeLe", { date: formaterDate(w.enVigueur.modifieeLe) })}
              </span>
            </span>
          ),
        },
      ]}
    />
  );
}
