import { ErreurApi, lireWorkflow } from "@emine/api-client";
import { EnTetePage } from "@emine/ui";
import { ArrowLeft } from "@emine/ui/icones";
import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { FormulaireWorkflow } from "@/composants/workflow/formulaire-workflow";
import { enregistrerWorkflow } from "@/serveur/actions-workflow";
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
  const v = detail.workflow.enVigueur;

  return (
    <div className="flex flex-col gap-6">
      <EnTetePage
        retour={
          <Link
            href={`/workflows/${code}`}
            className="inline-flex min-h-11 items-center gap-2 text-royal underline-offset-4 hover:underline"
          >
            <ArrowLeft aria-hidden="true" className="size-4" />
            {v.libelle}
          </Link>
        }
        titre={t("titre")}
        description={t("description", { libelle: v.libelle, n: v.version })}
      />
      <FormulaireWorkflow code={code} enVigueur={v} enregistrer={enregistrerWorkflow} />
    </div>
  );
}
