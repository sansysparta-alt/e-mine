import {
  compterATraiter,
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
import { type CleGroupe, groupesDepuis, parametresListe } from "@/composants/travail/file-travail";
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
  travail?: { groupe: CleGroupe; dossiers: DemandeDto[]; total: number };
}> {
  try {
    const options = await optionsApi();
    const total = async (params: Parameters<typeof listerDemandes>[0]) =>
      (await listerDemandes({ ...params, taille: 10 }, options)).data;
    // Règle « À traiter » de l'API : mêmes nombres que « Mes demandes » et « À traiter ».
    const compteur = (await compterATraiter(options)).data;
    const groupeTravail = groupesDepuis(compteur, moi)[0];
    const [demandes, compteurs, documents, aTraiter, file] = await Promise.all([
      // L'API n'envoie jamais les brouillons des usagers à un agent (règle du socle).
      listerDemandes({ taille: 10 }, options),
      compterDemandes(options),
      listerTypesDocuments(),
      moi.interne ? null : total({ aTraiter: true, tri: "date", sens: "asc" }),
      groupeTravail
        ? listerDemandes({ ...parametresListe(groupeTravail), taille: 10 }, options).then(
            (r) => r.data,
          )
        : null,
    ]);
    const tarifs = Object.fromEntries(documents.data.map((d) => [d.code, d.tarifs]));
    const aFaire: ActionAFaire[] = (aTraiter?.elements ?? []).slice(0, A_FAIRE_MAX).flatMap((d) => {
      const action = actionAttendue(d);
      if (!action) return [];
      const t = tarifs[d.typeDocumentCode];
      const montantGnf =
        action === "FRAIS" ? t?.aPayerAuDepotGnf : action === "DROITS" ? t?.droitsGnf : null;
      return [{ demande: d, action, montantGnf }];
    });
    const CLE_TACHE: Record<string, TacheAgent["cle"]> = {
      "a-examiner": "aExaminer",
      "a-decider": "decisions",
      "a-signer": "aSigner",
    };
    const taches: TacheAgent[] = moi.interne
      ? compteur.groupes.flatMap((g) => {
          const cle = CLE_TACHE[g.cle];
          return cle ? [{ cle, valeur: g.nombre }] : [];
        })
      : [];
    return {
      demandes: demandes.data.elements,
      compteurs: compteurs.data,
      libelles: Object.fromEntries(documents.data.map((d) => [d.code, d.libelleFr])),
      aFaire,
      taches,
      ...(groupeTravail && file
        ? {
            travail: {
              groupe: groupeTravail.cle,
              dossiers: file.elements.slice(0, A_FAIRE_MAX),
              total: file.total,
            },
          }
        : {}),
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
  const { demandes, compteurs, libelles, aFaire, taches, travail } = await chargerDemandes(moi);
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
      travail={travail}
    />
  );
}
