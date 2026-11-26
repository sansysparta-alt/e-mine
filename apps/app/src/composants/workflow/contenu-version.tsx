import type { VersionWorkflowDto } from "@emine/api-client";
import { useTranslations } from "next-intl";
import { EtapesWorkflow } from "./etapes-workflow";

/** Contenu d'une version de workflow : nom de la famille, paiements affichés, étapes. */
export function ContenuVersionWorkflow({ version }: { version: VersionWorkflowDto }) {
  const t = useTranslations("workflows");
  return (
    <div className="flex flex-col gap-3">
      <dl className="grid grid-cols-1 gap-x-6 gap-y-1 sm:grid-cols-[auto_1fr]">
        <dt className="font-semibold text-navy">{t("formulaire.champs.libelle")}</dt>
        <dd>{version.libelle}</dd>
        <dt className="font-semibold text-navy">{t("colonnes.paiements")}</dt>
        <dd>{version.reglePaiement}</dd>
      </dl>
      <EtapesWorkflow etapes={version.etapes} />
    </div>
  );
}
