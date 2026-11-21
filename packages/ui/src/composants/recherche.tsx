"use client";

import { Loader2, Search } from "lucide-react";
import { useEffect, useRef } from "react";
import { classesChamp } from "../champ";
import { cn } from "../cn";

/** Délai entre la dernière frappe et la recherche (ms). */
export const DELAI_RECHERCHE_MS = 300;

export type ChampRechercheProps = {
  libelle: string;
  nom?: string;
  valeur?: string;
  placeholder?: string;
  boutonLibelle: string;
  /** Paramètres à conserver lors de la recherche (filtres, tri). */
  conserver?: Record<string, string | undefined>;
  /**
   * Recherche pendant la saisie : appelé après un court délai sans frappe, avec le texte nettoyé.
   * Sans ce rappel, la recherche part à la validation du formulaire (fonctionne sans JavaScript).
   */
  surRecherche?: (texte: string) => void;
  /** Recherche en cours (indicateur discret dans le champ). */
  enCours?: boolean;
  delaiMs?: number;
};

/**
 * Champ de recherche du socle : formulaire GET (Entrée, sans JavaScript) et, avec `surRecherche`,
 * résultats mis à jour pendant la saisie après un court délai. Le bouton de validation reste
 * présent pour les lecteurs d'écran et la touche Entrée, mais n'est plus affiché.
 */
export function ChampRecherche({
  libelle,
  nom = "q",
  valeur,
  placeholder,
  boutonLibelle,
  conserver = {},
  surRecherche,
  enCours = false,
  delaiMs = DELAI_RECHERCHE_MS,
}: ChampRechercheProps) {
  const minuterie = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  useEffect(() => () => clearTimeout(minuterie.current), []);

  return (
    <form
      method="get"
      role="search"
      className="flex w-full max-w-md gap-2"
      onSubmit={(e) => {
        if (!surRecherche) return;
        e.preventDefault();
        clearTimeout(minuterie.current);
        const champ = e.currentTarget.elements.namedItem(nom) as HTMLInputElement | null;
        surRecherche((champ?.value ?? "").trim());
      }}
    >
      {Object.entries(conserver).map(([cle, v]) =>
        v ? <input key={cle} type="hidden" name={cle} value={v} /> : null,
      )}
      <label className="relative min-w-0 flex-1">
        <span className="sr-only">{libelle}</span>
        <Search
          aria-hidden="true"
          className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-texte-doux"
        />
        <input
          type="search"
          name={nom}
          defaultValue={valeur}
          placeholder={placeholder}
          autoComplete="off"
          onChange={(e) => {
            if (!surRecherche) return;
            const texte = e.target.value.trim();
            clearTimeout(minuterie.current);
            minuterie.current = setTimeout(() => surRecherche(texte), delaiMs);
          }}
          className={cn(classesChamp, "pl-9", surRecherche && "pr-9")}
        />
        {enCours ? (
          <Loader2
            aria-hidden="true"
            className="absolute top-1/2 right-3 size-4 -translate-y-1/2 text-texte-doux motion-safe:animate-spin"
          />
        ) : null}
      </label>
      <button
        type="submit"
        className={cn(
          "min-h-11 rounded-lg border-2 border-royal bg-white px-4 font-semibold text-royal hover:bg-ice",
          surRecherche && "sr-only",
        )}
      >
        {boutonLibelle}
      </button>
    </form>
  );
}
