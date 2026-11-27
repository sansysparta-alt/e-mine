import { listerSignalementsADecider } from "@emine/api-client";
import { EnTetePage, EtatVide, tailleAutorisee } from "@emine/ui";
import { Flag } from "@emine/ui/icones";
import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { TableauSignalements } from "@/composants/delivrance/tableau-signalements";
import { PaginationListe } from "@/composants/liste/pagination-liste";
import { appelPage, chargerMoi, optionsApi } from "@/serveur/donnees";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("signalements");
  return { title: t("titre") };
}

type Props = { searchParams: Promise<{ page?: string; taille?: string }> };

/**
 * « Signalements » (US-06.8, décision V1-11) : les signalements de la Brigade anti-fraude sur les
 * documents dont le profil connecté est le signataire, à décider (révoquer ou classer, avec motif).
 * Règle « À traiter » de l'API (`/signalements/a-decider`), même nombre que le compteur du menu.
 */
export default async function Signalements({ searchParams }: Props) {
  const p = await searchParams;
  const moi = await chargerMoi();
  if (!moi.interne || !moi.permissions.includes("SIGNER")) notFound();
  const t = await getTranslations("signalements");
  const page = Math.max(1, Number.parseInt(p.page ?? "1", 10) || 1);
  const taille = tailleAutorisee(Number.parseInt(p.taille ?? "", 10));
  const resultat = (
    await appelPage("/signalements", async () =>
      listerSignalementsADecider({ page, taille }, await optionsApi()),
    )
  ).data;

  return (
    <div className="flex flex-col gap-6">
      <EnTetePage titre={t("titre")} description={t("description")} />
      {resultat.total === 0 ? (
        <EtatVide icone={Flag} titre={t("vide.titre")} description={t("vide.description")} />
      ) : (
        <div className="flex flex-col gap-4">
          <TableauSignalements signalements={resultat.elements} />
          <PaginationListe
            chemin="/signalements"
            parametres={{}}
            page={resultat.page}
            taille={resultat.taille}
            total={resultat.total}
            elements={{ un: t("element"), plusieurs: t("elements") }}
          />
        </div>
      )}
    </div>
  );
}
