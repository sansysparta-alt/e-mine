import type { EtapeDto } from "@emine/api-client";
import { useLocale, useTranslations } from "next-intl";

/**
 * Étapes d'un workflow en frise verticale : numéro, intervenant, action, reliés par un trait.
 * Liste ordonnée, lisible au lecteur d'écran (« Étape 2 sur 6 »).
 */
export function EtapesWorkflow({ etapes }: { etapes: EtapeDto[] }) {
  const t = useTranslations("workflows");
  // Textes saisis par l'administrateur : l'anglais s'il existe (US-02.9), sinon le français.
  const anglais = useLocale() === "en";
  return (
    <ol className="flex flex-col">
      {etapes.map((e, i) => (
        <li key={`${i}-${e.acteur}`} className="relative flex gap-3 pb-4 last:pb-0">
          {i < etapes.length - 1 ? (
            <span
              aria-hidden="true"
              className="absolute top-8 bottom-0 left-3.5 w-px -translate-x-1/2 bg-bordure"
            />
          ) : null}
          <span
            aria-hidden="true"
            className="relative flex size-7 shrink-0 items-center justify-center rounded-full bg-ice text-sm font-semibold text-navy"
          >
            {i + 1}
          </span>
          <span className="flex flex-col pt-0.5">
            <span className="sr-only">{t("etape", { n: i + 1, total: etapes.length })} : </span>
            <span className="font-semibold text-navy">{(anglais && e.acteurEn) || e.acteur}</span>
            <span className="text-texte-doux">{(anglais && e.libelleEn) || e.libelle}</span>
          </span>
        </li>
      ))}
    </ol>
  );
}
