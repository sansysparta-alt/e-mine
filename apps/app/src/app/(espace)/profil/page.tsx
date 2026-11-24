import { Bouton, Carte, EnTetePage } from "@emine/ui";
import { KeyRound } from "@emine/ui/icones";
import type { Metadata } from "next";
import { getLocale, getTranslations } from "next-intl/server";
import { ChoixLangue } from "@/composants/session/choix-langue";
import { chargerMoi } from "@/serveur/donnees";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("profil");
  return { title: t("titre") };
}

/**
 * Mon profil (menu sous l'avatar) : identité telle que gérée par l'administrateur, langue de
 * l'interface, modification du mot de passe (page Keycloak à l'identité e-mine).
 */
export default async function Profil() {
  const moi = await chargerMoi();
  const t = await getTranslations("profil");
  const langue = await getLocale();
  const ligne = (libelle: string, valeur: string) => (
    <div className="flex flex-col gap-1 border-b border-bordure py-3 last:border-0 sm:flex-row sm:gap-6">
      <dt className="font-semibold text-navy sm:w-48 sm:shrink-0">{libelle}</dt>
      <dd className="min-w-0 break-words">{valeur}</dd>
    </div>
  );

  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <EnTetePage titre={t("titre")} description={t("description")} />

      <Carte titre={t("identite.titre")}>
        <dl>
          {ligne(t("identite.nom"), `${moi.prenom} ${moi.nom}`)}
          {ligne(t("identite.email"), moi.email)}
          {ligne(t("identite.profil"), moi.profil.libelle)}
          {moi.organisation ? ligne(t("identite.organisation"), moi.organisation) : null}
        </dl>
        <p className="mt-3 text-sm text-texte-doux">{t("identite.aide")}</p>
      </Carte>

      <Carte titre={t("langue.titre")}>
        <ChoixLangue langue={langue} legende={t("langue.legende")} />
      </Carte>

      <Carte titre={t("securite.titre")}>
        <p className="mb-4 text-texte-doux">{t("securite.texte")}</p>
        <Bouton asChild variante="secondaire">
          <a href="/api/auth/login?action=mot-de-passe&retour=%2Fprofil">
            <KeyRound aria-hidden="true" className="size-5" />
            {t("securite.motDePasse")}
          </a>
        </Bouton>
      </Carte>
    </div>
  );
}
