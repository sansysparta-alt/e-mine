"use client";

import { Loader2 } from "lucide-react";
import type * as ModulePdfJs from "pdfjs-dist";
import type { PDFDocumentProxy } from "pdfjs-dist";
import { useEffect, useRef, useState } from "react";
import { cn } from "../cn";

/** Zoom du lecteur : « à la largeur » de la zone de lecture, ou pourcentage de la taille réelle. */
export type ZoomPdf = "largeur" | number;

/** Largeur maximale d'une page « à la largeur » (px) : lisible même sur un très grand écran. */
export const LARGEUR_PAGE_MAX = 1200;

/** Marge autour des pages (px), de chaque côté. */
const MARGE = 16;

/** Points PDF (1/72 de pouce) vers pixels CSS (1/96 de pouce). */
const PT_EN_PX = 96 / 72;

/** Dimensions d'une page à 100 % (px CSS). */
type TaillePage = { largeur: number; hauteur: number };

/**
 * Échelle de rendu d'une page : « à la largeur » de la zone (marges déduites, plafonnée à
 * {@link LARGEUR_PAGE_MAX}), sinon le pourcentage de sa taille réelle.
 */
export function echellePage(page: TaillePage, zoom: ZoomPdf, largeurZone: number): number {
  if (zoom !== "largeur") return zoom / 100;
  const disponible = Math.max(200, Math.min(largeurZone - 2 * MARGE, LARGEUR_PAGE_MAX));
  return disponible / page.largeur;
}

/**
 * Page affichée d'après la part visible de chaque page : celle qui occupe le plus la zone de
 * lecture (la première en cas d'égalité). Numérotée à partir de 1.
 */
export function pageCourante(partsVisibles: readonly number[]): number {
  let meilleure = 0;
  for (let i = 1; i < partsVisibles.length; i++) {
    if ((partsVisibles[i] ?? 0) > (partsVisibles[meilleure] ?? 0)) meilleure = i;
  }
  return meilleure + 1;
}

type PdfJs = typeof ModulePdfJs;
type DocumentPdf = PDFDocumentProxy;

let pdfjs: Promise<PdfJs> | null = null;

const AUCUNE: TaillePage[] = [];

/** pdf.js, chargé à la première ouverture d'un PDF seulement (jamais côté serveur). */
function chargerPdfJs(): Promise<PdfJs> {
  pdfjs ??= import("pdfjs-dist").then((m) => {
    m.GlobalWorkerOptions.workerSrc = new URL(
      "pdfjs-dist/build/pdf.worker.min.mjs",
      import.meta.url,
    ).toString();
    return m;
  });
  return pdfjs;
}

export type LibellesLecteurPdf = {
  chargement: string;
  erreur: string;
  /** « Page 2 sur 5 » (libellé d'une page pour les lecteurs d'écran). */
  page: (numero: number, total: number) => string;
};

/**
 * Lecteur PDF du socle (pdf.js, décision du 25/11/2026) : pages **empilées verticalement**, à la
 * largeur de la zone de lecture ou au zoom choisi, rendues à mesure qu'elles approchent de l'écran
 * (un document long reste fluide), nettes sur les écrans haute densité. Signale le nombre de pages
 * et la page la plus visible (`surPage`) pour le compteur « Page X sur Y » de la barre d'outils.
 */
export function LecteurPdf({
  url,
  zoom,
  libelles,
  surPage,
}: {
  url: string;
  zoom: ZoomPdf;
  libelles: LibellesLecteurPdf;
  surPage?: (courante: number, total: number) => void;
}) {
  const zone = useRef<HTMLDivElement>(null);
  // État propre à l'adresse chargée : une nouvelle adresse repart de « chargement ».
  const [etat, setEtat] = useState<{
    url: string;
    document?: DocumentPdf;
    tailles?: TaillePage[];
    erreur?: boolean;
  } | null>(null);
  const document = etat?.url === url ? (etat.document ?? null) : null;
  const tailles = etat?.url === url ? (etat.tailles ?? AUCUNE) : AUCUNE;
  const erreur = etat?.url === url && etat.erreur === true;
  const [largeurZone, setLargeurZone] = useState(0);

  // Chargement du document (cookies de session envoyés : même origine, relais BFF).
  useEffect(() => {
    let annule = false;
    // Chargement en cours : détruit à la fermeture (requêtes et travailleur pdf.js libérés).
    let chargement: { destroy: () => Promise<void> } | null = null;
    chargerPdfJs()
      .then((m) => {
        const tache = m.getDocument({ url, withCredentials: true });
        chargement = tache;
        return tache.promise;
      })
      .then(async (doc) => {
        const pages = await Promise.all(
          Array.from({ length: doc.numPages }, (_, i) => doc.getPage(i + 1)),
        );
        if (annule) return;
        setEtat({
          url,
          document: doc,
          tailles: pages.map((p) => {
            const v = p.getViewport({ scale: PT_EN_PX });
            return { largeur: v.width, hauteur: v.height };
          }),
        });
      })
      .catch(() => {
        if (!annule) setEtat({ url, erreur: true });
      });
    return () => {
      annule = true;
      void (chargement as { destroy: () => Promise<void> } | null)?.destroy();
    };
  }, [url]);

  // Largeur de la zone de lecture (rotation d'un mobile, plein écran…).
  useEffect(() => {
    const el = zone.current;
    if (!el) return;
    const mesurer = () => setLargeurZone(el.clientWidth);
    mesurer();
    if (typeof ResizeObserver === "undefined") return;
    const obs = new ResizeObserver(mesurer);
    obs.observe(el);
    return () => obs.disconnect();
  }, []);

  // Page la plus visible → compteur.
  useEffect(() => {
    const el = zone.current;
    if (!el || tailles.length === 0) return;
    surPage?.(1, tailles.length);
    if (typeof IntersectionObserver === "undefined") return;
    const parts = new Array<number>(tailles.length).fill(0);
    const obs = new IntersectionObserver(
      (entrees) => {
        for (const e of entrees) {
          const i = Number((e.target as HTMLElement).dataset.page) - 1;
          parts[i] = e.isIntersecting ? e.intersectionRect.height : 0;
        }
        surPage?.(pageCourante(parts), tailles.length);
      },
      { root: el, threshold: [0, 0.1, 0.25, 0.5, 0.75, 1] },
    );
    el.querySelectorAll("[data-page]").forEach((p) => obs.observe(p));
    return () => obs.disconnect();
  }, [tailles, surPage]);

  if (erreur) {
    return (
      <div className="flex size-full items-center justify-center p-6 text-center">
        <p role="alert">{libelles.erreur}</p>
      </div>
    );
  }

  return (
    <div ref={zone} className="size-full overflow-auto">
      {document === null ? (
        <div
          role="status"
          className="flex size-full items-center justify-center gap-2 p-6 text-texte-doux"
        >
          <Loader2 aria-hidden="true" className="size-5 motion-safe:animate-spin" />
          {libelles.chargement}
        </div>
      ) : (
        <div className="flex w-max min-w-full flex-col items-center gap-4 p-4">
          {tailles.map((taille, i) => (
            <PagePdf
              key={`${url}-${i}`}
              document={document}
              numero={i + 1}
              echelle={echellePage(taille, zoom, largeurZone)}
              taille={taille}
              racine={zone}
              libelle={libelles.page(i + 1, tailles.length)}
            />
          ))}
        </div>
      )}
    </div>
  );
}

