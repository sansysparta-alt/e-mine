"use client";

import { Check, ChevronDown } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { type KeyboardEvent, type ReactNode, useEffect, useId, useRef, useState } from "react";
import { cn } from "../cn";
import type { ComposantLien } from "./liste";

/** Élément du menu de l'utilisateur. */
export type ElementMenuUtilisateur =
  | { genre: "lien"; cle: string; libelle: string; href: string; icone?: LucideIcon }
  | { genre: "action"; cle: string; libelle: string; icone?: LucideIcon; surSelection: () => void }
  | {
      genre: "groupe";
      cle: string;
      libelle: string;
      /** Choix exclusifs (langue…) : un seul est coché. */
      choix: {
        cle: string;
        libelle: string;
        coche: boolean;
        lang?: string;
        surSelection: () => void;
      }[];
    }
  | { genre: "separateur"; cle: string };

export type MenuUtilisateurProps = {
  nomComplet: string;
  profil: string;
  elements: ElementMenuUtilisateur[];
  /** Nom accessible du bouton (« Menu de l'utilisateur »). */
  libelle: string;
  Lien?: ComposantLien;
  /** Contenu invisible rendu avec le menu (formulaire de déconnexion…). */
  enPlus?: ReactNode;
};

const LienParDefaut: ComposantLien = ({ children, ...props }) => <a {...props}>{children}</a>;

export function initiales(nom: string): string {
  return nom
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((m) => m[0]?.toUpperCase())
    .join("");
}

const classesElement =
  "flex min-h-11 w-full items-center gap-3 rounded-md px-3 text-left text-navy outline-none hover:bg-ice focus-visible:bg-ice focus-visible:ring-2 focus-visible:ring-azure";

/**
 * Menu sous l'avatar, comme dans les applications de gestion : en-tête (nom, profil), puis les
 * éléments (liens, actions, choix exclusifs, séparateurs). Modèle « menu button » de l'ARIA : le
 * bouton annonce le menu, les flèches parcourent les éléments, Début et Fin vont aux extrémités,
 * Échap ferme et rend le focus au bouton, Tab ou un clic à l'extérieur ferment.
 */
