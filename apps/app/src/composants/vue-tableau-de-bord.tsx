import type { DemandeDto, IndicateursDto } from "@emine/api-client";
import {
  Alerte,
  Bouton,
  Carte,
  CarteChiffre,
  classesAuDessusDuLien,
  classesLienEtire,
  classesLigneCliquable,
  cn,
  EnTetePage,
  EtatErreur,
  EtatVide,
  FiltresPastilles,
  formaterGnf,
  formaterNombre,
  GrilleChiffres,
} from "@emine/ui";
import {
  Banknote,
  CircleCheck,
  CircleX,
  Clock,
  CreditCard,
  FilePen,
  FileText,
  FolderOpen,
  Hourglass,
  Timer,
  Inbox,
  Play,
  ListChecks,
  Plus,
  TriangleAlert,
  Stamp,
  Wallet,
} from "@emine/ui/icones";
import Link from "next/link";
import { useTranslations } from "next-intl";
import type { ReactNode } from "react";
import { TableauDemandes } from "./demande/tableau-demandes";
import { RepartitionDocuments } from "./tableau-de-bord/repartition-documents";
import { adresseFiche, type CleGroupe } from "./travail/file-travail";
import { TableauTravail } from "./travail/tableau-travail";

/** Nombre de demandes récentes affichées sur le tableau de bord. */
export const DEMANDES_RECENTES = 5;

/** Périodes des indicateurs de pilotage (clés de l'API). */
export const PERIODES = ["mois", "30-jours", "annee"] as const;
export type ClePeriode = (typeof PERIODES)[number];

/** Tableau de bord adapté au profil : ce que chacun doit voir en premier. */
/** Profil du tableau de bord ; « instruction » = profils qui rendent les avis techniques. */
export type ProfilTableau = "demandeur" | "guichet" | "instruction" | "decideur" | "autre";

/** Action attendue du demandeur sur une de ses demandes (bloc « Ce que je dois faire »). */
export type ActionAFaire = {
  demande: DemandeDto;
  action: "FRAIS" | "DROITS" | "COMPLEMENT";
  /** Montant à payer en GNF, s'il est connu. */
  montantGnf?: number | null;
  /** Le paiement précédent a été rejeté (US-07.5) : sa référence et son motif. */
  paiementRejete?: { reference: string; motif: string | null } | null;
};

/** Vues exclusives de la liste (API) : leur somme est le total. */
export type VuesTableau = { total: number; vues: { cle: string; nombre: number }[] };

/** Déduit l'action attendue du demandeur d'après le statut (et les frais payés). */
export function actionAttendue(d: DemandeDto): ActionAFaire["action"] | null {
  // Paiement déclaré en cours de vérification (virement, US-04.7) : rien à payer pour l'instant.
  if (d.paiementEnVerification && (d.statut === "deposee" || d.statut === "decision")) return null;
  if (d.statut === "deposee" && !d.fraisPayes) return "FRAIS";
  if (d.statut === "decision") return "DROITS";
  if (d.statut === "complement_requis") return "COMPLEMENT";
  return null;
}

export type VueTableauDeBordProps = {
  prenom: string;
  nom: string;
  profilLibelle: string;
  profil: ProfilTableau;
  /** Droit « déposer » (US-01.6) : affiche l'action de nouvelle demande. */
  peutDeposer?: boolean;
  /** Demandes récentes (les siennes pour un usager), la plus récente d'abord. */
  demandes?: DemandeDto[];
  /** Libellé de chaque document du catalogue, par code. */
  libelles?: Record<string, string>;
  /** Demandeur : ce qu'il doit faire maintenant (payer, compléter). */
  aFaire?: ActionAFaire[];
  /** Nombre total de demandes « à traiter » par le profil (règle unique de l'API). */
  aTraiterTotal?: number;
  /** Nombre de chaque groupe « à traiter » du profil (API), pour les cartes de son rôle. */
  groupes?: { cle: string; nombre: number }[];
  /** Vues exclusives du profil (API) : mêmes nombres que « Mes demandes ». */
  vues?: VuesTableau;
  /** Indicateurs de l'API (retards, attentes, période). */
  indicateurs?: IndicateursDto;
  /** Période choisie pour les indicateurs « sur la période ». */
  periode?: ClePeriode;
  /** Premiers dossiers de la file de travail de l'agent, avec de quoi commencer le traitement. */
  travail?: { groupe: CleGroupe; dossiers: DemandeDto[]; total: number };
  /**
   * Ministre (US-02.4, US-02.8) : propositions à valider au catalogue, dans les workflows et pour
   * la règle des délais (mêmes nombres que les listes « À valider ») ; absent pour les autres.
   */
  aValider?: AValider;
  /**
   * Directeur (US-04.18) : remboursements proposés par le Responsable fiscal qu'il doit valider ou
   * refuser (règle « À traiter » de l'API) ; absent pour les autres.
   */
  remboursementsAValider?: number;
  /** Données indisponibles (API injoignable) : état d'erreur explicite, aucun chiffre. */
  indisponible?: boolean;
};

