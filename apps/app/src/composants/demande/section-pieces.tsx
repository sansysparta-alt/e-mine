"use client";

import type { PieceDto } from "@emine/api-client";
import {
  Alerte,
  BadgeStatut,
  classesAuDessusDuLien,
  classesLienEtire,
  classesLigneCliquable,
  cn,
  formaterTaille,
} from "@emine/ui";
import { Eye, FileUp, Loader2, Trash2 } from "@emine/ui/icones";
import { useTranslations } from "next-intl";
import { type DragEvent, useEffect, useId, useRef, useState } from "react";
import { documentDePiece, useVisualiseurDocument } from "../documents/visualiseur";

/** 10 Mo, comme l'API (DOC-002 au-delà). */
export const TAILLE_MAX_OCTETS = 10 * 1024 * 1024;
const TYPES_ACCEPTES = ["application/pdf", "image/jpeg", "image/png"];
const EXTENSIONS = /\.(pdf|jpe?g|png)$/i;
/** Libellé enregistré pour une pièce supplémentaire (facultative). */
export const AUTRE_PIECE = "Autre pièce";

/** Contrôle côté navigateur (l'API refait le contrôle, par la signature du fichier). */
export function controlerFichier(f: { name: string; size: number; type: string }): string | null {
  if (f.size > TAILLE_MAX_OCTETS) return "DOC-002";
  if (f.size === 0 || !(TYPES_ACCEPTES.includes(f.type) || EXTENSIONS.test(f.name))) {
    return "DOC-001";
  }
  return null;
}

export type SectionPiecesProps = {
  demandeId: number;
  piecesRequises: string[];
  /** Prévenu à chaque changement de la liste (récapitulatif, dépôt). */
  surChangement?: (pieces: PieceDto[]) => void;
};

type Erreur = { code: string; message?: string };

const CODES_CONNUS = ["DOC-001", "DOC-002", "DOC-003", "DEM-003", "AUTH-001"];

async function lireErreur(r: Response): Promise<Erreur> {
  try {
    const corps = (await r.json()) as { code?: string; message?: string };
    return { code: corps.code ?? `HTTP-${r.status}`, message: corps.message };
  } catch {
    return { code: r.status === 401 ? "AUTH-001" : `HTTP-${r.status}` };
  }
}

/**
 * Pièces jointes d'un brouillon (US-03.2, correctifs J2 C5–C7) : un bloc par pièce attendue du
 * catalogue, plus « Autre pièce ». Choisir ou glisser-déposer un fichier l'envoie aussitôt (un
 * seul geste) ; les erreurs s'affichent sur la pièce concernée ; chaque fichier se consulte dans le
 * visualiseur ou se retire.
 */
