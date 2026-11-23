import type { EtapeDto } from "@emine/api-client";
import { useTranslations } from "next-intl";

/**
 * Étapes d'un workflow dans l'ordre : numéro, intervenant, action. Liste ordonnée, lisible au
 * lecteur d'écran (« Étape 2 sur 6 »).
 */
export function EtapesWorkflow({ etapes }: { etapes: EtapeDto[] }) {
  const t = useTranslations("workflows");
  return (
    <ol className="flex flex-col gap-2">
      {etapes.map((e, i) => (
        <li key={`${i}-${e.acteur}`} className="flex items-start gap-3">
          <span
            aria-hidden="true"
            className="flex size-7 shrink-0 items-center justify-center rounded-full bg-ice text-sm font-semibold text-navy"
          >
            {i + 1}
          </span>
          <span className="flex flex-col">
            <span className="sr-only">{t("etape", { n: i + 1, total: etapes.length })} : </span>
            <span className="font-semibold text-navy">{e.acteur}</span>
            <span className="text-texte-doux">{e.libelle}</span>
          </span>
        </li>
      ))}
    </ol>
  );
}
