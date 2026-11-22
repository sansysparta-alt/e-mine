import { cn, formaterDate, joursCalendairesDepuis } from "@emine/ui";
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
    </span>
  );
}
