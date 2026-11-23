import type { WorkflowDto } from "@emine/api-client";
import { type ComposantLien, formaterDate, TableauDonnees } from "@emine/ui";
import { ChevronRight } from "@emine/ui/icones";
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
 * Circuit en une ligne : étapes numérotées, séparées par une flèche, qui passent à la ligne sur
 * petit écran. Lu comme une liste ordonnée par les lecteurs d'écran.
 */
export function CircuitCompact({ etapes }: { etapes: WorkflowDto["enVigueur"]["etapes"] }) {
  return (
    <ol className="flex flex-wrap items-center gap-x-1 gap-y-1.5">
      {etapes.map((e, i) => (
        <li key={`${i}-${e.acteur}`} className="flex items-center gap-1">
          {i > 0 ? (
            <ChevronRight aria-hidden="true" className="size-4 shrink-0 text-texte-doux" />
          ) : null}
          <span className="inline-flex items-center gap-1.5 rounded-full bg-ice py-0.5 pr-2.5 pl-1 text-sm text-navy">
            <span
              aria-hidden="true"
              className="flex size-5 items-center justify-center rounded-full bg-white text-xs font-semibold"
            >
              {i + 1}
            </span>
            {e.acteur}
          </span>
        </li>
      ))}
    </ol>
  );
}

/**
 * Les workflows (US-02.5) : famille et documents (nom au survol et pour les lecteurs d'écran),
 * circuit compact, délais, version ; la ligne entière ouvre le workflow.
 */
export function TableauWorkflows({
  lignes,
  legende,
  libelles = {},
}: {
  lignes: WorkflowDto[];
  legende: string;
  /** Nom de chaque document du catalogue, par code. */
  libelles?: Record<string, string>;
}) {
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
            <span className="flex flex-col gap-0.5">
              <span className="font-semibold text-navy">{w.enVigueur.libelle}</span>
              <span className="text-sm text-texte-doux">
                {w.documents.map((c, i) => (
                  <span key={c}>
                    {i > 0 ? " · " : null}
                    {libelles[c] ? <abbr title={libelles[c]}>{c}</abbr> : c}
                  </span>
                ))}
              </span>
            </span>
          ),
        },
        {
          cle: "etapes",
          entete: t("colonnes.etapes"),
          rendu: (w) => (
            <span className="flex flex-col gap-1">
              <span className="sr-only">{t("etapesNombre", { n: w.enVigueur.etapes.length })}</span>
              <CircuitCompact etapes={w.enVigueur.etapes} />
            </span>
          ),
        },
        {
          cle: "delai",
          entete: t("colonnes.delai"),
          rendu: (w) => (
            <span className="flex flex-col whitespace-nowrap">
              <span>{fourchette(w)}</span>
              {w.delaiMinJours !== w.delaiMaxJours ? (
                <span className="text-sm text-texte-doux">{t("selonDocument")}</span>
              ) : null}
            </span>
          ),
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
