"use client";

import { classesChamp, cn } from "@emine/ui";
import { useTranslations } from "next-intl";

/** Bornes du motif, identiques à celles de l'API (10 à 2000 caractères hors espaces de bord). */
export const MOTIF_MIN = 10;
export const MOTIF_MAX = 2000;

/** Clé d'erreur du motif (`instruction.motif.erreurs.<cle>`), ou null s'il convient. */
export function erreurMotif(motif: string): "court" | "long" | null {
  const longueur = motif.trim().length;
  if (longueur < MOTIF_MIN) return "court";
  if (longueur > MOTIF_MAX) return "long";
  return null;
}

export type ChampMotifProps = {
  id: string;
  libelle: string;
  aide: string;
  valeur: string;
  surChangement: (valeur: string) => void;
  /** Message d'erreur affiché sous le champ et relié par `aria-describedby`. */
  erreur?: string;
  /** Champ obligatoire (motif d'un refus) ou facultatif (observations) ; obligatoire par défaut. */
  obligatoire?: boolean;
  /** Nombre maximal de caractères (compteur) ; celui des motifs communiqués par défaut. */
  max?: number;
  /**
   * Aperçu du motif tel qu'il sera lu, avant de confirmer (US-04.13) : titre de l'aperçu (« Le
   * demandeur lira ce motif »…). Affiché dès que le motif n'est pas vide.
   */
  apercu?: string;
};

/**
 * Motif communiqué au demandeur (non-recevabilité, rejet) : zone de texte avec aide, compteur de
 * caractères, erreur reliée au champ et, si demandé, aperçu du texte tel qu'il sera lu (US-04.13).
 */
export function ChampMotif({
  id,
  libelle,
  aide,
  valeur,
  surChangement,
  erreur,
  obligatoire = true,
  max = MOTIF_MAX,
  apercu,
}: ChampMotifProps) {
  const t = useTranslations("instruction.motif");
  const longueur = valeur.trim().length;
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="font-semibold">
        {libelle}
        {obligatoire ? (
          <>
            {" "}
            <span aria-hidden="true" className="text-danger">
              *
            </span>
            <span className="sr-only">({t("obligatoire")})</span>
          </>
        ) : (
          <span className="font-normal text-texte-doux"> ({t("facultatif")})</span>
        )}
      </label>
      <p id={`${id}-aide`} className="text-sm text-texte-doux">
        {aide}
      </p>
      <textarea
        id={id}
        value={valeur}
        onChange={(e) => surChangement(e.target.value)}
        rows={max < 500 ? 3 : 5}
        required={obligatoire}
        aria-invalid={erreur ? true : undefined}
        aria-describedby={`${id}-aide ${id}-compteur${erreur ? ` ${id}-erreur` : ""}`}
        className={cn(classesChamp, max < 500 ? "min-h-20 resize-y" : "min-h-32 resize-y")}
      />
      <p
        id={`${id}-compteur`}
        className={cn(
          "text-right text-sm",
          longueur > max ? "font-semibold text-danger" : "text-texte-doux",
        )}
      >
        {t("compteur", { nombre: longueur, max })}
      </p>
      {erreur ? (
        <p id={`${id}-erreur`} className="font-semibold text-danger">
          {erreur}
        </p>
      ) : null}
      {apercu && valeur.trim() ? (
        <div className="mt-2 flex flex-col gap-1">
          <p className="text-sm font-semibold">{apercu}</p>
          <blockquote className="rounded-lg border border-bordure bg-fond px-3 py-2 break-words whitespace-pre-line">
            {valeur.trim()}
          </blockquote>
        </div>
      ) : null}
    </div>
  );
}
