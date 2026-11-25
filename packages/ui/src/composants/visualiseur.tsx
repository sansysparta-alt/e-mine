"use client";

import {
  Download,
  FileQuestion,
  Maximize2,
  Minimize2,
  Printer,
  X,
  ZoomIn,
  ZoomOut,
} from "lucide-react";
import { useEffect, useId, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { cn } from "../cn";
import { Alerte } from "./alerte";

export type DocumentAffiche = {
  nomFichier: string;
  typeMime: string;
  /** Adresse d'affichage en ligne (PDF ou image). */
  urlAffichage: string;
  /** Adresse de téléchargement (pièce jointe). */
  urlTelechargement: string;
  /** Avertissement affiché au-dessus du document (ex. document révoqué). */
  avertissement?: string;
};

export type LibellesVisualiseur = {
  telecharger: string;
  imprimer: string;
  zoomAvant: string;
  zoomArriere: string;
  /** Zoom « à la largeur du panneau » (valeur par défaut). */
  ajuster: string;
  pleinEcran: string;
  quitterPleinEcran: string;
  fermer: string;
  formatNonAffichable: string;
};

export type VisualiseurDocumentProps = {
  /** Document à afficher ; `null` = panneau fermé. */
  document: DocumentAffiche | null;
  surFermeture: () => void;
  libelles: LibellesVisualiseur;
};

/** Niveaux de zoom proposés (en %), en plus de « à la largeur ». */
export const ZOOMS = [50, 75, 100, 125, 150, 200] as const;
type Zoom = "largeur" | (typeof ZOOMS)[number];

/**
 * Adresse d'affichage d'un PDF dans le lecteur du navigateur, **sans sa barre d'outils ni ses
 * miniatures** (la nôtre suffit : un seul « Télécharger ») : pages empilées, à la largeur du panneau
 * ou au zoom choisi. Paramètres d'ouverture PDF (Adobe), compris par Chrome, Edge et Firefox.
 */
export function adressePdf(url: string, zoom: Zoom): string {
  const base = url.split("#")[0];
  const vue = zoom === "largeur" ? "view=FitH" : `zoom=${zoom}`;
  return `${base}#toolbar=0&navpanes=0&pagemode=none&scrollbar=1&${vue}`;
}

/** Zoom suivant (+1) ou précédent (-1) ; « à la largeur » se comporte comme 100 %. */
export function zoomVoisin(zoom: Zoom, sens: 1 | -1): Zoom {
  const actuel = zoom === "largeur" ? 100 : zoom;
  const i = ZOOMS.indexOf(actuel);
  return ZOOMS[Math.min(ZOOMS.length - 1, Math.max(0, i + sens))]!;
}

/**
 * Visualiseur de documents du socle : panneau large qui glisse depuis la droite (plein écran
 * d'office sur mobile). **Une seule barre d'outils**, la nôtre : nom du fichier, zoom arrière,
 * niveau (« Largeur » ou %), zoom avant, Imprimer, Télécharger, Plein écran, Fermer ; celle du
 * lecteur PDF du navigateur est masquée. PDF à la largeur du panneau, pages empilées
 * verticalement, sans miniatures ; images ajustées puis zoomables. Échap ferme ; le focus va au
 * panneau à l'ouverture et revient à l'élément déclencheur à la fermeture.
 */
export function VisualiseurDocument({
  document,
  surFermeture,
  libelles,
}: VisualiseurDocumentProps) {
  const [pleinEcran, setPleinEcran] = useState(false);
  // Zoom propre au document affiché : un nouveau document s'ouvre à la largeur du panneau.
  const [choixZoom, setChoixZoom] = useState<{ url?: string; zoom: Zoom }>({ zoom: "largeur" });
  const panneau = useRef<HTMLElement>(null);
  const cadre = useRef<HTMLIFrameElement>(null);
  const declencheur = useRef<Element | null>(null);
  const idTitre = useId();
  const ouvert = document !== null;
  const url = document?.urlAffichage;
  const zoom: Zoom = choixZoom.url === url ? choixZoom.zoom : "largeur";
  const setZoom = (suivant: Zoom) => setChoixZoom({ url, zoom: suivant });

  useEffect(() => {
    if (!ouvert) return;
    declencheur.current = globalThis.document.activeElement;
    panneau.current?.focus();
    function surTouche(e: KeyboardEvent) {
      if (e.key === "Escape") surFermeture();
    }
    globalThis.document.addEventListener("keydown", surTouche);
    return () => {
      globalThis.document.removeEventListener("keydown", surTouche);
      if (declencheur.current instanceof HTMLElement) declencheur.current.focus();
    };
  }, [ouvert, surFermeture]);

  if (!document) return null;
  const pdf = document.typeMime === "application/pdf";
  const image = document.typeMime.startsWith("image/");
  const bouton =
    "inline-flex size-11 shrink-0 items-center justify-center rounded-lg text-navy hover:bg-ice disabled:cursor-not-allowed disabled:opacity-40";
  const minimum = zoom !== "largeur" && zoom === ZOOMS[0];
  const maximum = zoom !== "largeur" && zoom === ZOOMS[ZOOMS.length - 1];

  function imprimer() {
    // Le PDF est servi par le relais de l'application (même origine) : le cadre s'imprime seul ;
    // sinon (navigateur qui refuse), il s'ouvre dans un onglet où l'impression reste possible.
    try {
      cadre.current?.contentWindow?.focus();
      cadre.current?.contentWindow?.print();
    } catch {
      globalThis.open(document?.urlAffichage, "_blank", "noopener");
    }
  }

  // Portail vers <body> : le panneau échappe au contexte d'empilement de l'endroit où il est ouvert
  // (ligne de tableau, carte) ; sinon l'en-tête de l'application peut masquer son bouton Fermer.
  return createPortal(
    <>
      {/* Fond assombri : la page reste visible ; un clic à côté du panneau le ferme (Échap ne
          fonctionne plus quand le lecteur PDF du navigateur a le focus). */}
      <div
        aria-hidden="true"
        onClick={surFermeture}
        className="fixed inset-0 z-50 bg-navy/30 motion-safe:animate-[apparaitre_150ms_ease-out]"
      />
      <aside
        ref={panneau}
        tabIndex={-1}
        aria-labelledby={idTitre}
        className={cn(
          "fixed inset-y-0 right-0 z-50 flex w-full flex-col border-l border-bordure bg-white shadow-2xl outline-none",
          "motion-safe:animate-[glisser_200ms_ease-out]",
          pleinEcran ? "sm:w-full" : "sm:w-[min(72rem,80vw)]",
        )}
      >
        <style>
          {
            "@keyframes glisser{from{transform:translateX(100%)}to{transform:translateX(0)}}@keyframes apparaitre{from{opacity:0}to{opacity:1}}"
          }
        </style>
        <div className="flex min-h-16 flex-wrap items-center gap-1 border-b border-bordure px-2 py-1 sm:gap-2 sm:px-4">
          <p
            id={idTitre}
            className="min-w-0 flex-1 basis-40 truncate font-semibold text-navy"
            title={document.nomFichier}
          >
            {document.nomFichier}
          </p>
          {pdf || image ? (
            <div role="group" aria-label={libelles.ajuster} className="flex items-center">
              <button
                type="button"
                onClick={() => setZoom(zoomVoisin(zoom, -1))}
                disabled={minimum}
                className={bouton}
              >
                <ZoomOut aria-hidden="true" className="size-5" />
                <span className="sr-only">{libelles.zoomArriere}</span>
              </button>
              <button
                type="button"
                onClick={() => setZoom("largeur")}
                aria-pressed={zoom === "largeur"}
                className="min-h-11 min-w-16 rounded-lg px-2 text-sm font-semibold text-navy tabular-nums hover:bg-ice"
              >
                <span aria-live="polite">
                  {zoom === "largeur" ? libelles.ajuster : `${zoom} %`}
                </span>
              </button>
              <button
                type="button"
                onClick={() => setZoom(zoomVoisin(zoom, 1))}
                disabled={maximum}
                className={bouton}
              >
                <ZoomIn aria-hidden="true" className="size-5" />
                <span className="sr-only">{libelles.zoomAvant}</span>
              </button>
            </div>
          ) : null}
          {pdf ? (
            <button type="button" onClick={imprimer} className={bouton}>
              <Printer aria-hidden="true" className="size-5" />
              <span className="sr-only">{libelles.imprimer}</span>
            </button>
          ) : null}
          <a href={document.urlTelechargement} download={document.nomFichier} className={bouton}>
            <Download aria-hidden="true" className="size-5" />
            <span className="sr-only">{libelles.telecharger}</span>
          </a>
          <button
            type="button"
            onClick={() => setPleinEcran((p) => !p)}
            aria-pressed={pleinEcran}
            className={cn(bouton, "hidden sm:inline-flex")}
          >
            {pleinEcran ? (
              <Minimize2 aria-hidden="true" className="size-5" />
            ) : (
              <Maximize2 aria-hidden="true" className="size-5" />
            )}
            <span className="sr-only">
              {pleinEcran ? libelles.quitterPleinEcran : libelles.pleinEcran}
            </span>
          </button>
          {/* Fermer : toujours visible, avec son libellé (pas une simple croix). */}
          <button
            type="button"
            onClick={surFermeture}
            className="inline-flex min-h-11 shrink-0 items-center gap-2 rounded-lg border-2 border-royal bg-white px-3 font-semibold text-royal hover:bg-ice"
          >
            <X aria-hidden="true" className="size-5" />
            <span className="sr-only sm:not-sr-only">{libelles.fermer}</span>
          </button>
        </div>
        {document.avertissement ? (
          <div className="border-b border-bordure px-3 py-2 sm:px-4">
            <Alerte nature="erreur" titre={document.avertissement} />
          </div>
        ) : null}
        <div className="min-h-0 flex-1 bg-fond">
          {pdf ? (
            <iframe
              // Une nouvelle adresse (zoom) recharge le cadre : le lecteur relit ses paramètres.
              key={zoom}
              ref={cadre}
              title={document.nomFichier}
              src={adressePdf(document.urlAffichage, zoom)}
              className="size-full border-0"
            />
          ) : image ? (
            <div
              className={cn(
                "flex size-full overflow-auto p-4",
                zoom === "largeur" && "items-center justify-center",
              )}
            >
              {/* eslint-disable-next-line @next/next/no-img-element -- contenu privé servi par le BFF */}
              <img
                src={document.urlAffichage}
                alt={document.nomFichier}
                style={zoom === "largeur" ? undefined : { width: `${zoom}%`, maxWidth: "none" }}
                className={cn(
                  zoom === "largeur" && "max-h-full max-w-full object-contain",
                  zoom !== "largeur" && "m-auto h-auto",
                )}
              />
            </div>
          ) : (
            <div className="flex size-full flex-col items-center justify-center gap-3 p-6 text-center">
              <FileQuestion aria-hidden="true" className="size-8 text-texte-doux" />
              <p>{libelles.formatNonAffichable}</p>
            </div>
          )}
        </div>
      </aside>
    </>,
    globalThis.document.body,
  );
}
