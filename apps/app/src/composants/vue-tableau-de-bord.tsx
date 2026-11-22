import type { DemandeDto } from "@emine/api-client";
import {
  Bouton,
  Carte,
  CarteChiffre,
  classesAuDessusDuLien,
  classesLienEtire,
  classesLigneCliquable,
  cn,
  EnTetePage,
  EtatVide,
  formaterGnf,
  GrilleChiffres,
} from "@emine/ui";
import {
  CircleCheck,
  CreditCard,
  FilePen,
  FileSignature,
  FileText,
  Hourglass,
  Inbox,
  Play,
  Stamp,
} from "@emine/ui/icones";
import Link from "next/link";
import { useTranslations } from "next-intl";
import { TableauDemandes } from "./demande/tableau-demandes";
import { adresseFiche, type CleGroupe } from "./travail/file-travail";
import { TableauTravail } from "./travail/tableau-travail";

/** Nombre de demandes récentes affichées sur le tableau de bord. */
export const DEMANDES_RECENTES = 5;

const TERMINES = new Set(["delivree", "rejetee", "archivee"]);

/** Action attendue du demandeur sur une de ses demandes (bloc « À faire maintenant »). */
export type ActionAFaire = {
  demande: DemandeDto;
  action: "FRAIS" | "DROITS" | "COMPLEMENT";
  /** Montant à payer en GNF, s'il est connu. */
  montantGnf?: number | null;
};

/** Tâche d'un agent, avec son nombre et sa file de travail. */
export type TacheAgent = { cle: "aExaminer" | "decisions" | "aSigner"; valeur: number };

const TACHES: Record<TacheAgent["cle"], { href: string; icone: typeof Inbox }> = {
  aExaminer: { href: "/a-traiter?groupe=a-examiner", icone: Inbox },
  decisions: { href: "/a-traiter?groupe=a-decider", icone: Stamp },
  aSigner: { href: "/a-traiter?groupe=a-signer", icone: FileSignature },
};

/** Déduit l'action attendue du demandeur d'après le statut (et les frais payés). */
export function actionAttendue(d: DemandeDto): ActionAFaire["action"] | null {
  if (d.statut === "deposee" && !d.fraisPayes) return "FRAIS";
  if (d.statut === "decision") return "DROITS";
  if (d.statut === "complement_requis") return "COMPLEMENT";
  return null;
}

export type VueTableauDeBordProps = {
  prenom: string;
  nom: string;
  profilLibelle: string;
  interne: boolean;
  /** Droit « déposer » (US-01.6) : affiche l'action de nouvelle demande. */
  peutDeposer?: boolean;
  /** Demandes visibles (les siennes pour un usager, toutes pour un agent), la plus récente d'abord. */
  demandes?: DemandeDto[];
  /** Nombre de demandes visibles par statut (API, tableau de bord). */
  compteurs?: Record<string, number>;
  /** Libellé de chaque document du catalogue, par code. */
  libelles?: Record<string, string>;
  /** Demandeur : ce qu'il doit faire maintenant (payer, compléter). */
  aFaire?: ActionAFaire[];
  /** Agent : ses files de travail selon ses droits (guichet, décisions). */
  taches?: TacheAgent[];
  /**
   * Agent qui instruit ou signe : les premiers dossiers de sa file de travail (correctifs J5), au
   * lieu de toutes les demandes ; le tableau de bord propose de commencer le traitement.
   */
  travail?: { groupe: CleGroupe; dossiers: DemandeDto[]; total: number };
};

/** Compteurs par statut déduits d'une liste (quand l'API ne les fournit pas). */
function compteursDe(demandes: DemandeDto[]): Record<string, number> {
  const c: Record<string, number> = {};
  for (const d of demandes) c[d.statut] = (c[d.statut] ?? 0) + 1;
  return c;
}

/** Chiffres clés calculés à partir des compteurs par statut. */
export function chiffres(compteurs: Record<string, number>) {
  const n = (s: string) => compteurs[s] ?? 0;
  const total = Object.values(compteurs).reduce((a, b) => a + b, 0);
  const enCours = Object.entries(compteurs)
    .filter(([s]) => s !== "brouillon" && !TERMINES.has(s))
    .reduce((a, [, nb]) => a + nb, 0);
  return {
    total,
    brouillons: n("brouillon"),
    aTraiter: n("deposee"),
    enCours,
    delivrees: n("delivree"),
  };
}

/**
 * Tableau de bord sur le gabarit du socle : en-tête de page, chiffres clés adaptés au profil,
 * demandes récentes.
 */
