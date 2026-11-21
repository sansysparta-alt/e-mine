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
};

/**
 * Motif communiqué au demandeur (non-recevabilité, rejet) : zone de texte avec aide, compteur de
 * caractères et erreur reliée au champ.
 */
export function ChampMotif({ id, libelle, aide, valeur, surChangement, erreur }: ChampMotifProps) {
  const t = useTranslations("instruction.motif");
  const longueur = valeur.trim().length;
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="font-semibold">
        {libelle}{" "}
        <span aria-hidden="true" className="text-danger">
          *
        </span>
        <span className="sr-only">({t("obligatoire")})</span>
      </label>
      <p id={`${id}-aide`} className="text-sm text-texte-doux">
        {aide}
      </p>
      <textarea
        id={id}
        value={valeur}
        onChange={(e) => surChangement(e.target.value)}
        rows={5}
        required
        aria-invalid={erreur ? true : undefined}
        aria-describedby={`${id}-aide ${id}-compteur${erreur ? ` ${id}-erreur` : ""}`}
        className={cn(classesChamp, "min-h-32 resize-y")}
      />
      <p
        id={`${id}-compteur`}
        className={cn(
          "text-right text-sm",
          longueur > MOTIF_MAX ? "font-semibold text-danger" : "text-texte-doux",
        )}
      >
        {t("compteur", { nombre: longueur, max: MOTIF_MAX })}
      </p>
      {erreur ? (
        <p id={`${id}-erreur`} className="font-semibold text-danger">
          {erreur}
        </p>
      ) : null}
    </div>
  );
}