export function MenuUtilisateur({
  nomComplet,
  profil,
  elements,
  libelle,
  Lien = LienParDefaut,
  enPlus,
}: MenuUtilisateurProps) {
  const [ouvert, setOuvert] = useState(false);
  const id = useId();
  const bouton = useRef<HTMLButtonElement>(null);
  const conteneur = useRef<HTMLDivElement>(null);
  const menu = useRef<HTMLDivElement>(null);

  const items = () =>
    Array.from(menu.current?.querySelectorAll<HTMLElement>("[data-element-menu]") ?? []);

  useEffect(() => {
    if (!ouvert) return;
    items()[0]?.focus();
    function dehors(e: PointerEvent) {
      if (!conteneur.current?.contains(e.target as Node)) setOuvert(false);
    }
    document.addEventListener("pointerdown", dehors);
    return () => document.removeEventListener("pointerdown", dehors);
  }, [ouvert]);

  function fermer(rendreFocus: boolean) {
    setOuvert(false);
    if (rendreFocus) bouton.current?.focus();
  }

  function surToucheBouton(e: KeyboardEvent<HTMLButtonElement>) {
    if (e.key === "ArrowDown" || e.key === "ArrowUp") {
      e.preventDefault();
      setOuvert(true);
    }
  }

  function surToucheMenu(e: KeyboardEvent<HTMLDivElement>) {
    const liste = items();
    const i = liste.indexOf(document.activeElement as HTMLElement);
    const aller = (n: number) => liste[(n + liste.length) % liste.length]?.focus();
    switch (e.key) {
      case "ArrowDown":
        e.preventDefault();
        aller(i + 1);
        break;
      case "ArrowUp":
        e.preventDefault();
        aller(i - 1);
        break;
      case "Home":
        e.preventDefault();
        aller(0);
        break;
      case "End":
        e.preventDefault();
        aller(liste.length - 1);
        break;
      case "Escape":
        e.preventDefault();
        fermer(true);
        break;
      case "Tab":
        fermer(false);
        break;
    }
  }

  const choisir = (action: () => void) => () => {
    fermer(true);
    action();
  };

  return (
    <div ref={conteneur} className="relative">
      <button
        ref={bouton}
        type="button"
        id={`${id}-bouton`}
        aria-haspopup="menu"
        aria-expanded={ouvert}
        aria-controls={`${id}-menu`}
        onClick={() => setOuvert((o) => !o)}
        onKeyDown={surToucheBouton}
        className="flex min-h-11 items-center gap-3 rounded-lg px-1.5 text-left hover:bg-ice focus-visible:ring-2 focus-visible:ring-azure focus-visible:outline-none sm:px-2"
      >
        <span className="sr-only">{libelle}</span>
        <span
          aria-hidden="true"
          className="flex size-9 shrink-0 items-center justify-center rounded-full bg-ice text-sm font-bold text-royal"
        >
          {initiales(nomComplet)}
        </span>
        <span aria-hidden="true" className="hidden flex-col leading-tight sm:flex">
          <span className="font-semibold text-navy">{nomComplet}</span>
          <span className="text-sm text-texte-doux">{profil}</span>
        </span>
        <ChevronDown
          aria-hidden="true"
          className={cn("size-4 text-texte-doux transition-transform", ouvert && "rotate-180")}
        />
      </button>
      {ouvert ? (
        <div
          ref={menu}
          id={`${id}-menu`}
          role="menu"
          aria-labelledby={`${id}-bouton`}
          onKeyDown={surToucheMenu}
          className="absolute right-0 z-40 mt-2 w-72 max-w-[calc(100vw-2rem)] rounded-lg border border-bordure bg-white p-1.5 shadow-lg"
        >
          <div role="presentation" className="px-3 pt-2 pb-3">
            <p className="font-semibold text-navy">{nomComplet}</p>
            <p className="text-sm text-texte-doux">{profil}</p>
          </div>
          <div role="separator" className="my-1 h-px bg-bordure" />
          {elements.map((el) => {
            switch (el.genre) {
              case "separateur":
                return <div key={el.cle} role="separator" className="my-1 h-px bg-bordure" />;
              case "lien": {
                const Icone = el.icone;
                return (
                  <Lien
                    key={el.cle}
                    href={el.href}
                    role="menuitem"
                    tabIndex={-1}
                    data-element-menu=""
                    onClick={() => fermer(false)}
                    className={classesElement}
                  >
                    {Icone ? <Icone aria-hidden="true" className="size-5 text-texte-doux" /> : null}
                    {el.libelle}
                  </Lien>
                );
              }
              case "action": {
                const Icone = el.icone;
                return (
                  <button
                    key={el.cle}
                    type="button"
                    role="menuitem"
                    tabIndex={-1}
                    data-element-menu=""
                    onClick={choisir(el.surSelection)}
                    className={classesElement}
                  >
                    {Icone ? <Icone aria-hidden="true" className="size-5 text-texte-doux" /> : null}
                    {el.libelle}
                  </button>
                );
              }
              case "groupe":
                return (
                  <div key={el.cle} role="group" aria-labelledby={`${id}-${el.cle}`}>
                    <p
                      id={`${id}-${el.cle}`}
                      className="px-3 pt-2 pb-1 text-xs font-semibold tracking-wide text-texte-doux uppercase"
                    >
                      {el.libelle}
                    </p>
                    {el.choix.map((c) => (
                      <button
                        key={c.cle}
                        type="button"
                        role="menuitemradio"
                        aria-checked={c.coche}
                        tabIndex={-1}
                        lang={c.lang}
                        data-element-menu=""
                        onClick={choisir(c.surSelection)}
                        className={classesElement}
                      >
                        <span
                          aria-hidden="true"
                          className="flex size-5 items-center justify-center"
                        >
                          {c.coche ? <Check className="size-4 text-royal" /> : null}
                        </span>
                        {c.libelle}
                      </button>
                    ))}
                  </div>
                );
            }
          })}
        </div>
      ) : null}
      {enPlus}
    </div>
  );
}