export function VueTableauDeBord({
  prenom,
  nom,
  profilLibelle,
  interne,
  peutDeposer = false,
  demandes = [],
  compteurs,
  libelles = {},
  aFaire = [],
  taches = [],
  travail,
}: VueTableauDeBordProps) {
  const t = useTranslations("tableauDeBord");
  const c = chiffres(compteurs ?? compteursDe(demandes));
  const nombre = c.total;
  const nouvelle = peutDeposer ? (
    <Bouton asChild>
      <Link href="/demandes/nouvelle">{t("demandes.nouvelle")}</Link>
    </Bouton>
  ) : null;
  const bloc = interne ? "dossiers" : "demandes";

  return (
    <div className="flex flex-col gap-6">
      <EnTetePage
        titre={t("bonjour", { prenom, nom })}
        description={t("profil", { libelle: profilLibelle })}
        actions={nouvelle}
      />
      {!interne && aFaire.length > 0 ? (
        <Carte titre={t("aFaire.titre", { nombre: aFaire.length })}>
          <ul className="-mx-2 flex flex-col divide-y divide-bordure">
            {aFaire.map(({ demande, action, montantGnf }) => (
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
                    {demande.numero} ·{" "}
                    {libelles[demande.typeDocumentCode] ?? demande.typeDocumentCode}
                  </Link>
                  <span className="block text-sm text-texte-doux">
                    {montantGnf != null
                      ? t(`aFaire.explication.${action}`, { montant: formaterGnf(montantGnf) })
                      : t(`aFaire.explicationSansMontant.${action}`)}
                  </span>
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
                    {t(`aFaire.action.${action}`)}{" "}
                    <span className="sr-only">· {demande.numero}</span>
                  </Link>
                </Bouton>
              </li>
            ))}
          </ul>
        </Carte>
      ) : null}
      <GrilleChiffres>
        {interne ? (
          <>
            {taches.length > 0 ? (
              taches.map((tache) => (
                <CarteChiffre
                  key={tache.cle}
                  libelle={t(`taches.${tache.cle}`)}
                  valeur={tache.valeur}
                  precision={t(`taches.${tache.cle}Precision`)}
                  icone={TACHES[tache.cle].icone}
                  href={TACHES[tache.cle].href}
                />
              ))
            ) : (
              <CarteChiffre
                libelle={t("chiffres.aTraiter")}
                valeur={c.aTraiter}
                icone={Hourglass}
                href="/demandes?groupe=aTraiter"
              />
            )}
            <CarteChiffre
              libelle={t("chiffres.enCours")}
              valeur={c.enCours}
              icone={FileText}
              href="/demandes?groupe=enCours"
            />
            <CarteChiffre
              libelle={t("chiffres.delivrees")}
              valeur={c.delivrees}
              icone={CircleCheck}
              href="/demandes?statut=delivree"
            />
          </>
        ) : (
          <>
            <CarteChiffre
              libelle={t("chiffres.mesDemandes")}
              valeur={nombre}
              icone={FileText}
              href="/demandes"
            />
            <CarteChiffre
              libelle={t("chiffres.brouillons")}
              valeur={c.brouillons}
              icone={FilePen}
              href="/demandes?groupe=brouillons"
            />
            <CarteChiffre
              libelle={t("chiffres.enCours")}
              valeur={c.enCours}
              icone={Hourglass}
              href="/demandes?groupe=enCours"
            />
            <CarteChiffre
              libelle={t("chiffres.delivrees")}
              valeur={c.delivrees}
              icone={CircleCheck}
              href="/demandes?statut=delivree"
            />
          </>
        )}
      </GrilleChiffres>
      {interne && travail ? (
        <section aria-labelledby="dossiers-a-traiter" className="flex flex-col gap-3">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <h2 id="dossiers-a-traiter" className="text-xl">
              {t("travail.titre", { nombre: travail.total })}
            </h2>
            {travail.dossiers[0] ? (
              <div className="flex flex-wrap items-center gap-3">
                <Link
                  href={`/a-traiter?groupe=${travail.groupe}`}
                  className="inline-flex min-h-11 items-center text-royal underline"
                >
                  {t("travail.voirTout")}
                </Link>
                <Bouton asChild>
                  <Link href={adresseFiche(travail.dossiers[0].id, travail.groupe)}>
                    <Play aria-hidden="true" className="size-4" />
                    {t("travail.commencer", { nombre: travail.total })}
                  </Link>
                </Bouton>
              </div>
            ) : null}
          </div>
          {travail.dossiers.length > 0 ? (
            <TableauTravail
              demandes={travail.dossiers}
              libelles={libelles}
              legende={t("travail.titre", { nombre: travail.total })}
              groupe={travail.groupe}
              maintenant={new Date()}
            />
          ) : (
            <EtatVide titre={t("travail.vide")} description={t("travail.videAide")} />
          )}
        </section>
      ) : (
        <section aria-labelledby="demandes-recentes" className="flex flex-col gap-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h2 id="demandes-recentes" className="text-xl">
              {t(`${bloc}.titre`)}
            </h2>
            {demandes.length > 0 ? (
              <Link
                href="/demandes"
                className="inline-flex min-h-11 items-center text-royal underline"
              >
                {interne ? t("dossiers.toutes", { nombre }) : t("demandes.toutes", { nombre })}
              </Link>
            ) : null}
          </div>
          {demandes.length > 0 ? (
            <TableauDemandes
              demandes={demandes.slice(0, DEMANDES_RECENTES)}
              libelles={libelles}
              legende={t(`${bloc}.titre`)}
            />
          ) : (
            <EtatVide titre={t(`${bloc}.vide`)} description={t(`${bloc}.aide`)} action={nouvelle} />
          )}
        </section>
      )}
    </div>
  );
}
