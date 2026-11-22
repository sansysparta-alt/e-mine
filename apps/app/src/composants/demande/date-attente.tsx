import { cn, formaterDate, joursCalendairesDepuis } from "@emine/ui";
import { Hourglass, PauseCircle, TriangleAlert } from "@emine/ui/icones";
import { useTranslations } from "next-intl";

/** Attente à l'étape au-delà de laquelle le dossier est signalé (délai à surveiller). */
export const ATTENTE_LONGUE_JOURS = 5;

export type DateAttenteProps = {
  /** Date de dépôt, ou {@code null} pour un brouillon (date de création affichée). */
  deposeLe: string | null;
  creeLe: string;
  /** Entrée dans le statut actuel (API) : base du temps d'attente à l'étape. */
  etapeDepuis: string;
  /** Afficher le temps d'attente (dossiers en cours seulement). */
  avecAttente: boolean;
  /** Délai du catalogue dépassé (calculé par l'API, même règle que l'indicateur « En retard »). */
  enRetard?: boolean;
  /**
   * Délai du catalogue (US-05.6, calculé par l'API) : proche du dépassement (jours restants) ou
   * suspendu pendant un complément. Montré aux agents seulement.
   */
  delai?: { procheDepassement: boolean; joursRestants: number | null; suspendu: boolean };
  /** Date de référence (rendu serveur, tests). */
  maintenant?: Date;
};

/**
 * Date de la demande et, séparément, le temps d'attente à l'étape (correctifs ergonomie) :
 * « Déposée le 21/11/2026 » puis « En attente depuis 1 jour » (jours calendaires, heure de
 * Conakry), signalé à partir de 5 jours. Plus de « aujourd'hui » ambigu sous une date.
 */
export function DateAttente({
  deposeLe,
  creeLe,
  etapeDepuis,
  avecAttente,
  enRetard = false,
  delai,
  maintenant = new Date(),
}: DateAttenteProps) {
  const t = useTranslations("dates");
  const jours = joursCalendairesDepuis(etapeDepuis, maintenant);
  return (
    <span className="flex flex-col">
      <span>
        {deposeLe
          ? t("deposeeLe", { date: formaterDate(deposeLe) })
          : t("creeeLe", { date: formaterDate(creeLe) })}
      </span>
      {avecAttente ? (
        <span
          className={cn(
            "text-sm",
            jours >= ATTENTE_LONGUE_JOURS ? "font-semibold text-avertissement" : "text-texte-doux",
          )}
        >
          {t("attente", { jours })}
        </span>
      ) : null}
      {enRetard ? (
        <span className="flex items-center gap-1 text-sm font-semibold text-danger">
          <TriangleAlert aria-hidden="true" className="size-4 shrink-0" />
          {t("horsDelai")}
        </span>
      ) : delai?.procheDepassement && delai.joursRestants !== null ? (
        <span className="flex items-center gap-1 text-sm font-semibold text-avertissement">
          <Hourglass aria-hidden="true" className="size-4 shrink-0" />
          {t("echeance", { jours: delai.joursRestants })}
        </span>
      ) : delai?.suspendu ? (
        <span className="flex items-center gap-1 text-sm text-texte-doux">
          <PauseCircle aria-hidden="true" className="size-4 shrink-0" />
          {t("delaiSuspendu")}
        </span>
      ) : null}
    </span>
  );
}
