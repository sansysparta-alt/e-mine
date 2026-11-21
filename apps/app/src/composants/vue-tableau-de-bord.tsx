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
  /** Nombre total de demandes (la liste peut n'en contenir qu'une page). */
  total?: number;
  /** Libellé de chaque document du catalogue, par code. */
  libelles?: Record<string, string>;
};

/** Chiffres clés calculés à partir des demandes. */
export function chiffres(demandes: DemandeDto[]) {
  return {
    brouillons: demandes.filter((d) => d.statut === "brouillon").length,
    aTraiter: demandes.filter((d) => d.statut === "deposee").length,
    enCours: demandes.filter((d) => d.statut !== "brouillon" && !TERMINES.has(d.statut)).length,
    delivrees: demandes.filter((d) => d.statut === "delivree").length,
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
  total,
  libelles = {},
}: VueTableauDeBordProps) {
  const t = useTranslations("tableauDeBord");
  const c = chiffres(demandes);
  const nombre = total ?? demandes.length;
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