/** Propositions à valider par le Ministre, par objet (nombres de l'API). */
export type AValider = {
  catalogue: number;
  workflows: number;
  delais: number;
  /** Règle de remboursement des frais (V1-4), même circuit que la règle des délais. */
  remboursement: number;
};

/** Total des propositions à valider : les quatre objets ensemble. */
export function totalAValider(a: AValider): number {
  return a.catalogue + a.workflows + a.delais + a.remboursement;
}

/** Adresse de la liste des demandes filtrée (cartes cliquables). */
function liste(parametres: Record<string, string>): string {
  return `/demandes?${new URLSearchParams(parametres).toString()}`;
}

/**
 * Tableau de bord sur le gabarit du socle (correctifs ergonomie), adapté au profil :
 *
 * - demandeur : « Ce que je dois faire » en tête, puis ses chiffres (mêmes vues que « Mes
 *   demandes », leur somme est le total) ;
 * - guichet : indicateurs de son rôle (compléments et frais attendus, traités aujourd'hui, en
 *   retard), puis sa file ;
 * - décideur (Ministre, Directeur) : pilotage — retards bien visibles, chiffres de la période
 *   (modifiable), courte liste à traiter, répartition par document.
 *
 * Chaque carte affiche sa période et ouvre la liste filtrée correspondante.
 */
