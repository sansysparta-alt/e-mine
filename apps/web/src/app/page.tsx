import { Alerte } from "@emine/ui";
import { getTranslations } from "next-intl/server";

export default async function Accueil() {
  const t = await getTranslations("accueil");
  return (
    <main id="contenu" className="mx-auto flex max-w-4xl flex-col gap-6 px-4 py-12 sm:px-6">
      <h1 className="text-3xl sm:text-5xl">{t("titre")}</h1>
      <p className="max-w-2xl text-lg text-texte-doux sm:text-xl">{t("presentation")}</p>
      <Alerte titre={t("avertissement")} />
    </main>
  );
}
