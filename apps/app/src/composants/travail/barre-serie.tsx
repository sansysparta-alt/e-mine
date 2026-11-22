import { ArrowLeft, ArrowRight, X } from "@emine/ui/icones";
import Link from "next/link";
import { useTranslations } from "next-intl";
import { adresseFiche, type CleGroupe } from "./file-travail";
import { PasserDossier } from "./passer-dossier";

export type BarreSerieProps = {
  /** Dossier ouvert (celui qu'on peut passer). */
  demandeId: number;
  groupe: CleGroupe;
  /** Position du dossier dans la file (1 = le plus ancien) ; null s'il n'y est plus. */
  position: number | null;
  total: number;
  precedent: number | null;
  suivant: number | null;
};

const classesLien =
  "inline-flex min-h-11 items-center gap-1 rounded-lg px-3 font-semibold text-royal hover:bg-ice";

/**
 * Barre du traitement en série (correctifs J5) : où l'on en est dans la file (« Dossier 2 sur 7 »),
 * revenir au précédent, passer ce dossier (avec un motif tracé), quitter le traitement. Toujours visible en haut de
 * la fiche, au clavier comme à la souris.
 */
export function BarreSerie({
  demandeId,
  groupe,
  position,
  total,
  precedent,
  suivant,
}: BarreSerieProps) {
  const t = useTranslations("travail.serie");
  return (
    <nav
      aria-label={t("libelle")}
      className="flex flex-col gap-2 rounded-carte border border-bordure bg-white px-4 py-2 sm:flex-row sm:items-center sm:justify-between"
    >
      <p className="font-semibold text-navy">
        {t("groupe", { groupe: t(`groupes.${groupe}`) })}
        <span className="font-normal text-texte-doux">
          {" · "}
          {position != null ? t("position", { position, total }) : t("horsFile", { total })}
        </span>
      </p>
      <div className="flex flex-wrap items-center gap-1">
        {precedent != null ? (
          <Link href={adresseFiche(precedent, groupe)} className={classesLien}>
            <ArrowLeft aria-hidden="true" className="size-4" />
            {t("precedent")}
          </Link>
        ) : null}
        {suivant != null && position != null ? (
          // Passer un dossier de la file : motif court, tracé (correctifs ergonomie).
          <PasserDossier
            demandeId={demandeId}
            groupe={groupe}
            suivant={suivant}
            classes={classesLien}
          />
        ) : suivant != null ? (
          <Link href={adresseFiche(suivant, groupe)} className={classesLien}>
            {t("suivant")}
            <ArrowRight aria-hidden="true" className="size-4" />
          </Link>
        ) : null}
        <Link href={`/a-traiter?groupe=${groupe}`} className={classesLien}>
          <X aria-hidden="true" className="size-4" />
          {t("quitter")}
        </Link>
      </div>
    </nav>
  );
}