export function SectionPieces({ demandeId, piecesRequises, surChangement }: SectionPiecesProps) {
  const t = useTranslations("demande.pieces");
  const [pieces, setPieces] = useState<PieceDto[] | null>(null);
  const [erreurChargement, setErreurChargement] = useState<Erreur | null>(null);
  const { ouvrir, visualiseur } = useVisualiseurDocument();
  const url = `/api/demandes/${demandeId}/pieces`;

  // Rappel gardé dans une référence : le chargement initial ne dépend que de l'adresse.
  const rappel = useRef(surChangement);
  useEffect(() => {
    rappel.current = surChangement;
  }, [surChangement]);

  useEffect(() => {
    let actif = true;
    fetch(url)
      .then(async (r) => {
        if (!r.ok) throw await lireErreur(r);
        return (await r.json()) as PieceDto[];
      })
      .then((liste) => {
        if (actif) setPieces(liste);
      })
      .catch((e: Erreur) => {
        if (actif) setErreurChargement(e);
      });
    return () => {
      actif = false;
    };
  }, [url]);

  // Le parent (récapitulatif, dépôt) est prévenu après chaque changement de la liste, dans un
  // effet : jamais pendant le rendu ni dans une fonction de mise à jour d'état (interdit par React).
  useEffect(() => {
    if (pieces !== null) rappel.current?.(pieces);
  }, [pieces]);

  function mettreAJour(modifier: (liste: PieceDto[]) => PieceDto[]) {
    setPieces((avant) => modifier(avant ?? []));
  }

  const types = [...piecesRequises, AUTRE_PIECE];

  return (
    <div className="flex flex-col gap-4">
      <p className="text-texte-doux">{t("consigne")}</p>
      {erreurChargement ? (
        <Alerte nature="erreur" titre={t("erreurs.chargement")}>
          {erreurChargement.message || null}
        </Alerte>
      ) : null}
      {types.map((type) => (
        <BlocPiece
          key={type}
          type={type}
          obligatoire={type !== AUTRE_PIECE}
          pieces={pieces === null ? null : pieces.filter((p) => p.typePiece === type)}
          url={url}
          surAjout={(p) => mettreAJour((l) => [...l, p])}
          surRetrait={(p) => mettreAJour((l) => l.filter((x) => x.id !== p.id))}
          surConsultation={(p) => ouvrir(documentDePiece(demandeId, p))}
        />
      ))}
      {visualiseur}
    </div>
  );
}

type BlocPieceProps = {
  type: string;
  obligatoire: boolean;
  /** `null` pendant le chargement. */
  pieces: PieceDto[] | null;
  url: string;
  surAjout: (p: PieceDto) => void;
  surRetrait: (p: PieceDto) => void;
  surConsultation: (p: PieceDto) => void;
};

