import { listerReleves } from "@emine/api-client";
import { EnTetePage, EtatVide, tailleAutorisee } from "@emine/ui";
import { FileSpreadsheet } from "@emine/ui/icones";
import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { PaginationListe } from "@/composants/liste/pagination-liste";
import { ImportReleve } from "@/composants/releve/import-releve";
import { TableauReleves } from "@/composants/releve/tableaux-releve";
import { appelPage, chargerMoi, optionsApi } from "@/serveur/donnees";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("releves");
  return { title: t("titre") };
}

type Props = { searchParams: Promise<{ page?: string; taille?: string }> };

/**
 * « Relevés bancaires » du Responsable fiscal (US-04.8) : import d'un relevé CSV (rapprochement
 * automatique), puis liste paginée des relevés importés avec leurs résultats.
 */
export default async function Releves({ searchParams }: Props) {
  const p = await searchParams;
  const moi = await chargerMoi();
  if (!moi.interne || !moi.permissions.includes("ENCAISSER")) notFound();
  const t = await getTranslations("releves");
  const page = Math.max(1, Number.parseInt(p.page ?? "1", 10) || 1);
  const taille = tailleAutorisee(Number.parseInt(p.taille ?? "", 10));
  const resultat = (
    await appelPage("/releves", async () => listerReleves({ page, taille }, await optionsApi()))
  ).data;

  return (
    <div className="flex flex-col gap-6">
      <EnTetePage titre={t("titre")} description={t("description")} />
      <ImportReleve exempleDisponible={resultat.exempleDisponible} />
      {resultat.total === 0 ? (
        <EtatVide
          icone={FileSpreadsheet}
          titre={t("vide.titre")}
          description={t("vide.description")}
        />
      ) : (
        <div className="flex flex-col gap-4">
          <TableauReleves releves={resultat.elements} />
          <PaginationListe
            chemin="/releves"
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
