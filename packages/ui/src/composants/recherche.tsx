"use client";

import { Loader2, Search, X } from "lucide-react";
import { useEffect, useId, useRef, useState } from "react";
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
  /**
   * Suggestions proposées pendant la saisie (recherches récentes, noms de documents…), filtrées
   * par le navigateur (`datalist`, accessible au clavier et aux lecteurs d'écran).
   */
  suggestions?: string[];
  /** Libellé de la croix qui vide le champ (affichée dès qu'un texte est saisi). */
  libelleVider?: string;
};

/** Nombre maximal de suggestions proposées. */
const MAX_SUGGESTIONS = 12;

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
  suggestions = [],
  libelleVider = "Effacer la recherche",
}: ChampRechercheProps) {
  const minuterie = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const champ = useRef<HTMLInputElement>(null);
  const [texte, setTexte] = useState(valeur ?? "");
  const idSuggestions = useId();
  // Recherche changée hors du champ (lien « Effacer la recherche », retour arrière) : le champ suit,
  // sans toucher à ce que l'utilisateur est en train de taper.
  const [valeurConnue, setValeurConnue] = useState(valeur);
  if (valeur !== valeurConnue) {
    setValeurConnue(valeur);
    if ((valeur ?? "") !== texte.trim()) setTexte(valeur ?? "");
  }
  useEffect(() => () => clearTimeout(minuterie.current), []);
  const proposees = [...new Set(suggestions.map((x) => x.trim()).filter(Boolean))].slice(
    0,
    MAX_SUGGESTIONS,
  );

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
          ref={champ}
          value={texte}
          placeholder={placeholder}
          autoComplete="off"
          list={proposees.length > 0 ? idSuggestions : undefined}
          onChange={(e) => {
            setTexte(e.target.value);
            if (!surRecherche) return;
            const saisi = e.target.value.trim();
            clearTimeout(minuterie.current);
            minuterie.current = setTimeout(() => surRecherche(saisi), delaiMs);
          }}
          className={cn(
            classesChamp,
            "pl-9 [&::-webkit-search-cancel-button]:appearance-none",
            (surRecherche || texte) && "pr-20",
          )}
        />
        {proposees.length > 0 ? (
          <datalist id={idSuggestions}>
            {proposees.map((x) => (
              <option key={x} value={x} />
            ))}
          </datalist>
        ) : null}
        {enCours ? (
          <Loader2
            aria-hidden="true"
            className="absolute top-1/2 right-12 size-4 -translate-y-1/2 text-texte-doux motion-safe:animate-spin"
          />
        ) : null}
      </label>
      {texte ? (
        <button
          type="button"
          aria-label={libelleVider}
          onClick={() => {
            setTexte("");
            clearTimeout(minuterie.current);
            surRecherche?.("");
            champ.current?.focus();
          }}
          className="-ml-13 flex size-11 shrink-0 items-center justify-center self-center rounded-lg text-texte-doux hover:bg-ice"
        >
          <X aria-hidden="true" className="size-4" />
        </button>
      ) : null}
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
