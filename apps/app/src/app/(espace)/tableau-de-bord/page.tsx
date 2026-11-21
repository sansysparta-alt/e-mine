import { type DemandeDto, listerDemandes, listerTypesDocuments } from "@emine/api-client";
import type { Metadata } from "next";
import { unstable_rethrow } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { VueTableauDeBord } from "@/composants/vue-tableau-de-bord";
import { chargerMoi, optionsApi } from "@/serveur/donnees";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("tableauDeBord");
  return { title: t("titreOnglet") };
}

/** Demandes visibles et libellés du catalogue ; en cas d'échec, le tableau reste utilisable. */
async function chargerDemandes(): Promise<{
  demandes: DemandeDto[];
  libelles: Record<string, string>;
}> {
  try {
    const [demandes, documents] = await Promise.all([
      listerDemandes(undefined, await optionsApi()),
      listerTypesDocuments(),
    ]);
    return {
      demandes: demandes.data,
      libelles: Object.fromEntries(documents.data.map((d) => [d.code, d.libelleFr])),
    };
  } catch (e) {
    unstable_rethrow(e);
    console.error("Tableau de bord : demandes indisponibles", e);
    return { demandes: [], libelles: {} };
  }
}

/** Tableau de bord de l'espace connecté (gabarit du socle). */
export default async function TableauDeBord() {
  const [moi, { demandes, libelles }] = await Promise.all([chargerMoi(), chargerDemandes()]);
  return (
    <VueTableauDeBord
      prenom={moi.prenom}
      nom={moi.nom}
      profilLibelle={moi.profil.libelle}
      interne={moi.interne}
      peutDeposer={moi.permissions.includes("DEPOSER")}
      demandes={demandes}
      libelles={libelles}
    />
  );
}
