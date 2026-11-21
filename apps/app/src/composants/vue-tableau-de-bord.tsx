import type { DemandeDto } from "@emine/api-client";
import { Bouton, CarteChiffre, EnTetePage, EtatVide, GrilleChiffres } from "@emine/ui";
import { CircleCheck, FilePen, FileText, Hourglass } from "@emine/ui/icones";
import Link from "next/link";
import { useTranslations } from "next-intl";
import { TableauDemandes } from "./demande/tableau-demandes";

/** Nombre de demandes récentes affichées sur le tableau de bord. */
export const DEMANDES_RECENTES = 5;

const TERMINES = new Set(["delivree", "rejetee", "archivee"]);

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
      <GrilleChiffres>
        {interne ? (
          <>
            <CarteChiffre
              libelle={t("chiffres.aTraiter")}
              valeur={c.aTraiter}
              icone={Hourglass}
              href="/demandes?statut=deposee"
            />
            <CarteChiffre libelle={t("chiffres.enCours")} valeur={c.enCours} icone={FileText} />
            <CarteChiffre
              libelle={t("chiffres.delivrees")}
              valeur={c.delivrees}
              icone={CircleCheck}
            />
            <CarteChiffre
              libelle={t("chiffres.total")}
              valeur={nombre}
              icone={FileText}
              href="/demandes"
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
              href="/demandes?statut=brouillon"
            />
            <CarteChiffre libelle={t("chiffres.enCours")} valeur={c.enCours} icone={Hourglass} />
            <CarteChiffre
              libelle={t("chiffres.delivrees")}
              valeur={c.delivrees}
              icone={CircleCheck}
            />
          </>
        )}
      </GrilleChiffres>
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
              {t("demandes.toutes", { nombre })}
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
    </div>
  );
}
