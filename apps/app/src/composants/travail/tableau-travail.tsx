import type { DemandeDto } from "@emine/api-client";
import { type ComposantLien, TableauDonnees, TexteSurligne } from "@emine/ui";
import Link from "next/link";
import { useTranslations } from "next-intl";
import { DateAttente } from "../demande/date-attente";
import { cleAttente } from "../demande/statut";
import { StatutDemande } from "../demande/statut-demande";
import { MentionPassages } from "./mention-passages";
import { adresseFiche, type CleGroupe } from "./file-travail";

export type TableauTravailProps = {
  demandes: DemandeDto[];
  /** Libellé de chaque document du catalogue, par code. */
  libelles: Record<string, string>;
  legende: string;
  /** Groupe de la file : la fiche s'ouvre en traitement en série dans ce groupe. */
  groupe: CleGroupe;
  /** Date de référence du calcul de l'attente (rendu serveur). */
  maintenant: Date;
  /** Texte recherché : parties trouvées surlignées. */
  recherche?: string;
};

const LienNext: ComposantLien = ({ href, children, ...props }) => (
  <Link href={href} {...props}>
    {children}
  </Link>
);

/**
 * Dossiers d'une file de travail : numéro, document, demandeur, dépôt et attente, **action
 * attendue** (badge orange : il faut agir) ; la ligne entière ouvre le dossier en traitement en
 * série.
 */
export function TableauTravail({
  demandes,
  libelles,
  legende,
  groupe,
  maintenant,
  recherche = "",
}: TableauTravailProps) {
  const t = useTranslations("travail");
  const surligne = (texte: string) => <TexteSurligne texte={texte} recherche={recherche} />;
  return (
    <TableauDonnees
      legende={legende}
      lignes={demandes}
      cleLigne={(d) => d.id}
      Lien={LienNext}
      lienLigne={(d) => adresseFiche(d.id, groupe)}
      colonnes={[
        {
          cle: "numero",
          entete: t("colonnes.numero"),
          rendu: (d) => (
            <span className="flex flex-col">
              <span className="font-semibold tabular-nums">{surligne(d.numero ?? "")}</span>
              <MentionPassages passages={d.passages} bloque={d.bloque} />
            </span>
          ),
        },
        {
          cle: "document",
          entete: t("colonnes.document"),
          rendu: (d) => surligne(libelles[d.typeDocumentCode] ?? d.typeDocumentCode),
        },
        {
          cle: "demandeur",
          entete: t("colonnes.demandeur"),
          rendu: (d) => (
            <span className="flex flex-col">
              <span>{surligne(`${d.demandeur.prenom} ${d.demandeur.nom}`)}</span>
              {d.demandeur.organisation ? (
                <span className="text-sm text-texte-doux">
                  {surligne(d.demandeur.organisation)}
                </span>
              ) : null}
            </span>
          ),
        },
        {
          cle: "depot",
          entete: t("colonnes.depot"),
          rendu: (d) => (
            <DateAttente
              deposeLe={d.deposeLe}
              creeLe={d.creeLe}
              etapeDepuis={d.etapeDepuis}
              cleAttente={cleAttente(d, true)}
              avecAttente
              enRetard={d.enRetard}
              delai={{
                procheDepassement: d.procheDepassement,
                joursRestants: d.joursRestants,
                suspendu: d.delaiSuspendu,
                motifSuspension: d.motifSuspension,
              }}
              maintenant={maintenant}
            />
          ),
        },
        {
          cle: "statut",
          entete: t("colonnes.statut"),
          // Table unique des libellés par profil : orange seulement si l'API dit que le dossier
          // attend le profil connecté (aTraiterParMoi), comme partout ailleurs.
          rendu: (d) => <StatutDemande demande={d} interne />,
        },
      ]}
    />
  );
}
