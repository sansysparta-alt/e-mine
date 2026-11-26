"use client";

import type { PieceDto } from "@emine/api-client";
import { Alerte, BadgeStatut, cn, formaterTaille } from "@emine/ui";
import { Eye, FileText, FileUp, Loader2, RefreshCw, Trash2 } from "@emine/ui/icones";
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
  /** Libellé affiché de chaque pièce, dans la langue de l'interface (US-02.9) ; défaut : le code. */
  libelles?: Record<string, string>;
  /** Prévenu à chaque changement de la liste (récapitulatif, dépôt). */
  surChangement?: (pieces: PieceDto[]) => void;
  /**
   * Réponse à un complément (US-05.2) : seules les pièces ajoutées depuis cet instant (ISO 8601)
   * sont affichées, comptées et modifiables ; le dossier déjà déposé reste intact.
   */
  depuis?: string;
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
 * Pièces jointes d'un brouillon (US-03.2, correctifs J2 C5–C7, correctifs J4) : une liste
 * compacte, une ligne par pièce attendue du catalogue plus « Autre pièce », précédée de
 * l'avancement (pièces obligatoires fournies). Choisir un fichier ou le glisser sur la ligne
 * l'envoie aussitôt ; une pièce obligatoire déjà fournie se remplace en un geste ; chaque fichier
 * s'ouvre dans le visualiseur (consultation, puis téléchargement si besoin) ou se retire.
 */
export function SectionPieces({
  demandeId,
  piecesRequises,
  libelles,
  surChangement,
  depuis,
}: SectionPiecesProps) {
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
        if (!actif) return;
        const seuil = depuis ? Date.parse(depuis) : null;
        setPieces(seuil === null ? liste : liste.filter((p) => Date.parse(p.deposeLe) >= seuil));
      })
      .catch((e: Erreur) => {
        if (actif) setErreurChargement(e);
      });
    return () => {
      actif = false;
    };
  }, [url, depuis]);

  // Le parent (récapitulatif, dépôt) est prévenu après chaque changement de la liste, dans un
  // effet : jamais pendant le rendu ni dans une fonction de mise à jour d'état (interdit par React).
  useEffect(() => {
    if (pieces !== null) rappel.current?.(pieces);
  }, [pieces]);

  function mettreAJour(modifier: (liste: PieceDto[]) => PieceDto[]) {
    setPieces((avant) => modifier(avant ?? []));
  }

  const types = [...piecesRequises, AUTRE_PIECE];
  const fournies = piecesRequises.filter((p) => pieces?.some((x) => x.typePiece === p)).length;
  const total = piecesRequises.length;

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-col gap-2">
        <p className="text-texte-doux">{t("consigne")}</p>
        {total > 0 ? (
          <div className="flex items-center gap-3">
            <div
              role="progressbar"
              aria-valuemin={0}
              aria-valuemax={total}
              aria-valuenow={pieces === null ? undefined : fournies}
              aria-label={t("avancementLibelle")}
              className="h-2 flex-1 overflow-hidden rounded-full bg-fond ring-1 ring-bordure"
            >
              <div
                className={cn(
                  "h-full rounded-full transition-[width]",
                  fournies === total ? "bg-succes" : "bg-royal",
                )}
                style={{ width: `${pieces === null ? 0 : (fournies / total) * 100}%` }}
              />
            </div>
            <p className="shrink-0 text-sm font-semibold">
              {t("avancement", { fournies: pieces === null ? 0 : fournies, total })}
            </p>
          </div>
        ) : null}
      </div>
      {erreurChargement ? (
        <Alerte nature="erreur" titre={t("erreurs.chargement")}>
          {erreurChargement.message || null}
        </Alerte>
      ) : null}
      <ul className="flex flex-col divide-y divide-bordure rounded-carte border border-bordure bg-white">
        {types.map((type) => (
          <li key={type}>
            <LignePiece
              type={type}
              libelle={libelles?.[type]}
              obligatoire={type !== AUTRE_PIECE}
              pieces={pieces === null ? null : pieces.filter((p) => p.typePiece === type)}
              url={url}
              surAjout={(p) => mettreAJour((l) => [...l, p])}
              surRetrait={(p) => mettreAJour((l) => l.filter((x) => x.id !== p.id))}
              surConsultation={(p) => ouvrir(documentDePiece(demandeId, p))}
            />
          </li>
        ))}
      </ul>
      {visualiseur}
    </div>
  );
}

type LignePieceProps = {
  /** Libellé affiché (traduction de la pièce) ; défaut : le type. */
  libelle?: string;
  type: string;
  obligatoire: boolean;
  /** `null` pendant le chargement. */
  pieces: PieceDto[] | null;
  url: string;
  surAjout: (p: PieceDto) => void;
  surRetrait: (p: PieceDto) => void;
  surConsultation: (p: PieceDto) => void;
};

