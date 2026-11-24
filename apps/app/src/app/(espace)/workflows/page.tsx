import { listerTypesDocuments, listerWorkflows } from "@emine/api-client";
import { EnTetePage, EtatVide } from "@emine/ui";
import { Workflow } from "@emine/ui/icones";
import type { Metadata } from "next";
import { libelleDocument } from "@/composants/referentiels";
import { notFound } from "next/navigation";
import { getLocale, getTranslations } from "next-intl/server";
import { PaginationListe } from "@/composants/liste/pagination-liste";
import { TableauWorkflows } from "@/composants/workflow/tableau-workflows";
import { appelPage, chargerMoi, optionsApi } from "@/serveur/donnees";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("workflows");
  return { title: t("titre") };
}

type Props = { searchParams: Promise<{ page?: string; taille?: string }> };

/**
 * Workflows (US-02.5, cahier des charges diapo 15) : une famille de documents par workflow, ses
 * étapes et intervenants, sa règle de paiement et sa fourchette de délais.
 */
export default async function Workflows({ searchParams }: Props) {
  const moi = await chargerMoi();
  const admin = moi.permissions.includes("PARAMETRER");
  if (!moi.interne || (!admin && !moi.permissions.includes("VALIDER_CATALOGUE"))) notFound();
  const t = await getTranslations("workflows");
  const p = await searchParams;
  const page = Math.max(1, Number.parseInt(p.page ?? "1", 10) || 1);
  const taille = [10, 20, 50, 100].includes(Number(p.taille)) ? Number(p.taille) : 20;

  const [{ data }, documents] = await Promise.all([
    appelPage("/workflows", async () => listerWorkflows({ page, taille }, await optionsApi())),
    // Noms des documents (catalogue public) : les codes seuls sont obscurs pour un non-initié.
    listerTypesDocuments()
      .then((r) => r.data)
      .catch(() => []),
  ]);
  const langue = await getLocale();
  const libelles = Object.fromEntries(documents.map((d) => [d.code, libelleDocument(d, langue)]));

  return (
    <div className="flex flex-col gap-6">
      <EnTetePage
        titre={t("titre")}
        description={t(admin ? "description.admin" : "description.lecture")}
      />
      {data.total === 0 ? (
        <EtatVide icone={Workflow} titre={t("vide")} />
      ) : (
        <div className="flex flex-col gap-4">
          <TableauWorkflows lignes={data.elements} legende={t("titre")} libelles={libelles} />
          <PaginationListe
            chemin="/workflows"
            page={data.page}
            taille={data.taille}
            total={data.total}
            elements={{ un: t("element"), plusieurs: t("elements") }}
          />
        </div>
      )}
    </div>
  );
}