/** Une page : emplacement à la bonne taille tout de suite, rendue quand elle approche de l'écran. */
function PagePdf({
  document,
  numero,
  echelle,
  taille,
  racine,
  libelle,
}: {
  document: DocumentPdf;
  numero: number;
  echelle: number;
  taille: TaillePage;
  racine: React.RefObject<HTMLDivElement | null>;
  libelle: string;
}) {
  const conteneur = useRef<HTMLDivElement>(null);
  const toile = useRef<HTMLCanvasElement>(null);
  const coucheTexte = useRef<HTMLDivElement>(null);
  // Les deux premières pages tout de suite ; toutes si le navigateur ne sait pas observer.
  const [proche, setProche] = useState(numero <= 2 || typeof IntersectionObserver === "undefined");
  const [rendue, setRendue] = useState(false);
  const largeur = Math.floor(taille.largeur * echelle);
  const hauteur = Math.floor(taille.hauteur * echelle);

  useEffect(() => {
    const el = conteneur.current;
    if (proche || !el) return;
    const obs = new IntersectionObserver(
      ([e]) => {
        if (e?.isIntersecting) setProche(true);
      },
      { root: racine.current, rootMargin: "800px 0px" },
    );
    obs.observe(el);
    return () => obs.disconnect();
  }, [proche, racine]);

  useEffect(() => {
    if (!proche || !toile.current) return;
    let tache: { cancel: () => void; promise: Promise<void> } | null = null;
    let texte: { cancel: () => void } | null = null;
    let annule = false;
    const canvas = toile.current;
    const calque = coucheTexte.current;
    void Promise.all([document.getPage(numero), chargerPdfJs()]).then(([page, m]) => {
      if (annule) return;
      const densite = Math.min(globalThis.devicePixelRatio || 1, 3);
      const vue = page.getViewport({ scale: echelle * PT_EN_PX * densite });
      canvas.width = Math.floor(vue.width);
      canvas.height = Math.floor(vue.height);
      tache = page.render({ canvas, viewport: vue });
      tache.promise.then(
        () => !annule && setRendue(true),
        () => undefined,
      );
      // Couche texte (lot 5) : texte transparent posé sur l'image de la page, sélectionnable pour
      // la copie et lu par les lecteurs d'écran. Un PDF numérisé (sans texte) n'en a pas.
      if (!calque || typeof m.TextLayer !== "function") return;
      calque.replaceChildren();
      calque.style.setProperty("--total-scale-factor", String(echelle * PT_EN_PX));
      const couche = new m.TextLayer({
        textContentSource: page.streamTextContent(),
        container: calque,
        viewport: page.getViewport({ scale: echelle * PT_EN_PX }),
      });
      texte = couche;
      couche.render().catch(() => undefined);
    });
    return () => {
      annule = true;
      tache?.cancel();
      texte?.cancel();
    };
  }, [document, numero, echelle, proche]);

  return (
    <div
      ref={conteneur}
      data-page={numero}
      className={cn("relative shrink-0 bg-white shadow-md", !rendue && "animate-pulse")}
      style={{ width: largeur, height: hauteur }}
    >
      <canvas
        ref={toile}
        role="img"
        aria-label={libelle}
        className="block"
        style={{ width: largeur, height: hauteur }}
      />
      <div ref={coucheTexte} className="textLayer" />
    </div>
  );
}
