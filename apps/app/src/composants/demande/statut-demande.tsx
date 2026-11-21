import { BadgeStatut } from "@emine/ui";
import { useTranslations } from "next-intl";
import { tonStatut } from "./statut";

/** Statuts où l'information « frais payés » aide à comprendre où en est la demande (avant la recevabilité). */
const STATUTS_AVANT_RECEVABILITE = new Set(["deposee", "complement_requis"]);

export function afficherFraisPayes(statut: string, fraisPayes: boolean): boolean {
  return fraisPayes && STATUTS_AVANT_RECEVABILITE.has(statut);
}

/**
 * Statut d'une demande avec le code couleur commun et, avant la recevabilité, la mention « Frais
 * payés » (décision du porteur du projet, J3) : la demande reste « Déposée » jusqu'à la décision du
 * guichet, le paiement en est la condition.
 */
export function StatutDemande({ statut, fraisPayes }: { statut: string; fraisPayes: boolean }) {
  const tStatut = useTranslations("statuts");
  const t = useTranslations("detail");
  return (
    <span className="inline-flex flex-wrap items-center gap-2">
      <BadgeStatut ton={tonStatut(statut)}>
        {tStatut.has(statut) ? tStatut(statut) : statut}
      </BadgeStatut>
      {afficherFraisPayes(statut, fraisPayes) ? (
        <BadgeStatut ton="succes">{t("fraisPayes")}</BadgeStatut>
      ) : null}
    </span>
  );
}
