"use client";

import { ChevronsLeft, ChevronsRight, type LucideIcon, Menu, X } from "lucide-react";
import { type ReactNode, useEffect, useRef, useState, useSyncExternalStore } from "react";
import { cn } from "../cn";
import { initiales } from "./menu-utilisateur";
import type { ComposantLien } from "./liste";

export type EntreeNavigation = {
  cle: string;
  href: string;
  libelle: string;
  icone: LucideIcon;
  actif: boolean;
  /** Nombre affiché à côté de la rubrique (notifications non lues, dossiers à traiter…). */
  compteur?: number;
  /** Texte du compteur pour les lecteurs d'écran (« 3 à traiter »). */
  libelleCompteur?: string;
};

/** Lien de la navigation hors rubriques (action principale, aide). */
export type LienGabarit = { libelle: string; href: string; icone: LucideIcon };

export type SectionNavigation = { cle: string; titre?: string; entrees: EntreeNavigation[] };

export type UtilisateurGabarit = { nomComplet: string; profil: string };

export type GabaritApplicationProps = {
  /** Nom de l'application, en tête de la navigation. */
  marque: string;
  sousTitre?: string;
  sections: SectionNavigation[];
  /** Titre de la page courante, dans la barre du haut. */
  titre: string;
  utilisateur: UtilisateurGabarit;
  /**
   * Menu sous l'avatar, à droite de la barre du haut (`MenuUtilisateur` : profil, langue,
   * déconnexion). Sans menu, l'avatar et le nom de l'utilisateur s'affichent seuls.
   */
  menuUtilisateur?: ReactNode;
  /** Actions de la barre du haut placées avant l'utilisateur (centre de notifications…). */
  actionsEntete?: ReactNode;
  /** Action principale du profil, en tête du menu (« Nouvelle demande » pour un demandeur). */
  actionPrincipale?: LienGabarit;
  /** Lien d'aide, en bas du menu. */
  aide?: LienGabarit;
  /** Recherche globale, dans la barre du haut. */
  recherche?: ReactNode;
  /** Pied de page discret (mentions légales, version, aide). */
  piedDePage?: ReactNode;
  libelles: {
    navigation: string;
    ouvrirMenu: string;
    fermerMenu: string;
    reduireMenu: string;
    deplierMenu: string;
  };
  Lien?: ComposantLien;
  children: ReactNode;
};

const CLE_REPLIE = "emine:interface:menu-replie";

const LienParDefaut: ComposantLien = ({ children, ...props }) => <a {...props}>{children}</a>;

// Préférence « menu réduit » gardée dans le navigateur (simple confort, sans donnée personnelle).
const EVENEMENT_REPLIE = "emine:menu-replie";

function lireReplie(): boolean {
  try {
    return window.localStorage.getItem(CLE_REPLIE) === "1";
  } catch {
    return false;
  }
}

function ecrireReplie(valeur: boolean) {
  try {
    window.localStorage.setItem(CLE_REPLIE, valeur ? "1" : "0");
  } catch {
    // stockage indisponible : la préférence ne sera pas mémorisée
  }
  window.dispatchEvent(new Event(EVENEMENT_REPLIE));
}

function abonnerReplie(rappel: () => void) {
  window.addEventListener(EVENEMENT_REPLIE, rappel);
  window.addEventListener("storage", rappel);
  return () => {
    window.removeEventListener(EVENEMENT_REPLIE, rappel);
    window.removeEventListener("storage", rappel);
  };
}

/**
 * Gabarit de l'application (socle) : navigation latérale à gauche (repliable sur grand écran,
 * tiroir sur mobile), barre du haut avec le titre de la page et l'utilisateur, zone de contenu.
 * La navigation est fournie par la configuration unique de l'application, déjà filtrée selon le
 * profil.
 */
