import { listerNotifications } from "@emine/api-client";
import { EnTetePage, EtatVide } from "@emine/ui";
import { BellOff } from "@emine/ui/icones";
import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { lireParametres, type Parametres } from "@/composants/demande/parametres-liste";
import { PaginationListe } from "@/composants/liste/pagination-liste";
import { ListeNotifications } from "@/composants/notification/liste-notifications";
import { ToutMarquerLu } from "@/composants/notification/tout-marquer-lu";
import { appelPage, chargerMoi, optionsApi } from "@/serveur/donnees";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("notifications");
  return { title: t("titre") };
}

type Props = { searchParams: Promise<Parametres> };

/**
 * Centre de notifications (US-07.2) : les messages envoyés à l'utilisateur à chaque étape de ses
 * dossiers (les mêmes que par SMS et e-mail), des plus récents aux plus anciens, paginés. Chaque
 * ligne ouvre le dossier concerné et marque la notification comme lue.
 */
export default async function Notifications({ searchParams }: Props) {
  const p = lireParametres(await searchParams);
  const t = await getTranslations("notifications");
  // Les agents ne reçoivent pas de notification : comme « À traiter » pour un usager.
  if ((await chargerMoi()).interne) notFound();
  const resultat = (
    await appelPage("/notifications", async () =>
      listerNotifications({ page: p.page, taille: p.taille }, await optionsApi()),
    )
  ).data;

  return (
    <div className="flex flex-col gap-6">
      <EnTetePage
        titre={t("titre")}
        description={
          resultat.nonLues > 0
            ? t("descriptionNonLues", { nombre: resultat.nonLues })
            : t("description")
        }
        actions={resultat.nonLues > 0 ? <ToutMarquerLu /> : null}
      />
      {resultat.total === 0 ? (
        <EtatVide icone={BellOff} titre={t("vide.titre")} description={t("vide.description")} />
      ) : (
        <>
          <ListeNotifications notifications={resultat.elements} />
          <PaginationListe
            chemin="/notifications"
            page={resultat.page}
            taille={resultat.taille}
            total={resultat.total}
            elements={t("elements")}
          />
        </>
      )}
    </div>
  );
}
