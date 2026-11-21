import { useTranslations } from "next-intl";
import { FormulaireDeconnexion } from "./session/formulaire-deconnexion";
import { SurveillanceSession } from "./session/surveillance-session";

export type EnTeteProps = {
  nomComplet: string;
  profil: string;
};

/**
 * En-tête de l'espace connecté : marque, utilisateur et profil, déconnexion (formulaire POST).
 * Porte aussi la surveillance de la session (maintien pendant l'activité, avertissement).
 */
export function EnTete({ nomComplet, profil }: EnTeteProps) {
  const t = useTranslations("enTete");
  return (
    <header className="bg-navy text-white">
      <SurveillanceSession />
      <div className="mx-auto flex max-w-5xl flex-wrap items-center justify-between gap-3 px-4 py-3 sm:px-6">
        <p className="flex items-baseline gap-2">
          <span className="font-titre text-2xl font-bold">{t("marque")}</span>
          <span className="text-sm text-ice">{t("sousTitre")}</span>
        </p>
        <div className="flex flex-wrap items-center gap-3">
          <p className="text-left text-sm leading-tight sm:text-right">
            <span className="block font-semibold">{t("connecte", { nom: nomComplet })}</span>
            <span className="block text-ice">{profil}</span>
          </p>
          <FormulaireDeconnexion libelle={t("deconnexion")} />
        </div>
      </div>
    </header>
  );
}
