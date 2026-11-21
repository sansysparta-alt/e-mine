import type { DemandeDto } from "@emine/api-client";
import { BadgeStatut, type ComposantLien, formaterDate, TableauDonnees } from "@emine/ui";
import Link from "next/link";
import { useTranslations } from "next-intl";
import { joursAttente } from "./file-guichet";

export type TableauFileProps = {
  demandes: DemandeDto[];
  /** Libellé de chaque document du catalogue, par code. */
  libelles: Record<string, string>;
  legende: string;
  /** Date de référence du calcul de l'attente (rendu serveur). */
  maintenant: Date;
  /** Colonne « Frais » (file du guichet) ; inutile une fois la demande recevable. */
  avecFrais?: boolean;
  /** File d'origine, pour que la fiche propose d'y revenir. */
  depuis: "guichet" | "decisions";
};

const LienNext: ComposantLien = ({ href, children, ...props }) => (
  <Link href={href} {...props}>
    {children}
  </Link>
);

/** Attente au-delà de laquelle la demande est signalée (délai de traitement à surveiller). */
const ATTENTE_LONGUE_JOURS = 5;

/**
 * File du guichet (US-05.1) ou des décisions (US-05.4) : numéro, document, demandeur, dépôt et
 * attente, frais (guichet seulement) ; la ligne entière ouvre la fiche, où l'agent agit.
 */
export function TableauFile({
  demandes,
  libelles,
  legende,
  maintenant,
  avecFrais = true,
  depuis,
}: TableauFileProps) {
  const t = useTranslations("instruction.file");
  return (
    <TableauDonnees
      legende={legende}
      lignes={demandes}
      cleLigne={(d) => d.id}
      Lien={LienNext}
      lienLigne={(d) => `/demandes/${d.id}?depuis=${depuis}`}
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
        ...(avecFrais
          ? [
              {
                cle: "frais",
                entete: t("colonnes.frais"),
                rendu: (d: DemandeDto) =>
                  d.fraisPayes ? (
                    <BadgeStatut ton="succes">{t("fraisPayes")}</BadgeStatut>
                  ) : (
                    <BadgeStatut ton="avertissement">{t("fraisAttendus")}</BadgeStatut>
                  ),
              },
            ]
          : []),
      ]}
    />
  );
}
