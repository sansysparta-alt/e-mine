import { getTranslations } from "next-intl/server";

/**
 * En-tête officiel des pages publiques de vérification (R6, décision du porteur du 27/11/2026) :
 * texte seul, sans emblème ni logo (règle 3 du dépôt public) — « République de Guinée », la devise
 * nationale, puis le Ministère et la plateforme.
 */
export async function EnTeteOfficiel() {
  const t = await getTranslations("enTeteOfficiel");
  return (
    <header className="border-b border-bordure bg-white">
      <div className="mx-auto flex max-w-3xl flex-col items-center gap-0.5 px-4 py-4 text-center sm:px-6">
        <p className="text-base font-bold tracking-wide text-navy uppercase">{t("republique")}</p>
        <p className="text-sm text-texte-doux italic">{t("devise")}</p>
        <p className="mt-1 text-sm font-semibold text-navy">{t("ministere")}</p>
      </div>
    </header>
  );
}
