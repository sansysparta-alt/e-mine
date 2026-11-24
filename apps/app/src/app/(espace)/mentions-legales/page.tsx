import { Alerte, Carte, EnTetePage } from "@emine/ui";
import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("mentionsLegales");
  return { title: t("titre") };
}

const SECTIONS = ["editeur", "hebergement", "donnees", "cookies", "accessibilite"] as const;

/**
 * Mentions légales (pied de page) : éditeur, hébergement, données personnelles, cookies,
 * accessibilité. Texte de la version de démonstration, à valider par le Ministère.
 */
export default async function MentionsLegales() {
  const t = await getTranslations("mentionsLegales");
  return (
    <div className="flex max-w-3xl flex-col gap-6">
      <EnTetePage titre={t("titre")} description={t("description")} />
      <Alerte nature="info" titre={t("demonstration.titre")}>
        {t("demonstration.texte")}
      </Alerte>
      {SECTIONS.map((s) => (
        <Carte key={s} titre={t(`${s}.titre`)}>
          <p className="text-texte-doux">{t(`${s}.texte`)}</p>
        </Carte>
      ))}
    </div>
  );
}
