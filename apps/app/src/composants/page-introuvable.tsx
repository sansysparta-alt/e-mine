import { Bouton, EtatVide } from "@emine/ui";
import { SearchX } from "@emine/ui/icones";
import Link from "next/link";
import { getTranslations } from "next-intl/server";

/**
 * Page « introuvable », la même partout : adresse inconnue, écran réservé à un autre profil ou objet
 * inexistant. Elle ne dit pas lequel de ces cas, pour ne rien révéler des écrans des autres profils,
 * et ramène au tableau de bord.
 */
export async function PageIntrouvable() {
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
