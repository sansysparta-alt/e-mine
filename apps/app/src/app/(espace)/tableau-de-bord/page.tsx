import {
  compterATraiter,
  compterVues,
  type DemandeDto,
  type IndicateursDto,
  lireIndicateurs,
  listerDemandes,
  listerTypesDocuments,
} from "@emine/api-client";
import { tailleDePagePour } from "@emine/ui";
import type { Metadata } from "next";
import { unstable_rethrow } from "next/navigation";
import { getTranslations } from "next-intl/server";
import {
  type ActionAFaire,
  actionAttendue,
  type ClePeriode,
  DEMANDES_RECENTES,
  PERIODES,
  type ProfilTableau,
  VueTableauDeBord,
  type VuesTableau,
} from "@/composants/vue-tableau-de-bord";
import { type CleGroupe, groupesDepuis, parametresListe } from "@/composants/travail/file-travail";
import { chargerMoi, optionsApi } from "@/serveur/donnees";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("tableauDeBord");
  return { title: t("titreOnglet") };
}

type Profil = { interne: boolean; permissions: string[]; profil: { code: string } };

/** Nombre d'actions « à faire » et de dossiers à traiter montrés (le reste est dans les listes). */
const COURTE_LISTE = 5;

type Donnees = {
  profil: ProfilTableau;
  demandes: DemandeDto[];
  libelles: Record<string, string>;
  aFaire: ActionAFaire[];
  aTraiterTotal: number;
  groupes?: { cle: string; nombre: number }[];
  vues?: VuesTableau;
  indicateurs?: IndicateursDto;
  travail?: { groupe: CleGroupe; dossiers: DemandeDto[]; total: number };
  /** Données indisponibles : on le dit, plutôt qu'un état vide trompeur (revue B). */
  indisponible?: boolean;
};

/**
 * Profil du tableau de bord, d'après la règle « À traiter » de l'API : le signataire pilote, le
 * guichet suit son rôle, l'usager voit ce qu'il doit faire.
 */
function profilTableau(interne: boolean, groupes: string[]): ProfilTableau {
  if (!interne) return "demandeur";
  if (groupes.includes("a-decider")) return "decideur";
  if (groupes.includes("a-examiner")) return "guichet";
  // US-05.3 : DNM, Géologue, Inspection (avis techniques requis par le catalogue).
  if (groupes.includes("avis-a-rendre")) return "instruction";
  return "autre";
}

/**
 * Données du tableau de bord selon le profil (correctifs ergonomie). En cas d'échec, le tableau
 * reste utilisable (état vide).
 */
async function charger(moi: Profil, periode: ClePeriode): Promise<Donnees> {
  try {
    const options = await optionsApi();
    // Règle « À traiter » et vues de l'API : mêmes nombres que « Mes demandes » et « À traiter ».
    const [compteur, vues, documents] = await Promise.all([
      compterATraiter(options).then((r) => r.data),
      compterVues(options).then((r) => r.data),
      listerTypesDocuments().then((r) => r.data),
    ]);
    const profil = profilTableau(
      moi.interne,
      compteur.groupes.map((g) => g.cle),
    );
    const libelles = Object.fromEntries(documents.map((d) => [d.code, d.libelleFr]));
    const groupeTravail = groupesDepuis(compteur, moi)[0];
    const pilote = profil === "guichet" || profil === "instruction" || profil === "decideur";
    const [recentes, aTraiter, indicateurs, file] = await Promise.all([
      pilote
        ? null
        : listerDemandes({ taille: tailleDePagePour(DEMANDES_RECENTES) }, options).then(
            (r) => r.data,
          ),
      profil === "demandeur"
        ? listerDemandes({ aTraiter: true, tri: "date", sens: "asc", taille: 10 }, options).then(
            (r) => r.data,
          )
        : null,
      pilote ? lireIndicateurs({ periode }, options).then((r) => r.data) : undefined,
      pilote && groupeTravail
        ? listerDemandes(
            { ...parametresListe(groupeTravail), taille: tailleDePagePour(COURTE_LISTE) },
            options,
          ).then((r) => r.data)
        : null,
    ]);
    const tarifs = Object.fromEntries(documents.map((d) => [d.code, d.tarifs]));
    const aFaire: ActionAFaire[] = (aTraiter?.elements ?? [])
      .slice(0, COURTE_LISTE)
      .flatMap((d) => {
        const action = actionAttendue(d);
        if (!action) return [];
        const t = tarifs[d.typeDocumentCode];
        const montantGnf =
          action === "FRAIS" ? t?.aPayerAuDepotGnf : action === "DROITS" ? t?.droitsGnf : null;
        return [{ demande: d, action, montantGnf }];
      });
    return {
      profil,
      demandes: recentes?.elements ?? [],
      libelles,
      aFaire,
      aTraiterTotal: compteur.total,
      groupes: compteur.groupes,
      vues,
      indicateurs,
      ...(groupeTravail && file
        ? {
            travail: {
              groupe: groupeTravail.cle,
              dossiers: file.elements.slice(0, COURTE_LISTE),
              total: file.total,
            },
          }
        : {}),
    };
  } catch (e) {
    unstable_rethrow(e);
    console.error("Tableau de bord : données indisponibles", e);
    return {
      // Seul l’état d’erreur s’affiche : ni « rien à faire », ni chiffres à zéro.
      profil: "autre",
      demandes: [],
      libelles: {},
      aFaire: [],
      aTraiterTotal: 0,
      indisponible: true,
    };
  }
}

type Props = { searchParams: Promise<{ periode?: string }> };

/** Tableau de bord de l'espace connecté (gabarit du socle), adapté au profil. */
export default async function TableauDeBord({ searchParams }: Props) {
  const demandee = (await searchParams).periode;
  const periode = PERIODES.find((p) => p === demandee) ?? "mois";
  const moi = await chargerMoi();
  const donnees = await charger(moi, periode);
  return (
    <VueTableauDeBord
      prenom={moi.prenom}
      nom={moi.nom}
      profilLibelle={moi.profil.libelle}
      peutDeposer={moi.permissions.includes("DEPOSER")}
      periode={periode}
      {...donnees}
    />
  );
}
