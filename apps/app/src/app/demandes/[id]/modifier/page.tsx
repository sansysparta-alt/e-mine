import { ErreurApi, lireDemande } from "@emine/api-client";
import type { Metadata } from "next";
import { notFound, redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { FormulaireDemande } from "@/composants/demande/formulaire-demande";
import { enregistrerBrouillon } from "@/serveur/actions-demande";
import { appelPage, chargerReferentiels, optionsApi } from "@/serveur/donnees";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("demande");
  return { title: t("titreBrouillon") };
}

type Props = { params: Promise<{ id: string }> };

/** Reprise d'un brouillon (US-03.1). Une demande déjà déposée n'est plus modifiable. */
export default async function ModifierDemande({ params }: Props) {
  const { id } = await params;
  if (!/^\d{1,18}$/.test(id)) notFound();
  const t = await getTranslations("demande");
  const chemin = `/demandes/${id}/modifier`;
  let demande;
  try {
    demande = (await appelPage(chemin, async () => lireDemande(Number(id), await optionsApi())))
      .data;
  } catch (e) {
    if (e instanceof ErreurApi && e.code === "DEM-002") notFound();
    throw e;
  }
  if (demande.statut !== "brouillon") redirect("/tableau-de-bord");
  const referentiels = await chargerReferentiels();
  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-3xl sm:text-4xl">{t("titreBrouillon")}</h1>
      <FormulaireDemande
        demande={demande}
        demandeur={demande.demandeur}
        {...referentiels}
        enregistrer={enregistrerBrouillon}
      />
    </div>
  );
}
