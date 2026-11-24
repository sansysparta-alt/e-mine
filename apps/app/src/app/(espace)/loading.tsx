import { EtatChargement } from "@emine/ui";
import { getTranslations } from "next-intl/server";

/** Chargement d'un écran de l'espace connecté (état standard du socle). */
export default async function Chargement() {
  const t = await getTranslations("etats");
  return <EtatChargement libelle={t("chargement")} lignes={4} forme="page" />;
}