export function GabaritApplication({
  marque,
  sousTitre,
  sections,
  titre,
  utilisateur,
  menuUtilisateur,
  actionsEntete,
  actionPrincipale,
  aide,
  recherche,
  piedDePage,
  libelles,
  Lien = LienParDefaut,
  children,
}: GabaritApplicationProps) {
  const replie = useSyncExternalStore(abonnerReplie, lireReplie, () => false);
  const [tiroir, setTiroir] = useState(false);
  const boutonMenu = useRef<HTMLButtonElement>(null);
  const panneau = useRef<HTMLDivElement>(null);

  function basculer() {
    ecrireReplie(!replie);
  }

  // Tiroir mobile : Échap ferme, le focus va dans le tiroir puis revient au bouton.
  // Le focus reste dans le tiroir (Tab boucle), et la page derrière ne défile plus.
  useEffect(() => {
    if (!tiroir) return;
    const premier = panneau.current?.querySelector<HTMLElement>("a, button");
    premier?.focus();
    const defilement = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    function surTouche(e: KeyboardEvent) {
      if (e.key === "Escape") {
        setTiroir(false);
        boutonMenu.current?.focus();
        return;
      }
      if (e.key !== "Tab") return;
      const focusables = Array.from(
        panneau.current?.querySelectorAll<HTMLElement>("a[href], button:not([disabled])") ?? [],
      );
      const debut = focusables[0];
      const fin = focusables[focusables.length - 1];
      if (!debut || !fin) return;
      if (e.shiftKey && document.activeElement === debut) {
        e.preventDefault();
        fin.focus();
      } else if (!e.shiftKey && document.activeElement === fin) {
        e.preventDefault();
        debut.focus();
      }
    }
    document.addEventListener("keydown", surTouche);
    return () => {
      document.removeEventListener("keydown", surTouche);
      document.body.style.overflow = defilement;
    };
  }, [tiroir]);

  const navigation = (mobile: boolean) => (
    <nav aria-label={libelles.navigation} className="flex-1 overflow-y-auto px-3 py-4">
      {sections.map((s) => (
        <div key={s.cle} className="mb-5">
          {s.titre && (!replie || mobile) ? (
            <p className="mb-2 px-3 text-xs font-semibold tracking-wider text-sky uppercase">
              {s.titre}
            </p>
          ) : null}
          <ul className="flex flex-col gap-1">
            {s.entrees.map((e) => {
              const Icone = e.icone;
              const compact = replie && !mobile;
              return (
                <li key={e.cle}>
                  <Lien
                    href={e.href}
                    aria-current={e.actif ? "page" : undefined}
                    title={compact ? e.libelle : undefined}
                    onClick={mobile ? () => setTiroir(false) : undefined}
                    className={cn(
                      "flex min-h-11 items-center gap-3 rounded-lg px-3 text-base text-ice hover:bg-white/10 hover:text-white",
                      e.actif &&
                        "bg-white/15 font-semibold text-white shadow-[inset_3px_0_0_var(--color-or)]",
                      compact && "justify-center px-0",
                    )}
                  >
                    <span className="relative shrink-0">
                      <Icone aria-hidden="true" className="size-5" />
                      {compact && e.compteur ? (
                        <span
                          aria-hidden="true"
                          className="absolute -top-1.5 -right-2 size-2.5 rounded-full bg-or ring-2 ring-navy"
                        />
                      ) : null}
                    </span>
                    <span className={cn("flex-1", compact && "sr-only")}>{e.libelle}</span>
                    {e.compteur ? (
                      <>
                        <span
                          aria-hidden="true"
                          className={cn(
                            "min-w-6 rounded-full bg-or px-1.5 text-center text-xs leading-5 font-bold text-navy",
                            compact && "sr-only",
                          )}
                        >
                          {e.compteur > 99 ? "99+" : e.compteur}
                        </span>
                        <span className="sr-only">{`, ${e.libelleCompteur ?? e.compteur}`}</span>
                      </>
                    ) : null}
                  </Lien>
                </li>
              );
            })}
          </ul>
        </div>
      ))}
    </nav>
  );

  const enTeteNavigation = (
    <div
      className={cn(
        "flex min-h-16 items-center gap-3 border-b border-white/10 px-4",
        replie && "lg:justify-center lg:px-2",
      )}
    >
      <span
        aria-hidden="true"
        className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-or font-titre text-lg font-bold text-navy"
      >
        e
      </span>
      <span className={cn("flex flex-col leading-tight", replie && "lg:sr-only")}>
        <span className="font-titre text-lg font-bold text-white">{marque}</span>
        {sousTitre ? <span className="text-xs text-ice">{sousTitre}</span> : null}
      </span>
    </div>
  );

  const boutonAction = (mobile: boolean) =>
    actionPrincipale ? (
      <div className="px-3 pt-4">
        <Lien
          href={actionPrincipale.href}
          title={replie && !mobile ? actionPrincipale.libelle : undefined}
          onClick={mobile ? () => setTiroir(false) : undefined}
          className={cn(
            "flex min-h-11 items-center justify-center gap-2 rounded-lg bg-or px-3 font-semibold text-navy hover:bg-white",
            replie && !mobile && "px-0",
          )}
        >
          <actionPrincipale.icone aria-hidden="true" className="size-5 shrink-0" />
          <span className={cn(replie && !mobile && "sr-only")}>{actionPrincipale.libelle}</span>
        </Lien>
      </div>
    ) : null;

  const lienAide = (mobile: boolean) =>
    aide ? (
      <Lien
        href={aide.href}
        title={replie && !mobile ? aide.libelle : undefined}
        onClick={mobile ? () => setTiroir(false) : undefined}
        className={cn(
          "flex min-h-11 items-center gap-3 rounded-lg px-3 text-ice hover:bg-white/10 hover:text-white",
          replie && !mobile && "justify-center px-0",
        )}
      >
        <aide.icone aria-hidden="true" className="size-5 shrink-0" />
        <span className={cn(replie && !mobile && "sr-only")}>{aide.libelle}</span>
      </Lien>
    ) : null;

  return (
    <div className="min-h-dvh bg-fond lg:flex">
      {/* Navigation latérale (grand écran) */}
      <aside
        className={cn(
          "sticky top-0 hidden h-dvh shrink-0 flex-col bg-navy text-white transition-[width] lg:flex",
          replie ? "w-20" : "w-64",
        )}
      >
        {enTeteNavigation}
        {boutonAction(false)}
        {navigation(false)}
        <div className="flex flex-col gap-1 border-t border-white/10 p-3">
          {lienAide(false)}
          <button
            type="button"
            onClick={basculer}
            aria-expanded={!replie}
            className={cn(
              "flex min-h-11 w-full items-center gap-3 rounded-lg px-3 text-ice hover:bg-white/10 hover:text-white",
              replie && "justify-center px-0",
            )}
          >
            {replie ? (
              <ChevronsRight aria-hidden="true" className="size-5" />
            ) : (
              <ChevronsLeft aria-hidden="true" className="size-5" />
            )}
            <span className={cn(replie && "sr-only")}>
              {replie ? libelles.deplierMenu : libelles.reduireMenu}
            </span>
          </button>
        </div>
      </aside>

      {/* Tiroir de navigation (mobile et tablette) */}
      {tiroir ? (
        <div className="fixed inset-0 z-40 lg:hidden">
          <button
            type="button"
            aria-label={libelles.fermerMenu}
            className="absolute inset-0 bg-navy/50"
            onClick={() => setTiroir(false)}
            tabIndex={-1}
          />
          <div
            ref={panneau}
            id="navigation-mobile"
            role="dialog"
            aria-modal="true"
            aria-label={libelles.navigation}
            className="relative flex h-full w-[min(18rem,85vw)] flex-col bg-navy text-white shadow-xl"
          >
            <div className="flex items-center justify-between pr-2">
              {enTeteNavigation}
              <button
                type="button"
                onClick={() => {
                  setTiroir(false);
                  boutonMenu.current?.focus();
                }}
                className="flex size-11 items-center justify-center rounded-lg text-ice hover:bg-white/10"
              >
                <X aria-hidden="true" className="size-5" />
                <span className="sr-only">{libelles.fermerMenu}</span>
              </button>
            </div>
            {boutonAction(true)}
            {navigation(true)}
            {aide ? <div className="border-t border-white/10 p-3">{lienAide(true)}</div> : null}
          </div>
        </div>
      ) : null}

      <div className="flex min-w-0 flex-1 flex-col">
        {/* Barre du haut */}
        <header className="sticky top-0 z-30 flex min-h-16 items-center gap-3 border-b border-bordure bg-white px-4 sm:px-6">
          <button
            ref={boutonMenu}
            type="button"
            onClick={() => setTiroir(true)}
            aria-expanded={tiroir}
            aria-controls="navigation-mobile"
            className="flex size-11 shrink-0 items-center justify-center rounded-lg text-navy hover:bg-ice lg:hidden"
          >
            <Menu aria-hidden="true" className="size-6" />
            <span className="sr-only">{libelles.ouvrirMenu}</span>
          </button>
          <p className="min-w-0 flex-1 truncate font-titre text-lg font-bold text-navy">{titre}</p>
          {recherche}
          <div className="flex items-center gap-3">
            {actionsEntete}
            {menuUtilisateur ?? (
              <div className="hidden items-center gap-3 sm:flex">
                <span
                  aria-hidden="true"
                  className="flex size-9 items-center justify-center rounded-full bg-ice text-sm font-bold text-royal"
                >
                  {initiales(utilisateur.nomComplet)}
                </span>
                <span className="flex flex-col text-right leading-tight">
                  <span className="font-semibold text-navy">{utilisateur.nomComplet}</span>
                  <span className="text-sm text-texte-doux">{utilisateur.profil}</span>
                </span>
              </div>
            )}
          </div>
        </header>
        <main
          id="contenu"
          tabIndex={-1}
          className="mx-auto w-full max-w-6xl flex-1 px-4 py-6 outline-none sm:px-6 sm:py-8"
        >
          {children}
        </main>
        {piedDePage ? (
          <footer className="border-t border-bordure bg-white px-4 py-4 text-sm text-texte-doux sm:px-6">
            <div className="mx-auto w-full max-w-6xl">{piedDePage}</div>
          </footer>
        ) : null}
      </div>
    </div>
  );
}
