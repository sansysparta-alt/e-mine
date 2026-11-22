"use client";

import { cn, type OptionFiltre } from "@emine/ui";
import { ChevronDown, X } from "@emine/ui/icones";
import Link from "next/link";
import { useEffect, useId, useRef, useState } from "react";

export type PlusDeFiltresProps = {
  libelle: string;
  /** Titre du panneau (lecteurs d'écran et visuel). */
  titre: string;
  options: OptionFiltre[];
  /** Filtre détaillé actif (libellé affiché sur la pastille) et adresse pour l'effacer. */
  actif?: { libelle: string; effacer: string; libelleEffacer: string };
};

/**
 * « Plus de filtres » dans la rangée des pastilles (correctifs ergonomie) : une pastille qui ouvre
 * un panneau des filtres détaillés (par statut), fermé par Échap ou un clic à l'extérieur ; un
 * filtre détaillé actif s'affiche sur la pastille elle-même, avec de quoi l'effacer.
 */
export function PlusDeFiltres({ libelle, titre, options, actif }: PlusDeFiltresProps) {
  const [ouvert, setOuvert] = useState(false);
  const id = useId();
  const zone = useRef<HTMLDivElement>(null);
  const bouton = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!ouvert) return;
    const clic = (e: MouseEvent) => {
      if (zone.current && !zone.current.contains(e.target as Node)) setOuvert(false);
    };
    const touche = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        setOuvert(false);
        bouton.current?.focus();
      }
    };
    document.addEventListener("mousedown", clic);
    document.addEventListener("keydown", touche);
    return () => {
      document.removeEventListener("mousedown", clic);
      document.removeEventListener("keydown", touche);
    };
  }, [ouvert]);

  if (actif) {
    return (
      <span className="inline-flex min-h-11 items-center gap-1 rounded-full border border-navy bg-navy pr-1 pl-4 text-sm font-semibold text-white">
        {actif.libelle}
        <Link
          href={actif.effacer}
          className="inline-flex size-9 items-center justify-center rounded-full hover:bg-white/20"
        >
          <X aria-hidden="true" className="size-4" />
          <span className="sr-only">{actif.libelleEffacer}</span>
        </Link>
      </span>
    );
  }

  return (
    <div ref={zone}>
      <button
        ref={bouton}
        type="button"
        aria-expanded={ouvert}
        aria-controls={`${id}-panneau`}
        onClick={() => setOuvert((o) => !o)}
        className="inline-flex min-h-11 items-center gap-1 rounded-full border border-dashed border-bordure bg-white px-4 text-sm font-semibold text-royal hover:border-royal"
      >
        {libelle}
        <ChevronDown
          aria-hidden="true"
          className={cn("size-4 transition-transform", ouvert && "rotate-180")}
        />
      </button>
      {ouvert ? (
        <div
          id={`${id}-panneau`}
          role="region"
          aria-label={titre}
          className="absolute left-0 z-20 mt-2 w-[min(22rem,calc(100vw-2rem))] rounded-carte border border-bordure bg-white p-3 shadow-lg"
        >
          <p className="mb-2 text-sm font-semibold text-texte-doux">{titre}</p>
          <ul className="flex flex-col">
            {options.map((o) => (
              <li key={o.valeur}>
                <Link
                  href={o.href}
                  onClick={() => setOuvert(false)}
                  className="flex min-h-11 items-center justify-between gap-3 rounded-lg px-3 hover:bg-fond"
                >
                  <span>{o.libelle}</span>
                  {o.nombre !== undefined ? (
                    <span className="rounded-full bg-fond px-2 text-xs text-texte-doux">
                      {o.nombre}
                    </span>
                  ) : null}
                </Link>
              </li>
            ))}
          </ul>
        </div>
      ) : null}
    </div>
  );
}
