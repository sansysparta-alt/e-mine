import {
  compterDemandes,
  type DemandeDto,
  listerDemandes,
  listerTypesDocuments,
} from "@emine/api-client";
import type { Metadata } from "next";
import { unstable_rethrow } from "next/navigation";
import { getTranslations } from "next-intl/server";
import {
  type ActionAFaire,
  actionAttendue,
  type TacheAgent,
  VueTableauDeBord,
} from "@/composants/vue-tableau-de-bord";
import { chargerMoi, optionsApi } from "@/serveur/donnees";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("tableauDeBord");
  return { title: t("titreOnglet") };
}

type Profil = { interne: boolean; permissions: string[]; profil: { code: string } };

/** Nombre d'actions « à faire » montrées au demandeur (le reste est dans « Mes demandes »). */
const A_FAIRE_MAX = 5;

/**
 * Demandes visibles, libellés du catalogue et travail attendu selon le profil (correctifs J4) :
 * le demandeur voit ce qu'il doit payer ou compléter, l'agent ses files (guichet, décisions).
 * En cas d'échec, le tableau reste utilisable.
 */
async function chargerDemandes(moi: Profil): Promise<{
  demandes: DemandeDto[];
  compteurs: Record<string, number>;
  libelles: Record<string, string>;
  aFaire: ActionAFaire[];
  taches: TacheAgent[];
}> {
  try {
    const options = await optionsApi();
    const total = async (params: Parameters<typeof listerDemandes>[0]) =>
      (await listerDemandes({ ...params, taille: 10 }, options)).data;
    const [demandes, compteurs, documents, fraisDus, aAgir, aExaminer, decisions, aSigner] =
      await Promise.all([
        listerDemandes({ taille: 10 }, options),
        compterDemandes(options),
        listerTypesDocuments(),
        moi.interne
          ? null
          : total({ statut: ["deposee"], fraisPayes: false, tri: "date", sens: "asc" }),
        moi.interne
          ? null
          : total({ statut: ["decision", "complement_requis"], tri: "date", sens: "asc" }),
        moi.permissions.includes("INSTRUIRE")
          ? total({ statut: ["deposee"], fraisPayes: true })
          : null,
        moi.interne && moi.permissions.includes("SIGNER")
          ? total({ statut: ["recevable"], signataireProfil: moi.profil.code })
          : null,
        moi.interne && moi.permissions.includes("SIGNER")
          ? total({ statut: ["droits_payes"], signataireProfil: moi.profil.code })
          : null,
      ]);
    const tarifs = Object.fromEntries(documents.data.map((d) => [d.code, d.tarifs]));
    const aFaire: ActionAFaire[] = [...(aAgir?.elements ?? []), ...(fraisDus?.elements ?? [])]
      .slice(0, A_FAIRE_MAX)
      .flatMap((d) => {
        const action = actionAttendue(d);
        if (!action) return [];
        const t = tarifs[d.typeDocumentCode];
        const montantGnf =
          action === "FRAIS" ? t?.aPayerAuDepotGnf : action === "DROITS" ? t?.droitsGnf : null;
        return [{ demande: d, action, montantGnf }];
      });
    const taches: TacheAgent[] = [
      ...(aExaminer ? [{ cle: "aExaminer" as const, valeur: aExaminer.total }] : []),
      ...(decisions ? [{ cle: "decisions" as const, valeur: decisions.total }] : []),
      ...(aSigner ? [{ cle: "aSigner" as const, valeur: aSigner.total }] : []),
    ];
    return {
      demandes: demandes.data.elements,
      compteurs: compteurs.data,
      libelles: Object.fromEntries(documents.data.map((d) => [d.code, d.libelleFr])),
      aFaire,
      taches,
    };
  } catch (e) {
    unstable_rethrow(e);
    console.error("Tableau de bord : demandes indisponibles", e);
    return { demandes: [], compteurs: {}, libelles: {}, aFaire: [], taches: [] };
  }
}

/** Tableau de bord de l'espace connecté (gabarit du socle). */
export default async function TableauDeBord() {
  const moi = await chargerMoi();
  const { demandes, compteurs, libelles, aFaire, taches } = await chargerDemandes(moi);
  return (
    <VueTableauDeBord
      prenom={moi.prenom}
      nom={moi.nom}
      profilLibelle={moi.profil.libelle}
      interne={moi.interne}
      peutDeposer={moi.permissions.includes("DEPOSER")}
      demandes={demandes}
      compteurs={compteurs}
      libelles={libelles}
      aFaire={aFaire}
      taches={taches}
    />
  );
}