export function VueTableauDeBord({
  prenom,
  nom,
  profilLibelle,
  profil,
  peutDeposer = false,
  demandes = [],
  libelles = {},
  aFaire = [],
  aTraiterTotal = 0,
  groupes = [],
  vues,
  indicateurs,
  periode = "mois",
  travail,
  aValider,
  remboursementsAValider = 0,
  indisponible = false,
}: VueTableauDeBordProps) {
  const t = useTranslations("tableauDeBord");
  const nouvelle = peutDeposer ? (
    <Bouton asChild>
      <Link href="/demandes/nouvelle">
        <Plus aria-hidden="true" className="size-5" />
        {t("demandes.nouvelle")}
      </Link>
    </Bouton>
  ) : null;
  const nombreVue = (cle: string) => vues?.vues.find((v) => v.cle === cle)?.nombre ?? 0;
  const aCeJour = t("periodes.aCeJour");
  const nombreGroupe = (cle: string) => groupes.find((g) => g.cle === cle)?.nombre ?? 0;
  // Délais (US-05.6) : mêmes chiffres que la liste filtrée « Proches du dépassement ».
  const carteProches = indicateurs ? (
    <CarteChiffre
      libelle={t("guichet.proches")}
      periode={aCeJour}
      valeur={indicateurs.procheDepassement}
      precision={t("guichet.prochesPrecision", {
        pourcent: indicateurs.parametresDelai.seuilProchePourcent,
        min: indicateurs.parametresDelai.seuilProcheMinJours,
      })}
      icone={Hourglass}
      href={liste({ procheDepassement: "1" })}
    />
  ) : null;
  const carteRetard = indicateurs ? (
    <CarteChiffre
      libelle={t("guichet.retard")}
      periode={aCeJour}
      valeur={indicateurs.enRetard}
      precision={t("guichet.retardPrecision")}
      icone={TriangleAlert}
      href={liste({ enRetard: "1" })}
    />
  ) : null;

  return (
    <div className="flex flex-col gap-6">
      <EnTetePage
        titre={t("bonjour", { prenom, nom })}
        description={t("profil", { libelle: profilLibelle })}
        actions={nouvelle}
      />

      {indisponible ? (
        <EtatErreur
          titre={t("indisponible.titre")}
          description={t("indisponible.texte")}
          action={
            <Bouton asChild variante="secondaire">
              <Link href="/tableau-de-bord">{t("indisponible.reessayer")}</Link>
            </Bouton>
          }
        />
      ) : null}

      {aValider && totalAValider(aValider) > 0 ? (
        <Alerte
          nature="avertissement"
          titre={t("aValider.titre", { nombre: totalAValider(aValider) })}
        >
          <p>{t("aValider.texte")}</p>
          <ul className="mt-1 flex flex-col">
            {(
              [
                ["catalogue", aValider.catalogue, "/catalogue?filtre=A_VALIDER"],
                ["workflows", aValider.workflows, "/workflows"],
                ["delais", aValider.delais, "/parametres"],
                ["remboursement", aValider.remboursement, "/parametres#titre-remboursement"],
              ] as const
            )
              .filter(([, n]) => n > 0)
              .map(([cle, n, href]) => (
                <li key={cle}>
                  <Link
                    href={href}
                    className="inline-flex min-h-11 items-center font-semibold text-royal underline"
                  >
                    {t(`aValider.${cle}`, { n })}
                  </Link>
                </li>
              ))}
          </ul>
        </Alerte>
      ) : null}

      {remboursementsAValider > 0 ? (
        <Alerte
          nature="avertissement"
          titre={t("remboursementsAValider.titre", { nombre: remboursementsAValider })}
        >
          <p>{t("remboursementsAValider.texte")}</p>
          <Link
            href="/remboursements?statut=demande"
            className="inline-flex min-h-11 items-center font-semibold text-royal underline"
          >
            {t("remboursementsAValider.lien")}
          </Link>
        </Alerte>
      ) : null}

      {profil === "demandeur" ? (
        <>
          <CeQueJeDoisFaire aFaire={aFaire} libelles={libelles} />
          <section aria-labelledby="mes-chiffres" className="flex flex-col gap-3">
            <h2 id="mes-chiffres" className="text-xl">
              {t("chiffres.titreDemandeur")}
            </h2>
            <GrilleChiffres colonnes={3}>
              <CarteChiffre
                libelle={t("chiffres.toutes")}
                periode={aCeJour}
                valeur={vues?.total ?? 0}
                icone={FileText}
                href="/demandes"
              />
              <CarteChiffre
                libelle={t("chiffres.aFaire")}
                periode={aCeJour}
                valeur={nombreVue("a-traiter")}
                icone={ListChecks}
                href="/demandes?groupe=a-traiter"
              />
              <CarteChiffre
                libelle={t("chiffres.enCours")}
                periode={aCeJour}
                valeur={nombreVue("en-cours")}
                icone={Hourglass}
                href="/demandes?groupe=en-cours"
              />
              <CarteChiffre
                libelle={t("chiffres.brouillons")}
                periode={aCeJour}
                valeur={nombreVue("brouillons")}
                icone={FilePen}
                href="/demandes?groupe=brouillons"
              />
              <CarteChiffre
                libelle={t("chiffres.delivrees")}
                periode={aCeJour}
                valeur={nombreVue("terminees")}
                icone={CircleCheck}
                href="/demandes?groupe=terminees"
              />
              <CarteChiffre
                libelle={t("chiffres.rejetees")}
                periode={aCeJour}
                valeur={nombreVue("rejetees")}
                icone={CircleX}
                href="/demandes?groupe=rejetees"
              />
            </GrilleChiffres>
          </section>
          <DemandesRecentes demandes={demandes} libelles={libelles} total={vues?.total ?? 0}>
            {nouvelle}
          </DemandesRecentes>
        </>
      ) : null}

      {profil === "guichet" && indicateurs ? (
        <>
          <GrilleChiffres colonnes={3}>
            <CarteChiffre
              libelle={t("guichet.aExaminer")}
              periode={aCeJour}
              valeur={nombreGroupe("a-examiner")}
              precision={t("guichet.aExaminerPrecision")}
              icone={Inbox}
              href="/a-traiter?groupe=a-examiner"
            />
            <CarteChiffre
              libelle={t("guichet.complements")}
              periode={aCeJour}
              valeur={indicateurs.complementsAttendus}
              precision={t("guichet.complementsPrecision")}
              icone={FolderOpen}
              href={liste({ statut: "complement_requis" })}
            />
            <CarteChiffre
              libelle={t("guichet.frais")}
              periode={aCeJour}
              valeur={indicateurs.fraisAttendus}
              precision={t("guichet.fraisPrecision")}
              icone={Wallet}
              href={liste({ statut: "deposee", fraisPayes: "false" })}
            />
            <CarteChiffre
              libelle={t("guichet.traites")}
              periode={t("periodes.aujourdhui")}
              valeur={indicateurs.traitesAujourdhui}
              precision={t("guichet.traitesPrecision")}
              icone={CircleCheck}
              href={liste({ traiteParMoi: "1" })}
            />
            {carteProches}
            {carteRetard}
          </GrilleChiffres>
          <FileDeTravail travail={travail} libelles={libelles} />
        </>
      ) : null}

      {profil === "instruction" && indicateurs ? (
        <>
          <GrilleChiffres>
            <CarteChiffre
              libelle={t("instruction.avis")}
              periode={aCeJour}
              valeur={nombreGroupe("avis-a-rendre")}
              precision={t("instruction.avisPrecision")}
              icone={Inbox}
              href="/a-traiter?groupe=avis-a-rendre"
            />
            <CarteChiffre
              libelle={t("instruction.sansReponse")}
              periode={aCeJour}
              valeur={nombreGroupe("sans-reponse")}
              precision={t("instruction.sansReponsePrecision")}
              icone={FolderOpen}
              href="/a-traiter?groupe=sans-reponse"
            />
            {carteProches}
            {carteRetard}
          </GrilleChiffres>
          <FileDeTravail travail={travail} libelles={libelles} />
        </>
      ) : null}

      {profil === "decideur" && indicateurs ? (
        <>
          <section aria-labelledby="pilotage" className="flex flex-col gap-4">
            <div className="flex flex-wrap items-end justify-between gap-3">
              <h2 id="pilotage" className="text-xl">
                {t("pilotage.titre")}
              </h2>
              <FiltresPastilles
                libelle={t("periodes.titre")}
                options={PERIODES.map((p) => ({
                  valeur: p,
                  libelle: t(`periodes.${p}`),
                  href: p === "mois" ? "/tableau-de-bord" : `/tableau-de-bord?periode=${p}`,
                  actif: p === periode,
                }))}
              />
            </div>
            {indicateurs.enRetard > 0 ? (
              <Alerte
                nature="avertissement"
                titre={t("pilotage.retard.titre", { nombre: indicateurs.enRetard })}
              >
                <p>{t("pilotage.retard.texte")}</p>
                <Link
                  href={liste({ enRetard: "1" })}
                  className="mt-1 inline-flex min-h-11 items-center font-semibold text-royal underline"
                >
                  {t("pilotage.retard.lien")}
                </Link>
              </Alerte>
            ) : (
              <Alerte nature="succes" titre={t("pilotage.retard.aucun")}>
                {t("pilotage.retard.aucunTexte")}
              </Alerte>
            )}
            {indicateurs.procheDepassement > 0 ? (
              <Alerte
                nature="info"
                titre={t("pilotage.proches.titre", { nombre: indicateurs.procheDepassement })}
              >
                <p>
                  {t("pilotage.proches.texte", {
                    pourcent: indicateurs.parametresDelai.seuilProchePourcent,
                  })}
                </p>
                <Link
                  href={liste({ procheDepassement: "1" })}
                  className="mt-1 inline-flex min-h-11 items-center font-semibold text-royal underline"
                >
                  {t("pilotage.proches.lien")}
                </Link>
              </Alerte>
            ) : null}
            {indicateurs.bloques > 0 ? (
              <Alerte
                nature="avertissement"
                titre={t("pilotage.bloques.titre", { nombre: indicateurs.bloques })}
              >
                <p>{t("pilotage.bloques.texte")}</p>
                <Link
                  href={liste({ bloque: "1" })}
                  className="mt-1 inline-flex min-h-11 items-center font-semibold text-royal underline"
                >
                  {t("pilotage.bloques.lien")}
                </Link>
              </Alerte>
            ) : null}
            {/* Six cartes sur trois colonnes : ce qui attend le décideur, puis le respect des
                délais du catalogue (US-05.6) et l'activité de la période. */}
            <GrilleChiffres colonnes={3}>
              <CarteChiffre
                libelle={t("pilotage.aTraiter")}
                periode={aCeJour}
                valeur={aTraiterTotal}
                precision={t("pilotage.aTraiterPrecision")}
                icone={Stamp}
                href="/a-traiter"
              />
              <CarteChiffre
                libelle={t("pilotage.delivrees")}
                periode={t(`periodes.${periode}`)}
                valeur={indicateurs.delivrees}
                icone={CircleCheck}
                href={liste({ statut: "delivree", periode })}
              />
              <CarteChiffre
                libelle={t("pilotage.dansLeDelai")}
                periode={t(`periodes.${periode}`)}
                valeur={
                  indicateurs.tauxDansLeDelai == null
                    ? t("pilotage.delaiAucun")
                    : t("pilotage.dansLeDelaiValeur", { taux: indicateurs.tauxDansLeDelai })
                }
                precision={
                  indicateurs.delivrees === 0
                    ? t("pilotage.dansLeDelaiAucun")
                    : t("pilotage.dansLeDelaiPrecision", {
                        dans: indicateurs.delivreesDansLeDelai,
                        total: indicateurs.delivrees,
                      })
                }
                icone={Timer}
                href="#repartition"
              />
              <CarteChiffre
                libelle={t("pilotage.rejetees")}
                periode={t(`periodes.${periode}`)}
                valeur={indicateurs.rejetees}
                precision={t("pilotage.rejeteesPrecision")}
                icone={CircleX}
                href={liste({ statut: "rejetee", periode })}
              />
              <CarteChiffre
                libelle={t("pilotage.delai")}
                periode={t(`periodes.${periode}`)}
                valeur={
                  indicateurs.delaiMoyenJours == null
                    ? t("pilotage.delaiAucun")
                    : t("pilotage.delaiValeur", {
                        jours: formaterNombre(indicateurs.delaiMoyenJours, "fr", 1),
                      })
                }
                precision={t("pilotage.delaiPrecision")}
                icone={Clock}
                href="#repartition"
              />
              <CarteChiffre
                libelle={t("pilotage.encaissements")}
                periode={t(`periodes.${periode}`)}
                valeur={formaterGnf(indicateurs.encaissementsGnf)}
                precision={t("pilotage.encaissementsPrecision")}
                icone={Banknote}
                href="#repartition"
              />
            </GrilleChiffres>
          </section>
          <FileDeTravail travail={travail} libelles={libelles} />
          <RepartitionDocuments
            lignes={indicateurs.parDocument}
            libelles={libelles}
            periode={t(`periodes.${periode}`)}
          />
        </>
      ) : null}

      {profil === "autre" && !indisponible ? (
        <>
          <GrilleChiffres>
            {(vues?.vues ?? []).map((v) => (
              <CarteChiffre
                key={v.cle}
                libelle={t(`chiffres.vues.${v.cle}`)}
                periode={aCeJour}
                valeur={v.nombre}
                icone={v.cle === "terminees" ? CircleCheck : v.cle === "rejetees" ? CircleX : Inbox}
                href={`/demandes?groupe=${v.cle}`}
              />
            ))}
          </GrilleChiffres>
          <DemandesRecentes
            demandes={demandes}
            libelles={libelles}
            total={vues?.total ?? 0}
            interne
          />
        </>
      ) : null}
    </div>
  );
}

