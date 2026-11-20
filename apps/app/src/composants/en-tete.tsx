import { Bouton } from "@emine/ui";
import { useTranslations } from "next-intl";

export type EnTeteProps = {
  nomComplet: string;
  profil: string;
};

/** En-tête de l'espace connecté : marque, utilisateur et profil, déconnexion (formulaire POST). */
export function EnTete({ nomComplet, profil }: EnTeteProps) {
  const t = useTranslations("enTete");
  return (
    <header className="bg-navy text-white">
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
          <form action="/api/auth/logout" method="post">
            <Bouton type="submit" variante="secondaire" className="border-white">
              {t("deconnexion")}
            </Bouton>
          </form>
        </div>
      </div>
    </header>
  );
}
