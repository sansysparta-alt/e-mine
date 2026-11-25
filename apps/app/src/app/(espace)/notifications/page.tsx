import { listerNotifications } from "@emine/api-client";
import { EnTetePage, EtatVide, FiltresPastilles, tailleAutorisee } from "@emine/ui";
import { BellOff, CheckCheck } from "@emine/ui/icones";
import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { PaginationListe } from "@/composants/liste/pagination-liste";
import { ListeNotifications } from "@/composants/notification/liste-notifications";
import { ToutMarquerLu } from "@/composants/notification/tout-marquer-lu";
import { appelPage, chargerMoi, optionsApi } from "@/serveur/donnees";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("notifications");
  return { title: t("titre") };
}

type Props = { searchParams: Promise<{ page?: string; taille?: string; filtre?: string }> };

/**
 * Centre de notifications (US-07.2) : les messages envoyés à chaque étape de ses dossiers, des plus
 * récents aux plus anciens, **groupés par jour**, paginés ; filtres « Toutes » et « Non lues »
 * (le nombre de non lues est celui de la pastille de la cloche, et l'en-tête le dit) ; « Tout
 * marquer comme lu ». Chaque ligne ouvre le dossier et marque la notification comme lue ; une
 * action encore attendue (payer les droits, compléter) a son bouton.
 */
export default async function Notifications({ searchParams }: Props) {
  const p = await searchParams;
  const t = await getTranslations("notifications");
  // Les agents ne reçoivent pas de notification de dossier.
  if ((await chargerMoi()).interne) notFound();
  const page = Math.max(1, Number.parseInt(p.page ?? "1", 10) || 1);
  const taille = tailleAutorisee(Number.parseInt(p.taille ?? "", 10));
  const nonLues = p.filtre === "non-lues";
  const resultat = (
    await appelPage("/notifications", async () =>
      listerNotifications({ page, taille, nonLues }, await optionsApi()),
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
      {resultat.toutes === 0 ? (
        <EtatVide icone={BellOff} titre={t("vide.titre")} description={t("vide.description")} />
      ) : (
        <>
          <FiltresPastilles
            libelle={t("filtres.libelle")}
            options={[
              {
                valeur: "",
                libelle: t("filtres.toutes"),
                href: "/notifications",
                actif: !nonLues,
                nombre: resultat.toutes,
              },
              {
                valeur: "non-lues",
                libelle: t("filtres.nonLues"),
                href: "/notifications?filtre=non-lues",
                actif: nonLues,
                nombre: resultat.nonLues,
              },
            ]}
          />
          {resultat.total === 0 ? (
            <EtatVide
              icone={CheckCheck}
              titre={t("vide.toutLu")}
              description={t("vide.toutLuDescription")}
            />
          ) : (
            <>
              <ListeNotifications notifications={resultat.elements} />
              <PaginationListe
                chemin="/notifications"
                parametres={{ filtre: nonLues ? "non-lues" : undefined }}
                page={resultat.page}
                taille={resultat.taille}
                total={resultat.total}
                elements={{ un: t("element"), plusieurs: t("elements") }}
              />
            </>
          )}
        </>
      )}
    </div>
  );
}
