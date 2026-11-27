import {
  lireIndicateursEncaissements,
  compterATraiter,
  compterVues,
  consulterJournal,
  type DemandeDto,
  type IndicateursDto,
  lireIndicateurs,
  listerDemandes,
  listerPaiementsDemande,
  lireParametresDelai,
  lireRegleRemboursement,
  listerRemboursements,
  listerSignalementsADecider,
  listerFichesDocuments,
  listerWorkflows,
  listerTypesDocuments,
  lireTableauAdministration,
} from "@emine/api-client";
import { EnTetePage, EtatErreur, tailleDePagePour } from "@emine/ui";
import { VueAdministration } from "@/composants/administration/vue-administration";
import { VueFiscal } from "@/composants/encaissement/vue-fiscal";
import type { Metadata } from "next";
import { libelleDocument } from "@/composants/referentiels";
import { unstable_rethrow } from "next/navigation";
import { getLocale, getTranslations } from "next-intl/server";
import {
  type ActionAFaire,
  type AValider,
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
import { profilAffiche } from "@/serveur/profil";
import { paiementRejeteAExpliquer } from "@/composants/paiement/paiement-rejete";

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
  /** Ministre : propositions à valider (catalogue, workflows, règle des délais ; US-02.4, US-02.8). */
  aValider?: AValider;
  /** Directeur : remboursements proposés qu'il doit valider ou refuser (US-04.18). */
  remboursementsAValider?: number;
  /** Signataire : signalements de la Brigade sur ses documents, à décider (US-06.8). */
  signalementsADecider?: number;
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
    const langue = await getLocale();
    const libelles = Object.fromEntries(documents.map((d) => [d.code, libelleDocument(d, langue)]));
    const groupeTravail = groupesDepuis(compteur, moi)[0];
    const pilote = profil === "guichet" || profil === "instruction" || profil === "decideur";
    const [recentes, aTraiter, indicateurs, file, catalogue, remboursements, signalements] =
      await Promise.all([
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
        // Mêmes nombres que les listes « À valider » (compteurs de l'API).
        moi.permissions.includes("VALIDER_CATALOGUE") ? propositionsAValider(options) : undefined,
        // Règle « À traiter » des remboursements (API) : même nombre que le compteur du menu.
        moi.permissions.includes("VALIDER_REMBOURSEMENT")
          ? listerRemboursements({ taille: 10 }, options).then((r) => r.data.compteurs.aTraiter)
          : undefined,
        // Même nombre que le compteur du menu « Signalements » (US-06.8).
        moi.interne && moi.permissions.includes("SIGNER")
          ? listerSignalementsADecider({ taille: 10 }, options).then((r) => r.data.total)
          : undefined,
      ]);
    const tarifs = Object.fromEntries(documents.map((d) => [d.code, d.tarifs]));
    const aFaire: ActionAFaire[] = await Promise.all(
      (aTraiter?.elements ?? []).slice(0, COURTE_LISTE).flatMap((d) => {
        const action = actionAttendue(d);
        if (!action) return [];
        const t = tarifs[d.typeDocumentCode];
        const montantGnf =
          action === "FRAIS" ? t?.aPayerAuDepotGnf : action === "DROITS" ? t?.droitsGnf : null;
        // Un paiement rejeté s'explique là où l'usager doit payer de nouveau (US-07.5).
        const rejet =
          action === "COMPLEMENT"
            ? Promise.resolve(null)
            : listerPaiementsDemande(d.id, options)
                .then((r) => paiementRejeteAExpliquer(r.data))
                .catch(() => null);
        return [
          rejet.then((p) => ({
            demande: d,
            action,
            montantGnf,
            paiementRejete: p ? { reference: p.reference, motif: p.motifRejet ?? null } : null,
          })),
        ];
      }),
    );
    return {
      profil,
      demandes: recentes?.elements ?? [],
      libelles,
      aFaire,
      aTraiterTotal: compteur.total,
      groupes: compteur.groupes,
      vues,
      indicateurs,
      aValider: catalogue,
      remboursementsAValider: remboursements,
      signalementsADecider: signalements,
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
/** Propositions à valider par le Ministre ; une lecture impossible compte zéro (pas d'alerte). */
async function propositionsAValider(
  options: Awaited<ReturnType<typeof optionsApi>>,
): Promise<AValider> {
  const [catalogue, workflows, delais, remboursement] = await Promise.allSettled([
    listerFichesDocuments({ filtre: "A_VALIDER", taille: 10 }, options).then(
      (r) => r.data.compteurs["A_VALIDER"] ?? 0,
    ),
    listerWorkflows({ taille: 10 }, options).then((r) => r.data.aValider),
    lireParametresDelai(options).then(
      (r) => r.data.versions.filter((v) => v.etat === "a_valider").length,
    ),
    lireRegleRemboursement(options).then(
      (r) => r.data.versions.filter((v) => v.etat === "a_valider").length,
    ),
  ]);
  const n = (r: PromiseSettledResult<number>) => (r.status === "fulfilled" ? r.value : 0);
  return {
    catalogue: n(catalogue),
    workflows: n(workflows),
    delais: n(delais),
    remboursement: n(remboursement),
  };
}

export default async function TableauDeBord({ searchParams }: Props) {
  const demandee = (await searchParams).periode;
  const periode = PERIODES.find((p) => p === demandee) ?? "mois";
  const moi = await chargerMoi();
  // Administrateur technique (paramétrer, sans instruire ni signer) : vue d'ensemble technique du
  // cahier des charges (comptes, profils, disponibilité, journal), et non une file de dossiers.
  if (
    moi.interne &&
    moi.permissions.includes("PARAMETRER") &&
    !moi.permissions.some((d) => d === "INSTRUIRE" || d === "SIGNER")
  ) {
    return (
      <TableauAdministration prenom={moi.prenom} nom={moi.nom} profil={await profilAffiche(moi)} />
    );
  }
  // Responsable fiscal (encaisser, sans instruire ni signer) : ses files et ses recettes (lot 5).
  if (
    moi.interne &&
    moi.permissions.includes("ENCAISSER") &&
    !moi.permissions.some((d) => d === "INSTRUIRE" || d === "SIGNER")
  ) {
    return <TableauFiscal prenom={moi.prenom} nom={moi.nom} profil={await profilAffiche(moi)} />;
  }
  const donnees = await charger(moi, periode);
  return (
    <VueTableauDeBord
      prenom={moi.prenom}
      nom={moi.nom}
      profilLibelle={await profilAffiche(moi)}
      peutDeposer={moi.permissions.includes("DEPOSER")}
      periode={periode}
      {...donnees}
    />
  );
}

/** Tableau de bord du Responsable fiscal (US-04.11) ; les données indisponibles sont annoncées. */
async function TableauFiscal({
  prenom,
  nom,
  profil,
}: {
  prenom: string;
  nom: string;
  profil: string;
}) {
  const t = await getTranslations("tableauDeBord");
  const indicateurs = await lireIndicateursEncaissements(await optionsApi())
    .then((r) => r.data)
    .catch((e: unknown) => {
      unstable_rethrow(e);
      console.error("Tableau de bord fiscal : données indisponibles", e);
      return null;
    });
  return (
    <div className="flex flex-col gap-6">
      <EnTetePage
        titre={t("bonjour", { prenom, nom })}
        description={t("profil", { libelle: profil })}
      />
      {indicateurs ? (
        <VueFiscal indicateurs={indicateurs} />
      ) : (
        <EtatErreur titre={t("indisponible.titre")} description={t("fiscal.indisponible")} />
      )}
    </div>
  );
}

/** Tableau de bord de l'administrateur technique ; les données indisponibles sont annoncées. */
async function TableauAdministration({
  prenom,
  nom,
  profil,
}: {
  prenom: string;
  nom: string;
  profil: string;
}) {
  const t = await getTranslations("tableauDeBord");
  const tAdmin = await getTranslations("administration");
  const options = await optionsApi();
  const [tableau, journal] = await Promise.all([
    lireTableauAdministration(options)
      .then((r) => r.data)
      .catch((e: unknown) => {
        unstable_rethrow(e);
        console.error("Tableau de bord technique : données indisponibles", e);
        return null;
      }),
    consulterJournal({ taille: 10 }, options)
      .then((r) => r.data.elements.slice(0, 5))
      .catch(() => []),
  ]);
  return (
    <div className="flex flex-col gap-6">
      <EnTetePage
        titre={t("bonjour", { prenom, nom })}
        description={t("profil", { libelle: profil })}
      />
      {tableau ? (
        <VueAdministration tableau={tableau} operations={journal} />
      ) : (
        <EtatErreur titre={t("indisponible.titre")} description={tAdmin("indisponible")} />
      )}
    </div>
  );
}
