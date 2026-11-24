"use client";

import { cn, correspond, TexteSurligne } from "@emine/ui";
import { FileText, History, type LucideIcon, Search, X } from "@emine/ui/icones";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useCallback, useEffect, useId, useMemo, useRef, useState } from "react";

/** Page proposée par la recherche : seulement celles que le profil peut ouvrir. */
export type PageRecherche = { cle: string; libelle: string; href: string; icone: LucideIcon };

/** Dossier trouvé par l'API (droits du profil appliqués côté serveur). */
export type ResultatDemande = {
  id: number;
  numero: string | null;
  document: string;
  demandeur: string | null;
};

const CLE_RECENTES = "emine.recherches-recentes";
const MAX_RECENTES = 5;
const DELAI_MS = 250;

/** Recherches récentes de ce navigateur (commodité locale : absente ou illisible, liste vide). */
export function lireRecentes(): string[] {
  try {
    const brut = JSON.parse(window.localStorage.getItem(CLE_RECENTES) ?? "[]") as unknown;
    return Array.isArray(brut) ? brut.filter((x): x is string => typeof x === "string") : [];
  } catch {
    return [];
  }
}

/** Ajoute une recherche en tête des récentes (sans doublon, 5 au plus). */
export function memoriserRecente(texte: string, recentes = lireRecentes()): string[] {
  const t = texte.trim();
  if (t.length < 2) return recentes;
  const liste = [t, ...recentes.filter((r) => r.toLowerCase() !== t.toLowerCase())].slice(
    0,
    MAX_RECENTES,
  );
  try {
    window.localStorage.setItem(CLE_RECENTES, JSON.stringify(liste));
  } catch {
    // Stockage indisponible (navigation privée) : rien n'est gardé.
  }
  return liste;
}

type Option =
  | { genre: "recente"; id: string; texte: string }
  | { genre: "page"; id: string; page: PageRecherche }
  | { genre: "demande"; id: string; demande: ResultatDemande }
  | { genre: "toutes"; id: string; href: string };

type EtatDemandes =
  | { etat: "inactif" }
  | { etat: "chargement" }
  | { etat: "erreur" }
  | { etat: "pret"; elements: ResultatDemande[]; total: number };

/**
 * Recherche globale de la barre du haut (raccourci Ctrl+K) : pages que le profil peut ouvrir et
 * dossiers qu'il a le droit de voir (filtrés par l'API). Recherche tolérante (accents, majuscules,
 * partie du numéro), parties trouvées surlignées, recherches récentes, croix pour vider, et un
 * écran « aucun résultat » qui dit quoi essayer. Combobox accessible : flèches, Entrée, Échap.
 */
