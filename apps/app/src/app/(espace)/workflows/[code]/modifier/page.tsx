import { ErreurApi, lireWorkflow } from "@emine/api-client";
import { EnTetePage } from "@emine/ui";
import type { Metadata } from "next";
import { notFound, redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { libelleWorkflow } from "@/composants/referentiels";
import { FormulaireWorkflow } from "@/composants/workflow/formulaire-workflow";
import { proposerVersionWorkflow } from "@/serveur/actions-workflow";
import { FilArianeApp } from "@/composants/fil-ariane-app";
import { appelPage, chargerMoi, optionsApi } from "@/serveur/donnees";

type Props = { params: Promise<{ code: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { code } = await params;
  const t = await getTranslations("workflows.formulaire");
  return { title: `${code} · ${t("titre")}` };
}

/** Modifier un workflow (US-02.5) : réservé à l'administrateur. */
export default async function ModifierWorkflow({ params }: Props) {
  const { code } = await params;
  if (!/^[A-Z_]{2,30}$/.test(code)) notFound();
  const moi = await chargerMoi();
  if (!moi.interne || !moi.permissions.includes("PARAMETRER")) notFound();
  const t = await getTranslations("workflows.formulaire");

  let detail;
  try {
    detail = (
      await appelPage(`/workflows/${code}/modifier`, async () =>
        lireWorkflow(code, await optionsApi()),
      )
    ).data;
  } catch (e) {
    if (e instanceof ErreurApi && e.code === "SYS-002") notFound();
    throw e;
  }
  // Une proposition attend déjà le Ministre : retour à la fiche, qui l'affiche.
  if (!detail.peutProposer) redirect(`/workflows/${code}`);
  // Point de départ : la dernière version validée (programmée, sinon en vigueur).
  const v = detail.workflow.programmee ?? detail.workflow.enVigueur;
  const tReferentiels = await getTranslations("referentiels");

  return (
    <div className="flex flex-col gap-6">
      <EnTetePage
        retour={
          <FilArianeApp
            rubrique="workflows"
            parents={[
              {
                libelle: libelleWorkflow(code, v.libelle, tReferentiels),
                href: `/workflows/${code}`,
              },
            ]}
            courante={t("titre")}
          />
        }
        titre={t("titre")}
        description={t("description", {
          libelle: libelleWorkflow(code, v.libelle, tReferentiels),
          n: v.version,
        })}
      />
      <FormulaireWorkflow code={code} enVigueur={v} enregistrer={proposerVersionWorkflow} />
    </div>
  );
}
