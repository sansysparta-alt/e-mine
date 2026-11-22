import type { DemandeDto } from "@emine/api-client";
import { BadgeStatut, type ComposantLien, formaterDate, TableauDonnees } from "@emine/ui";
import Link from "next/link";
import { useTranslations } from "next-intl";
import { actionPour, adresseFiche, type CleGroupe, joursAttente } from "./file-travail";

export type TableauTravailProps = {
  demandes: DemandeDto[];
  /** Libellé de chaque document du catalogue, par code. */
  libelles: Record<string, string>;
  legende: string;
  /** Groupe de la file : la fiche s'ouvre en traitement en série dans ce groupe. */
  groupe: CleGroupe;
  /** Date de référence du calcul de l'attente (rendu serveur). */
  maintenant: Date;
};

const LienNext: ComposantLien = ({ href, children, ...props }) => (
  <Link href={href} {...props}>
    {children}
  </Link>
);

/** Attente au-delà de laquelle le dossier est signalé (délai à surveiller). */
const ATTENTE_LONGUE_JOURS = 5;

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
}: TableauTravailProps) {
  const t = useTranslations("travail");
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
          rendu: (d) => <span className="font-semibold">{d.numero}</span>,
        },
        {
          cle: "document",
          entete: t("colonnes.document"),
          rendu: (d) => libelles[d.typeDocumentCode] ?? d.typeDocumentCode,
        },
        {
          cle: "demandeur",
          entete: t("colonnes.demandeur"),
          rendu: (d) => (
            <span className="flex flex-col">
              <span>
                {d.demandeur.prenom} {d.demandeur.nom}
              </span>
              {d.demandeur.organisation ? (
                <span className="text-sm text-texte-doux">{d.demandeur.organisation}</span>
              ) : null}
            </span>
          ),
        },
        {
          cle: "depot",
          entete: t("colonnes.depot"),
          rendu: (d) => {
            if (!d.deposeLe) return "—";
            const jours = joursAttente(d.deposeLe, maintenant);
            return (
              <span className="flex flex-col">
                <span>{formaterDate(d.deposeLe)}</span>
                <span
                  className={
                    jours >= ATTENTE_LONGUE_JOURS
                      ? "text-sm font-semibold text-avertissement"
                      : "text-sm text-texte-doux"
                  }
                >
                  {t("attente", { jours })}
                </span>
              </span>
            );
          },
        },
        {
          cle: "action",
          entete: t("colonnes.action"),
          rendu: (d) => {
            const action = actionPour(d.statut, d.fraisPayes);
            return action ? (
              <BadgeStatut ton="avertissement">{t(`actions.${action}`)}</BadgeStatut>
            ) : (
              <BadgeStatut ton="neutre">{t("actions.fraisAttendus")}</BadgeStatut>
            );
          },
        },
      ]}
    />
  );
}
