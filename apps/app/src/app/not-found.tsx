import { Bouton, EtatVide } from "@emine/ui";
import { SearchX } from "@emine/ui/icones";
import Link from "next/link";
import { getTranslations } from "next-intl/server";

/** Page introuvable hors de l'espace connecté : même message, sur fond clair, en français. */
export default async function IntrouvableRacine() {
  const t = await getTranslations("etats.introuvable");
  return (
    <main id="contenu" className="mx-auto flex min-h-dvh max-w-2xl items-center bg-fond px-4">
      <div className="w-full">
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
      </div>
    </main>
  );
}
