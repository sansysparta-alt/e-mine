import { lireParametresDelai, listerCanauxNotification } from "@emine/api-client";
import { Alerte, Carte, EnTetePage } from "@emine/ui";
import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { FormulaireCanaux } from "@/composants/parametres/formulaire-canaux";
import { FormulaireDelais } from "@/composants/parametres/formulaire-delais";
import { enregistrerCanaux, enregistrerDelais } from "@/serveur/actions-parametres";
import { appelPage, chargerMoi, optionsApi } from "@/serveur/donnees";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("parametres");
  return { title: t("titre") };
}

/**
 * Délais et notifications (US-02.6, US-02.7) : règle des délais et canaux de chaque notification,
 * choix provisoires du porteur du projet à faire valider par le Ministère. L'administrateur modifie
 * (immédiat et tracé), le Ministre consulte.
 */
export default async function Parametres() {
  const moi = await chargerMoi();
  const admin = moi.permissions.includes("PARAMETRER");
  if (!moi.interne || (!admin && !moi.permissions.includes("VALIDER_CATALOGUE"))) notFound();
  const t = await getTranslations("parametres");

  const [delais, canaux] = await appelPage("/parametres", async () => {
    const options = await optionsApi();
    return Promise.all([
      lireParametresDelai(options).then((r) => r.data),
      listerCanauxNotification(options).then((r) => r.data),
    ]);
  });

  return (
    <div className="flex flex-col gap-6">
      <EnTetePage
        titre={t("titre")}
        description={t(admin ? "description.admin" : "description.lecture")}
      />
      <Alerte nature="info" titre={t("provisoire.titre")}>
        {t("provisoire.texte")}
      </Alerte>
      <Carte titre={t("delais.titre")}>
        <FormulaireDelais initial={delais} modifiable={admin} enregistrer={enregistrerDelais} />
      </Carte>
      <Carte titre={t("canaux.titre")}>
        <FormulaireCanaux initial={canaux} modifiable={admin} enregistrer={enregistrerCanaux} />
      </Carte>
    </div>
  );
}
