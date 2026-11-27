import type { DemandeDto } from "@emine/api-client";
import {
  type ColonneTableau,
  type ComposantLien,
  type SensTri,
  TableauDonnees,
  TexteSurligne,
} from "@emine/ui";
import Link from "next/link";
import { useTranslations } from "next-intl";
import { MentionPassages } from "../travail/mention-passages";
import { ActionsDemande } from "./actions-demande";
import { DateAttente } from "./date-attente";
import { cleAttente } from "./statut";
import { StatutDemande } from "./statut-demande";

/** Statuts pour lesquels le temps d'attente à l'étape a un sens. */
const STATUTS_EN_COURS = new Set([
  "deposee",
  "complement_requis",
  "recevable",
  "en_instruction",
  "avis_rendu",
  "decision",
  "droits_payes",
]);

export type TableauDemandesProps = {
  demandes: DemandeDto[];
  /** Libellé de chaque document du catalogue, par code. */
  libelles: Record<string, string>;
  legende: string;
  tri?: { colonne: string; sens: SensTri };
  lienTri?: (colonne: string) => string;
  /** Colonnes triables (selon ce que l'API sait trier). */
  triables?: string[];
  /**
   * Profil interne : libellés de statut de l'agent et colonne Demandeur ; sinon libellés du
   * demandeur et colonne Actions (reprendre ou supprimer un brouillon, télécharger un document).
   */
  interne?: boolean;
  /** Texte recherché : parties trouvées surlignées (numéro, document, demandeur). */
  recherche?: string;
};

const LienNext: ComposantLien = ({ href, children, ...props }) => (
  <Link href={href} {...props}>
    {children}
  </Link>
);

/** Adresse d'une demande : le formulaire pour un brouillon, la fiche sinon. */
export function lienDemande(d: Pick<DemandeDto, "id" | "statut">): string {
  return d.statut === "brouillon" ? `/demandes/${d.id}/modifier` : `/demandes/${d.id}`;
}

/**
 * Tableau des demandes (US-03.5, correctifs ergonomie) : mêmes colonnes de base que « À traiter »
 * (numéro, document, demandeur pour les agents, dépôt et attente à l'étape, statut selon le
 * profil) ; pour le demandeur, une colonne d'actions directes. La ligne entière ouvre la demande
 * (formulaire pour un brouillon, fiche sinon) ; les actions passent au-dessus du lien de ligne.
 */
export function TableauDemandes({
  demandes,
  libelles,
  legende,
  tri,
  lienTri,
  triables = [],
  interne = false,
  recherche = "",
}: TableauDemandesProps) {
  const t = useTranslations("mesDemandes");
  const surligne = (texte: string) => <TexteSurligne texte={texte} recherche={recherche} />;
  const colonnes: ColonneTableau<DemandeDto>[] = [
    {
      cle: "numero",
      entete: t("colonnes.numero"),
      triable: triables.includes("numero"),
      rendu: (d) => (
        <span className="flex flex-col">
          <span className="font-semibold whitespace-nowrap tabular-nums">
            {surligne(d.numero ?? t("brouillon"))}
          </span>
          {interne ? <MentionPassages passages={d.passages} bloque={d.bloque} /> : null}
        </span>
      ),
    },
    {
      cle: "document",
      entete: t("colonnes.document"),
      rendu: (d) => surligne(libelles[d.typeDocumentCode] ?? d.typeDocumentCode),
    },
  ];
  // Le statut suit le numéro : c'est ce qu'on cherche d'abord en parcourant la liste. Sous le
  // badge, l'avancement dans le circuit (« Étape 3 sur 6 »), calculé par l'API.
  colonnes.splice(1, 0, {
    cle: "statut",
    entete: t("colonnes.statut"),
    triable: triables.includes("statut"),
    rendu: (d) => (
      <span className="flex flex-col items-start gap-1">
        <StatutDemande demande={d} interne={interne} />
        {d.avancement ? (
          <span className="text-sm text-texte-doux">
            {t("avancement", { etape: d.avancement.etape, total: d.avancement.total })}
          </span>
        ) : null}
      </span>
    ),
  });
  if (interne) {
    colonnes.push({
      cle: "demandeur",
      entete: t("colonnes.demandeur"),
      rendu: (d) => (
        <span className="flex flex-col">
          <span>{surligne(`${d.demandeur.prenom} ${d.demandeur.nom}`)}</span>
          {d.demandeur.organisation ? (
            <span className="text-sm text-texte-doux">{surligne(d.demandeur.organisation)}</span>
          ) : null}
        </span>
      ),
    });
  }
  colonnes.push({
    cle: "date",
    entete: t("colonnes.date"),
    triable: triables.includes("date"),
    rendu: (d) => (
      <DateAttente
        deposeLe={d.deposeLe}
        creeLe={d.creeLe}
        etapeDepuis={d.etapeDepuis}
        cleAttente={cleAttente(d, interne)}
        avecAttente={STATUTS_EN_COURS.has(d.statut)}
        enRetard={interne && d.enRetard}
        delai={
          interne
            ? {
                procheDepassement: d.procheDepassement,
                joursRestants: d.joursRestants,
                suspendu: d.delaiSuspendu,
                motifSuspension: d.motifSuspension,
              }
            : undefined
        }
      />
    ),
  });
  if (!interne) {
    colonnes.push({
      cle: "actions",
      entete: t("colonnes.actions"),
      interactive: true,
      rendu: (d) => <ActionsDemande demande={d} />,
    });
  }
  return (
    <TableauDonnees
      legende={legende}
      lignes={demandes}
      cleLigne={(d) => d.id}
      tri={tri}
      lienTri={lienTri}
      Lien={LienNext}
      lienLigne={lienDemande}
      libellesTri={{
        trier: t("tri.trier"),
        croissant: t("tri.croissant"),
        decroissant: t("tri.decroissant"),
      }}
      colonnes={colonnes}
    />
  );
}