/** Bloc « Ce que je dois faire » du demandeur : chaque action avec son bouton direct. */
function CeQueJeDoisFaire({
  aFaire,
  libelles,
}: {
  aFaire: ActionAFaire[];
  libelles: Record<string, string>;
}) {
  const t = useTranslations("tableauDeBord.aFaire");
  const tRejet = useTranslations("paiementRejete");
  if (aFaire.length === 0) {
    return (
      <Alerte nature="succes" titre={t("vide")}>
        {t("videAide")}
      </Alerte>
    );
  }
  return (
    <Carte titre={t("titre", { nombre: aFaire.length })}>
      <ul className="-mx-2 flex flex-col divide-y divide-bordure">
        {aFaire.map(({ demande, action, montantGnf, paiementRejete }) => (
          <li
            key={demande.id}
            className={cn(
              "flex flex-col gap-2 px-2 py-3 sm:flex-row sm:items-center sm:justify-between",
              classesLigneCliquable,
            )}
          >
            <span className="min-w-0">
              <Link
                href={`/demandes/${demande.id}`}
                className={cn("block font-semibold text-navy", classesLienEtire)}
              >
                <span className="whitespace-nowrap tabular-nums">{demande.numero}</span> ·{" "}
                {libelles[demande.typeDocumentCode] ?? demande.typeDocumentCode}
              </Link>
              <span className="block text-sm text-texte-doux">
                {montantGnf != null
                  ? t(`explication.${action}`, { montant: formaterGnf(montantGnf) })
                  : t(`explicationSansMontant.${action}`)}
              </span>
              {paiementRejete ? (
                <span className="block text-sm font-semibold text-danger">
                  {tRejet("ligne", {
                    reference: paiementRejete.reference,
                    motif: paiementRejete.motif ?? tRejet("sansMotif"),
                  })}
                </span>
              ) : null}
            </span>
            <Bouton
              asChild
              className={cn("shrink-0 self-start sm:self-auto", classesAuDessusDuLien)}
            >
              <Link
                href={
                  action === "COMPLEMENT"
                    ? `/demandes/${demande.id}`
                    : `/demandes/${demande.id}/paiement`
                }
              >
                {action === "COMPLEMENT" ? null : (
                  <CreditCard aria-hidden="true" className="size-4" />
                )}
                {t(`action.${action}`)} <span className="sr-only">· {demande.numero}</span>
              </Link>
            </Bouton>
          </li>
        ))}
      </ul>
    </Carte>
  );
}

