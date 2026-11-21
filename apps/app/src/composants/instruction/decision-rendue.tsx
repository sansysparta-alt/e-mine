import type { EtatDecisionDto } from "@emine/api-client";
import { BadgeStatut, formaterDateHeure } from "@emine/ui";
import { useTranslations } from "next-intl";

/**
 * Décision rendue (US-05.4), montrée au demandeur et aux agents : sens (badge au code couleur
 * commun), date, signataire et motif ou observations. Rien si aucune décision n'est rendue.
 */
export function DecisionRendue({ etat }: { etat: EtatDecisionDto }) {
  const t = useTranslations("instruction.decision.rendue");
  if (etat.decisionFavorable == null || !etat.decisionRenduLe) return null;
  const favorable = etat.decisionFavorable;
  return (
    <dl className="grid grid-cols-1 gap-x-6 gap-y-2 sm:grid-cols-[auto_1fr]">
      <dt className="font-semibold">{t("sens")}</dt>
      <dd>
        <BadgeStatut ton={favorable ? "succes" : "danger"}>
          {favorable ? t("favorable") : t("rejet")}
        </BadgeStatut>
      </dd>
      <dt className="font-semibold">{t("le")}</dt>
      <dd>{formaterDateHeure(etat.decisionRenduLe)}</dd>
      <dt className="font-semibold">{t("par")}</dt>
      <dd>
        {etat.decisionAuteur}
        {etat.decisionAuteurProfil ? ` · ${etat.decisionAuteurProfil}` : ""}
      </dd>
      {etat.decisionMotif ? (
        <>
          <dt className="font-semibold">{favorable ? t("observations") : t("motif")}</dt>
          <dd className="whitespace-pre-line">{etat.decisionMotif}</dd>
        </>
      ) : null}
    </dl>
  );
}
