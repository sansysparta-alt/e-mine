"use client";

import { Download, FileQuestion, Maximize2, Minimize2, X } from "lucide-react";
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

/**
 * Visualiseur de documents du socle : panneau qui glisse depuis la droite sans masquer la page
 * (la liste reste visible et utilisable à gauche). En-tête : nom du fichier, Télécharger, Plein
 * écran, Fermer ; Échap ferme ; plein écran d'office sur mobile. PDF affiché par le lecteur du
 * navigateur, images en taille ajustée. Le focus va au panneau à l'ouverture et revient à
 * l'élément déclencheur à la fermeture.
 */
export function VisualiseurDocument({
  document,
  surFermeture,
  libelles,
}: VisualiseurDocumentProps) {
  const [pleinEcran, setPleinEcran] = useState(false);
  const panneau = useRef<HTMLElement>(null);
  const declencheur = useRef<Element | null>(null);
  const idTitre = useId();
  const ouvert = document !== null;

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
    "inline-flex size-11 shrink-0 items-center justify-center rounded-lg text-navy hover:bg-ice";

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
          pleinEcran ? "sm:w-full" : "sm:w-[min(52rem,62vw)]",
        )}
      >
        <style>
          {
            "@keyframes glisser{from{transform:translateX(100%)}to{transform:translateX(0)}}@keyframes apparaitre{from{opacity:0}to{opacity:1}}"
          }
        </style>
        <div className="flex min-h-16 items-center gap-2 border-b border-bordure px-3 sm:px-4">
          <p
            id={idTitre}
            className="min-w-0 flex-1 truncate font-semibold text-navy"
            title={document.nomFichier}
          >
            {document.nomFichier}
          </p>
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
              title={document.nomFichier}
              src={document.urlAffichage}
              className="size-full border-0"
            />
          ) : image ? (
            <div className="flex size-full items-center justify-center overflow-auto p-4">
              {/* eslint-disable-next-line @next/next/no-img-element -- contenu privé servi par le BFF */}
              <img
                src={document.urlAffichage}
                alt={document.nomFichier}
                className="max-h-full max-w-full object-contain"
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
