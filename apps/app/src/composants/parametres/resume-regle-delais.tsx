import type { ParametresDelaiDto } from "@emine/api-client";
import { useTranslations } from "next-intl";

/** Règle des délais en clair (consultation, proposition à valider, historique). */
export function ResumeRegleDelais({ regle }: { regle: ParametresDelaiDto }) {
  const t = useTranslations("parametres.delais");
  const pauses = [
    regle.pauseComplement ? t("pauses.complement") : null,
    regle.pauseDroits ? t("pauses.droits") : null,
  ].filter((p): p is string => p !== null);
  return (
    <dl className="grid grid-cols-1 gap-x-6 gap-y-2 sm:grid-cols-[auto_1fr]">
      <dt className="font-semibold text-navy">{t("resume.depart")}</dt>
      <dd>{t(`depart.${regle.depart}`)}</dd>
      <dt className="font-semibold text-navy">{t("resume.pauses")}</dt>
      <dd>
        {pauses.length === 0 ? (
          t("resume.aucunePause")
        ) : (
          <ul className="flex flex-col">
            {pauses.map((p) => (
              <li key={p}>{p}</li>
            ))}
          </ul>
        )}
      </dd>
      <dt className="font-semibold text-navy">{t("resume.seuil")}</dt>
      <dd>
        {t("resume.seuilValeur", {
          pct: regle.seuilProchePourcent,
          min: regle.seuilProcheMinJours,
        })}
      </dd>
    </dl>
  );
}
