import { Bouton, EtatVide } from "@emine/ui";
import { SearchX } from "@emine/ui/icones";
import Link from "next/link";
import { getTranslations } from "next-intl/server";

/**
 * Page introuvable dans l'espace connecté (gabarit conservé) : adresse inconnue, ou écran réservé
 * à un autre profil (catalogue, workflows, paramètres, journal d'audit). On ne dit pas lequel des
 * deux, pour ne rien révéler des écrans d'un profil ; on propose de revenir au tableau de bord.
 */
export default async function Introuvable() {
  const t = await getTranslations("etats.introuvable");
  return (
    <EtatVide
      icone={SearchX}
      titre={t("titre")}
      description={t("description")}
      action={
        <Bouton asChild>
          <Link href="/tableau-de-bord">{t("retour")}</Link>
        </Bouton>
      }
    />
  );
}