/** Courte liste des dossiers que l'agent doit traiter, avec « Commencer le traitement ». */
function FileDeTravail({
  travail,
  libelles,
}: {
  travail?: { groupe: CleGroupe; dossiers: DemandeDto[]; total: number };
  libelles: Record<string, string>;
}) {
  const t = useTranslations("tableauDeBord.travail");
  if (!travail) return null;
  return (
    <section aria-labelledby="dossiers-a-traiter" className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 id="dossiers-a-traiter" className="text-xl">
          {t("titre", { nombre: travail.total })}
        </h2>
        {travail.dossiers[0] ? (
          <div className="flex flex-wrap items-center gap-3">
            <Link
              href={`/a-traiter?groupe=${travail.groupe}`}
              className="inline-flex min-h-11 items-center text-royal underline"
            >
              {t("voirTout")}
            </Link>
            <Bouton asChild>
              <Link href={adresseFiche(travail.dossiers[0].id, travail.groupe)}>
                <Play aria-hidden="true" className="size-4" />
                {t("commencer", { nombre: travail.total })}
              </Link>
            </Bouton>
          </div>
        ) : null}
      </div>
      {travail.dossiers.length > 0 ? (
        <TableauTravail
          demandes={travail.dossiers}
          libelles={libelles}
          legende={t("titre", { nombre: travail.total })}
          groupe={travail.groupe}
          maintenant={new Date()}
        />
      ) : (
        <EtatVide titre={t("vide")} description={t("videAide")} />
      )}
    </section>
  );
}

/** Demandes récentes, avec le lien vers la liste complète (même total que les cartes). */
function DemandesRecentes({
  demandes,
  libelles,
  total,
  interne = false,
  children,
}: {
  demandes: DemandeDto[];
  libelles: Record<string, string>;
  total: number;
  interne?: boolean;
  children?: ReactNode;
}) {
  const t = useTranslations("tableauDeBord");
  const bloc = interne ? "dossiers" : "demandes";
  return (
    <section aria-labelledby="demandes-recentes" className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 id="demandes-recentes" className="text-xl">
          {t(`${bloc}.titre`)}
        </h2>
        {demandes.length > 0 ? (
          <Link href="/demandes" className="inline-flex min-h-11 items-center text-royal underline">
            {t(`${bloc}.toutes`, { nombre: total })}
          </Link>
        ) : null}
      </div>
      {demandes.length > 0 ? (
        <TableauDemandes
          demandes={demandes.slice(0, DEMANDES_RECENTES)}
          libelles={libelles}
          legende={t(`${bloc}.titre`)}
          interne={interne}
        />
      ) : (
        <EtatVide titre={t(`${bloc}.vide`)} description={t(`${bloc}.aide`)} action={children} />
      )}
    </section>
  );
}
