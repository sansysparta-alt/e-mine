import { lireParametresDelai } from "@emine/api-client";
import { Alerte, Carte, EnTetePage } from "@emine/ui";
import type { Metadata } from "next";
import { notFound, redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { FilArianeApp } from "@/composants/fil-ariane-app";
import { FormulaireDelais } from "@/composants/parametres/formulaire-delais";
import { proposerDelais } from "@/serveur/actions-parametres";
import { appelPage, chargerMoi, optionsApi } from "@/serveur/donnees";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("parametres.delais");
  return { title: t("proposerTitre") };
}

/**
 * Proposer une règle des délais (US-02.8) : réservé à l'administrateur, une proposition à la fois.
 * Point de départ : la dernière règle validée (programmée, sinon en vigueur).
 */
export default async function ProposerRegleDelais() {
  const moi = await chargerMoi();
  if (!moi.interne || !moi.permissions.includes("PARAMETRER")) notFound();
  const t = await getTranslations("parametres");
  const tWorkflows = await getTranslations("workflows.formulaire");
  const delais = await appelPage("/parametres/delais/proposer", async () =>
    lireParametresDelai(await optionsApi()).then((r) => r.data),
  );
  if (!delais.peutProposer) redirect("/parametres");
  const base =
    delais.versions.find((v) => v.etat === "programmee") ??
    delais.versions.find((v) => v.etat === "en_vigueur");

  return (
    <div className="flex flex-col gap-6">
      <EnTetePage
        retour={<FilArianeApp rubrique="parametres" courante={t("delais.proposerTitre")} />}
        titre={t("delais.proposerTitre")}
        description={t("delais.proposerDescription", { n: base?.version ?? 1 })}
      />
      <Alerte nature="info" titre={tWorkflows("effet.titre")}>
        {tWorkflows("effet.texte")}
      </Alerte>
      <Carte titre={t("delais.titre")}>
        <FormulaireDelais initial={base?.regle ?? delais.enVigueur} proposer={proposerDelais} />
      </Carte>
    </div>
  );
}
