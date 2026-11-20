import { Carte } from "@emine/ui";
import { getTranslations } from "next-intl/server";

export default async function Accueil() {
  const t = await getTranslations("accueil");
  return (
    <main id="contenu" className="mx-auto flex max-w-3xl flex-col gap-6 px-4 py-10 sm:px-6">
      <h1 className="text-3xl sm:text-4xl">{t("titre")}</h1>
      <Carte>
        <p className="text-lg text-texte-doux">{t("presentation")}</p>
      </Carte>
    </main>
  );
}