export function RechercheGlobale({
  pages,
  lienDemandes,
}: {
  pages: PageRecherche[];
  /** Liste complète des dossiers, pour « Voir tous les résultats » (absente : pas de lien). */
  lienDemandes?: string;
}) {
  const t = useTranslations("rechercheGlobale");
  const router = useRouter();
  const [ouvert, setOuvert] = useState(false);
  const [texte, setTexte] = useState("");
  const [actif, setActif] = useState(0);
  const [recentes, setRecentes] = useState<string[]>([]);
  const [demandes, setDemandes] = useState<EtatDemandes>({ etat: "inactif" });
  const dialogue = useRef<HTMLDialogElement>(null);
  const champ = useRef<HTMLInputElement>(null);
  const declencheur = useRef<HTMLButtonElement>(null);
  const idListe = useId();
  const idTitre = useId();
  const requete = texte.trim();

  const ouvrir = useCallback(() => {
    setRecentes(lireRecentes());
    setOuvert(true);
  }, []);

  const fermer = useCallback(() => {
    setOuvert(false);
    setTexte("");
    setDemandes({ etat: "inactif" });
    setActif(0);
    declencheur.current?.focus();
  }, []);

  // Raccourci Ctrl+K (Cmd+K sur Mac), depuis n'importe quel écran.
  useEffect(() => {
    const surTouche = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        ouvrir();
      }
    };
    document.addEventListener("keydown", surTouche);
    return () => document.removeEventListener("keydown", surTouche);
  }, [ouvrir]);

  // Fenêtre modale native : focus gardé, fond inerte.
  useEffect(() => {
    const d = dialogue.current;
    if (!d) return;
    if (ouvert && !d.open) {
      if (typeof d.showModal === "function") d.showModal();
      else d.setAttribute("open", "");
      champ.current?.focus();
    } else if (!ouvert && d.open) {
      if (typeof d.close === "function") d.close();
      else d.removeAttribute("open");
    }
  }, [ouvert]);

  // Dossiers : lus après un court délai sans frappe ; la dernière frappe l'emporte.
  useEffect(() => {
    if (requete.length < 2) return;
    let valide = true;
    const minuterie = window.setTimeout(async () => {
      try {
        const r = await fetch(`/api/recherche?q=${encodeURIComponent(requete)}`, {
          cache: "no-store",
        });
        if (!r.ok) throw new Error(String(r.status));
        const corps = (await r.json()) as { demandes: ResultatDemande[]; total?: number };
        if (valide) {
          setDemandes({
            etat: "pret",
            elements: corps.demandes,
            total: corps.total ?? corps.demandes.length,
          });
        }
      } catch {
        if (valide) setDemandes({ etat: "erreur" });
      }
    }, DELAI_MS);
    return () => {
      valide = false;
      window.clearTimeout(minuterie);
    };
  }, [requete]);

  const options: Option[] = useMemo(() => {
    if (requete.length === 0) {
      return [
        ...recentes.map((r, i) => ({ genre: "recente" as const, id: `recente-${i}`, texte: r })),
        ...pages.map((p) => ({ genre: "page" as const, id: `page-${p.cle}`, page: p })),
      ];
    }
    const trouvees = pages
      .filter((p) => correspond(p.libelle, requete))
      .map((p) => ({ genre: "page" as const, id: `page-${p.cle}`, page: p }));
    const dossiers =
      demandes.etat === "pret" && requete.length >= 2
        ? demandes.elements.map((d) => ({
            genre: "demande" as const,
            id: `demande-${d.id}`,
            demande: d,
          }))
        : [];
    const toutes =
      lienDemandes && demandes.etat === "pret" && demandes.total > demandes.elements.length
        ? [
            {
              genre: "toutes" as const,
              id: "toutes",
              href: `${lienDemandes}?q=${encodeURIComponent(requete)}`,
            },
          ]
        : [];
    return [...dossiers, ...toutes, ...trouvees];
  }, [requete, recentes, pages, demandes, lienDemandes]);

  const selection = options[Math.min(actif, options.length - 1)];

  const choisir = (o: Option | undefined) => {
    if (!o) return;
    if (o.genre === "recente") {
      setTexte(o.texte);
      setActif(0);
      champ.current?.focus();
      return;
    }
    if (o.genre !== "page") setRecentes(memoriserRecente(requete));
    const href =
      o.genre === "page"
        ? o.page.href
        : o.genre === "demande"
          ? `/demandes/${o.demande.id}`
          : o.href;
    fermer();
    router.push(href);
  };

  const surTouche = (e: React.KeyboardEvent) => {
    if (e.key === "ArrowDown" || e.key === "ArrowUp") {
      e.preventDefault();
      if (options.length === 0) return;
      const pas = e.key === "ArrowDown" ? 1 : -1;
      setActif((a) => (Math.min(a, options.length - 1) + pas + options.length) % options.length);
    } else if (e.key === "Enter") {
      e.preventDefault();
      choisir(selection);
    }
  };

  // Défilement : l'option active reste visible.
  useEffect(() => {
    if (!selection) return;
    document.getElementById(`${idListe}-${selection.id}`)?.scrollIntoView?.({ block: "nearest" });
  }, [selection, idListe]);

  const chargement = requete.length >= 2 && demandes.etat !== "pret" && demandes.etat !== "erreur";
  const aucunResultat = requete.length > 0 && !chargement && options.length === 0;

  const groupe = (genre: Option["genre"][], titre: string) => {
    const liste = options.filter((o) => genre.includes(o.genre));
    if (liste.length === 0) return null;
    const idGroupe = `${idListe}-g-${genre[0]}`;
    return (
      <li role="presentation" className="py-1">
        <p
          id={idGroupe}
          role="presentation"
          className="px-4 pt-2 pb-1 text-xs font-semibold tracking-wide text-texte-doux uppercase"
        >
          {titre}
        </p>
        <ul role="group" aria-labelledby={idGroupe}>
          {liste.map((o) => {
            const estActif = selection?.id === o.id;
            return (
              <li
                key={o.id}
                id={`${idListe}-${o.id}`}
                role="option"
                aria-selected={estActif}
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => choisir(o)}
                onMouseMove={() => setActif(options.indexOf(o))}
                className={cn(
                  "mx-2 flex min-h-11 cursor-pointer items-center gap-3 rounded-lg px-3 py-2",
                  estActif ? "bg-ice text-navy" : "text-texte",
                )}
              >
                {o.genre === "recente" ? (
                  <>
                    <History aria-hidden="true" className="size-4 shrink-0 text-texte-doux" />
                    <span className="truncate">{o.texte}</span>
                  </>
                ) : o.genre === "page" ? (
                  <>
                    <o.page.icone aria-hidden="true" className="size-4 shrink-0 text-texte-doux" />
                    <span className="truncate">
                      <TexteSurligne texte={o.page.libelle} recherche={requete} />
                    </span>
                  </>
                ) : o.genre === "demande" ? (
                  <>
                    <FileText aria-hidden="true" className="size-4 shrink-0 text-texte-doux" />
                    <span className="flex min-w-0 flex-col">
                      <span className="font-semibold text-navy tabular-nums">
                        <TexteSurligne
                          texte={o.demande.numero ?? t("brouillon")}
                          recherche={requete}
                        />
                      </span>
                      <span className="truncate text-sm text-texte-doux">
                        <TexteSurligne texte={o.demande.document} recherche={requete} />
                        {o.demande.demandeur ? ` · ${o.demande.demandeur}` : null}
                      </span>
                    </span>
                  </>
                ) : (
                  <>
                    <Search aria-hidden="true" className="size-4 shrink-0 text-texte-doux" />
                    <span className="font-semibold text-royal">
                      {t("voirTous", {
                        nombre: demandes.etat === "pret" ? demandes.total : 0,
                      })}
                    </span>
                  </>
                )}
              </li>
            );
          })}
        </ul>
      </li>
    );
  };

  return (
    <>
      <button
        ref={declencheur}
        type="button"
        onClick={ouvrir}
        aria-haspopup="dialog"
        aria-keyshortcuts="Control+K"
        aria-label={t("ouvrir")}
        className="flex min-h-11 min-w-11 items-center justify-center gap-2 rounded-lg border-bordure text-texte-doux hover:bg-ice sm:w-72 sm:justify-start sm:border sm:bg-fond sm:px-3"
      >
        <Search aria-hidden="true" className="size-5 shrink-0 text-navy sm:size-4" />
        <span className="hidden flex-1 truncate text-left text-sm sm:block">
          {t("placeholder")}
        </span>
        <kbd className="hidden rounded border border-bordure bg-white px-1.5 font-sans text-xs sm:block">
          Ctrl K
        </kbd>
      </button>
      <dialog
        ref={dialogue}
        aria-labelledby={idTitre}
        onCancel={(e) => {
          e.preventDefault();
          fermer();
        }}
        onClick={(e) => {
          if (e.target === dialogue.current) fermer();
        }}
        className="mx-auto mt-[10vh] w-[min(40rem,calc(100vw-2rem))] max-w-none overflow-hidden rounded-carte bg-white p-0 shadow-xl backdrop:bg-navy/40"
      >
        <h2 id={idTitre} className="sr-only">
          {t("titre")}
        </h2>
        <div className="flex items-center gap-2 border-b border-bordure px-4">
          <Search aria-hidden="true" className="size-5 shrink-0 text-navy" />
          <input
            ref={champ}
            type="text"
            role="combobox"
            aria-expanded={options.length > 0}
            aria-controls={idListe}
            aria-autocomplete="list"
            aria-activedescendant={selection ? `${idListe}-${selection.id}` : undefined}
            aria-label={t("titre")}
            placeholder={t("placeholder")}
            value={texte}
            onChange={(e) => {
              setTexte(e.target.value);
              setActif(0);
              if (e.target.value.trim().length >= 2) setDemandes({ etat: "chargement" });
            }}
            onKeyDown={surTouche}
            autoComplete="off"
            spellCheck={false}
            className="min-h-14 flex-1 bg-transparent text-base text-texte outline-none placeholder:text-texte-doux"
          />
          {texte ? (
            <button
              type="button"
              onClick={() => {
                setTexte("");
                setDemandes({ etat: "inactif" });
                champ.current?.focus();
              }}
              aria-label={t("vider")}
              className="flex size-11 items-center justify-center rounded-lg text-texte-doux hover:bg-ice"
            >
              <X aria-hidden="true" className="size-5" />
            </button>
          ) : null}
          <button
            type="button"
            onClick={fermer}
            className="hidden min-h-11 rounded-lg px-2 text-sm text-texte-doux hover:bg-ice sm:block"
          >
            <kbd className="font-sans">{t("echap")}</kbd>
          </button>
        </div>
        <div className="max-h-[60vh] overflow-y-auto pb-2">
          <ul id={idListe} role="listbox" aria-label={t("resultats")}>
            {groupe(["recente"], t("recentes"))}
            {groupe(["demande", "toutes"], t("dossiers"))}
            {groupe(["page"], t("pages"))}
          </ul>
          <div aria-live="polite" className="px-4">
            {chargement ? (
              <p className="py-3 text-sm text-texte-doux">{t("chargement")}</p>
            ) : demandes.etat === "erreur" ? (
              <p className="py-3 text-sm text-texte-doux">{t("erreur")}</p>
            ) : aucunResultat ? (
              <div className="py-6 text-center">
                <p className="font-semibold text-navy">{t("aucun.titre", { texte: requete })}</p>
                <p className="mt-1 text-sm text-texte-doux">{t("aucun.conseil")}</p>
              </div>
            ) : null}
          </div>
        </div>
        <p className="hidden border-t border-bordure px-4 py-2 text-xs text-texte-doux sm:block">
          {t("aideClavier")}
        </p>
      </dialog>
    </>
  );
}
