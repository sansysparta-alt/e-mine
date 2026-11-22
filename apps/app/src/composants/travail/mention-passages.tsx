import { TriangleAlert } from "@emine/ui/icones";
import { useTranslations } from "next-intl";

/**
 * Mention discrète sous le numéro d'un dossier passé dans le traitement en série (correctifs
 * ergonomie) : « Passé 2 fois », ou « Bloqué · passé 3 fois » en rouge avec une icône. Texte, pas un
 * second badge (un seul badge par ligne).
 */
export function MentionPassages({ passages, bloque }: { passages: number; bloque: boolean }) {
  const t = useTranslations("travail.passages");
  if (bloque) {
    return (
      <span className="flex items-center gap-1 text-sm font-semibold text-danger">
        <TriangleAlert aria-hidden="true" className="size-4" />
        {t("bloqueCourt", { nombre: passages })}
      </span>
    );
  }
  if (passages > 0) {
    return <span className="text-sm text-texte-doux">{t("passeNFois", { nombre: passages })}</span>;
  }
  return null;
}