/** Une pièce attendue : état, fichiers joints, zone de dépôt et erreurs propres à cette pièce. */
function BlocPiece({
  type,
  obligatoire,
  pieces,
  url,
  surAjout,
  surRetrait,
  surConsultation,
}: BlocPieceProps) {
  const t = useTranslations("demande.pieces");
  const id = useId();
  const champ = useRef<HTMLInputElement>(null);
  const [erreur, setErreur] = useState<Erreur | null>(null);
  const [message, setMessage] = useState("");
  const [envoi, setEnvoi] = useState(false);
  const [survol, setSurvol] = useState(false);
  const fournie = (pieces?.length ?? 0) > 0;
  const titre = type === AUTRE_PIECE ? t("autre") : type;

  async function envoyer(fichier: File | undefined) {
    if (!fichier || envoi) return;
    setErreur(null);
    setMessage("");
    const refus = controlerFichier(fichier);
    if (refus) {
      setErreur({ code: refus });
      return;
    }
    setEnvoi(true);
    try {
      const donnees = new FormData();
      donnees.set("typePiece", type);
      donnees.set("fichier", fichier);
      const r = await fetch(url, { method: "POST", body: donnees });
      if (!r.ok) throw await lireErreur(r);
      const piece = (await r.json()) as PieceDto;
      surAjout(piece);
      setMessage(t("ajoutee", { nom: piece.nomFichier }));
    } catch (e) {
      setErreur(e as Erreur);
    } finally {
      setEnvoi(false);
      if (champ.current) champ.current.value = "";
    }
  }

  async function retirer(piece: PieceDto) {
    setErreur(null);
    setMessage("");
    const r = await fetch(`${url}/${piece.id}`, { method: "DELETE" });
    if (!r.ok) {
      setErreur(await lireErreur(r));
      return;
    }
    surRetrait(piece);
    setMessage(t("retiree", { nom: piece.nomFichier }));
  }

  function deposer(e: DragEvent<HTMLLabelElement>) {
    e.preventDefault();
    setSurvol(false);
    void envoyer(e.dataTransfer.files[0]);
  }

  return (
    <section
      aria-labelledby={`${id}-titre`}
      className={cn(
        "flex flex-col gap-3 rounded-carte border bg-white p-4",
        erreur ? "border-danger/50" : "border-bordure",
      )}
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 id={`${id}-titre`} className="text-lg">
          {titre}
        </h3>
        {obligatoire ? (
          <BadgeStatut ton={fournie ? "succes" : "avertissement"}>
            {fournie ? t("fournie") : t("obligatoire")}
          </BadgeStatut>
        ) : (
          <BadgeStatut ton="neutre">{t("facultative")}</BadgeStatut>
        )}
      </div>

      {pieces && pieces.length > 0 ? (
        <ul className="flex flex-col divide-y divide-bordure rounded-lg border border-bordure">
          {pieces.map((p) => (
            <li
              key={p.id}
              className={cn("flex flex-wrap items-center gap-2 px-3 py-2", classesLigneCliquable)}
            >
              <span className="min-w-0 flex-1">
                <span className="block font-semibold break-all">{p.nomFichier}</span>
                <span className="block text-sm text-texte-doux">
                  {formaterTaille(p.tailleOctets)} ·{" "}
                  <span title={p.empreinteSha256}>
                    {t("empreinte", { debut: p.empreinteSha256.slice(0, 12) })}
                  </span>
                </span>
              </span>
              <button
                type="button"
                onClick={() => surConsultation(p)}
                className={cn(
                  "inline-flex min-h-11 items-center gap-2 rounded-lg px-3 font-semibold text-royal",
                  classesLienEtire,
                )}
              >
                <Eye aria-hidden="true" className="size-5" />
                {t("consulter")}
                <span className="sr-only">{p.nomFichier}</span>
              </button>
              <button
                type="button"
                onClick={() => void retirer(p)}
                className={cn(
                  "inline-flex min-h-11 items-center gap-2 rounded-lg px-3 font-semibold text-danger hover:bg-danger-fond",
                  classesAuDessusDuLien,
                )}
              >
                <Trash2 aria-hidden="true" className="size-5" />
                {t("retirer")}
                <span className="sr-only">{p.nomFichier}</span>
              </button>
            </li>
          ))}
        </ul>
      ) : null}

      <label
        htmlFor={`${id}-fichier`}
        onDragOver={(e) => {
          e.preventDefault();
          setSurvol(true);
        }}
        onDragLeave={() => setSurvol(false)}
        onDrop={deposer}
        aria-busy={envoi || undefined}
        className={cn(
          "flex min-h-16 cursor-pointer items-center justify-center gap-3 rounded-lg border-2 border-dashed px-4 py-3 text-center font-semibold transition-colors",
          "focus-within:outline-3 focus-within:outline-offset-2 focus-within:outline-azure",
          survol
            ? "border-royal bg-ice text-royal"
            : "border-azure/60 bg-white text-royal hover:bg-ice",
          envoi && "cursor-wait opacity-80",
        )}
      >
        {envoi ? (
          <Loader2 aria-hidden="true" className="size-5 motion-safe:animate-spin" />
        ) : (
          <FileUp aria-hidden="true" className="size-5" />
        )}
        <span>
          {envoi ? t("envoi") : fournie ? t("ajouterAutre") : t("choisir")}
          <span className="block text-sm font-normal text-texte-doux">{t("aide")}</span>
        </span>
        <input
          id={`${id}-fichier`}
          ref={champ}
          type="file"
          accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png"
          disabled={envoi}
          aria-label={t("champ", { piece: titre })}
          aria-describedby={erreur ? `${id}-erreur` : undefined}
          onChange={(e) => void envoyer(e.target.files?.[0])}
          className="sr-only"
        />
      </label>

      {erreur ? (
        <div id={`${id}-erreur`}>
          <Alerte
            nature="erreur"
            titre={t(`erreurs.${CODES_CONNUS.includes(erreur.code) ? erreur.code : "autre"}`)}
          >
            {erreur.message || null}
          </Alerte>
        </div>
      ) : null}
      <p role="status" className={cn("text-sm text-succes", !message && "sr-only")}>
        {message}
      </p>
    </section>
  );
}