/** Une pièce attendue : état, bouton d'ajout (ou de remplacement), fichiers joints, erreurs. */
function LignePiece({
  type,
  libelle,
  obligatoire,
  pieces,
  url,
  surAjout,
  surRetrait,
  surConsultation,
}: LignePieceProps) {
  const t = useTranslations("demande.pieces");
  const id = useId();
  const champ = useRef<HTMLInputElement>(null);
  const [erreur, setErreur] = useState<Erreur | null>(null);
  const [message, setMessage] = useState("");
  const [envoi, setEnvoi] = useState(false);
  const [survol, setSurvol] = useState(false);
  const fichiers = pieces ?? [];
  const fournie = fichiers.length > 0;
  /** Une pièce obligatoire n'a qu'un fichier : en choisir un autre remplace le précédent. */
  const remplacer = obligatoire && fournie;
  const titre = type === AUTRE_PIECE ? t("autre") : (libelle ?? type);

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
      if (remplacer) {
        // Le nouveau fichier est enregistré : l'ancien peut être retiré sans risque de perte.
        for (const ancien of fichiers) {
          const s = await fetch(`${url}/${ancien.id}`, { method: "DELETE" });
          if (s.ok) surRetrait(ancien);
        }
        setMessage(t("remplacee", { nom: piece.nomFichier }));
      } else {
        setMessage(t("ajoutee", { nom: piece.nomFichier }));
      }
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

  function deposer(e: DragEvent<HTMLElement>) {
    e.preventDefault();
    setSurvol(false);
    void envoyer(e.dataTransfer.files[0]);
  }

  const action = envoi ? t("envoi") : remplacer ? t("remplacer") : t("ajouter");
  const IconeAction = envoi ? Loader2 : remplacer ? RefreshCw : FileUp;

  return (
    <section
      aria-labelledby={`${id}-titre`}
      aria-busy={envoi || undefined}
      onDragOver={(e) => {
        e.preventDefault();
        setSurvol(true);
      }}
      onDragLeave={(e) => {
        if (!e.currentTarget.contains(e.relatedTarget as Node | null)) setSurvol(false);
      }}
      onDrop={deposer}
      className={cn(
        "flex flex-col gap-2 px-4 py-3 transition-colors",
        survol && "bg-ice outline-2 -outline-offset-2 outline-royal outline-dashed",
        erreur && !survol && "bg-danger-fond/40",
      )}
    >
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:gap-4">
        <div className="flex min-w-0 flex-1 flex-wrap items-center gap-x-3 gap-y-1">
          <h3 id={`${id}-titre`} className="font-texte text-base font-semibold text-navy">
            {titre}
          </h3>
          {obligatoire ? (
            <BadgeStatut ton={fournie ? "succes" : "avertissement"}>
              {fournie ? t("fournie") : t("obligatoire")}
            </BadgeStatut>
          ) : (
            <BadgeStatut ton="neutre">{t("facultative")}</BadgeStatut>
          )}
          {survol ? (
            <span className="text-sm font-semibold text-royal">{t("deposerIci")}</span>
          ) : null}
        </div>
        <label
          htmlFor={`${id}-fichier`}
          className={cn(
            "inline-flex min-h-11 shrink-0 cursor-pointer items-center justify-center gap-2 self-start rounded-lg border-2 px-4 font-semibold sm:self-auto",
            "focus-within:outline-3 focus-within:outline-offset-2 focus-within:outline-azure",
            remplacer
              ? "border-bordure bg-white text-royal hover:border-royal"
              : "border-royal bg-white text-royal hover:bg-ice",
            envoi && "cursor-wait opacity-80",
          )}
        >
          <IconeAction
            aria-hidden="true"
            className={cn("size-5", envoi && "motion-safe:animate-spin")}
          />
          <span aria-hidden="true">{action}</span>
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
      </div>

      {fichiers.length > 0 ? (
        <ul className="flex flex-col gap-1">
          {fichiers.map((p) => (
            <li
              key={p.id}
              className="flex flex-wrap items-center gap-x-3 gap-y-1 rounded-lg bg-fond px-3 py-1"
            >
              <FileText aria-hidden="true" className="size-5 shrink-0 text-texte-doux" />
              <span
                className="min-w-0 flex-1 text-sm"
                title={t("empreinte", { valeur: p.empreinteSha256 })}
              >
                <span className="font-semibold break-all">{p.nomFichier}</span>
                <span className="text-texte-doux"> · {formaterTaille(p.tailleOctets)}</span>
              </span>
              <span className="flex items-center">
                <button
                  type="button"
                  onClick={() => surConsultation(p)}
                  className="inline-flex min-h-11 items-center gap-1 rounded-lg px-3 text-sm font-semibold text-royal hover:bg-ice"
                >
                  <Eye aria-hidden="true" className="size-4" />
                  {t("consulter")}
                  <span className="sr-only">{p.nomFichier}</span>
                </button>
                <button
                  type="button"
                  onClick={() => void retirer(p)}
                  className="inline-flex min-h-11 items-center gap-1 rounded-lg px-3 text-sm font-semibold text-danger hover:bg-danger-fond"
                >
                  <Trash2 aria-hidden="true" className="size-4" />
                  {t("retirer")}
                  <span className="sr-only">{p.nomFichier}</span>
                </button>
              </span>
            </li>
          ))}
        </ul>
      ) : null}

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
