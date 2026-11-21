"use client";

import type { PieceDto } from "@emine/api-client";
import { Alerte, Bouton, classesChamp } from "@emine/ui";
import { useTranslations } from "next-intl";
import { useEffect, useId, useRef, useState } from "react";

/** 10 Mo, comme l'API (DOC-002 au-delà). */
export const TAILLE_MAX_OCTETS = 10 * 1024 * 1024;
const TYPES_ACCEPTES = ["application/pdf", "image/jpeg", "image/png"];
const EXTENSIONS = /\.(pdf|jpe?g|png)$/i;
export const AUTRE_PIECE = "Autre pièce";

/** Contrôle côté navigateur (l'API refait le contrôle, par la signature du fichier). */
export function controlerFichier(f: { name: string; size: number; type: string }): string | null {
  if (f.size > TAILLE_MAX_OCTETS) return "DOC-002";
  if (f.size === 0 || !(TYPES_ACCEPTES.includes(f.type) || EXTENSIONS.test(f.name)))
    return "DOC-001";
  return null;
}

export function formaterTaille(octets: number): string {
  if (octets < 1024) return `${octets} o`;
  if (octets < 1024 * 1024) return `${Math.round(octets / 1024)} Ko`;
  return `${(octets / (1024 * 1024)).toFixed(1).replace(".", ",")} Mo`;
}

export type SectionPiecesProps = {
  demandeId: number;
  piecesRequises: string[];
  /** Prévenu à chaque changement de la liste (récapitulatif, dépôt). */
  surChangement?: (pieces: PieceDto[]) => void;
};

type Erreur = { code: string; message?: string };

/**
 * Pièces jointes d'un brouillon (US-03.2) : ajout d'un PDF, JPG ou PNG de 10 Mo au plus, liste
 * avec empreinte, téléchargement et retrait. Les appels passent par les relais du BFF.
 */
export function SectionPieces({ demandeId, piecesRequises, surChangement }: SectionPiecesProps) {
  const t = useTranslations("demande.pieces");
  const id = useId();
  const champFichier = useRef<HTMLInputElement>(null);
  const [pieces, setPieces] = useState<PieceDto[] | null>(null);
  const [typePiece, setTypePiece] = useState(piecesRequises[0] ?? AUTRE_PIECE);
  const [erreur, setErreur] = useState<Erreur | null>(null);
  const [message, setMessage] = useState("");
  const [envoi, setEnvoi] = useState(false);
  const url = `/api/demandes/${demandeId}/pieces`;

  // Rappel gardé dans une référence : le chargement initial ne dépend que de l'adresse.
  const rappel = useRef(surChangement);
  useEffect(() => {
    rappel.current = surChangement;
  }, [surChangement]);

  function mettreAJour(liste: PieceDto[]) {
    setPieces(liste);
    rappel.current?.(liste);
  }

  useEffect(() => {
    let actif = true;
    fetch(url)
      .then(async (r) => {
        if (!r.ok) throw await lireErreur(r);
        return (await r.json()) as PieceDto[];
      })
      .then((liste) => {
        if (actif) {
          setPieces(liste);
          rappel.current?.(liste);
        }
      })
      .catch((e: Erreur) => {
        if (actif) setErreur(e);
      });
    return () => {
      actif = false;
    };
  }, [url]);

  async function joindre() {
    const fichier = champFichier.current?.files?.[0];
    setErreur(null);
    setMessage("");
    if (!fichier) {
      setErreur({ code: "aucunFichier" });
      return;
    }
    const refus = controlerFichier(fichier);
    if (refus) {
      setErreur({ code: refus });
      return;
    }
    setEnvoi(true);
    try {
      const donnees = new FormData();
      donnees.set("typePiece", typePiece);
      donnees.set("fichier", fichier);
      const r = await fetch(url, { method: "POST", body: donnees });
      if (!r.ok) throw await lireErreur(r);
      const piece = (await r.json()) as PieceDto;
      mettreAJour([...(pieces ?? []), piece]);
      setMessage(t("ajoutee", { nom: piece.nomFichier }));
      if (champFichier.current) champFichier.current.value = "";
    } catch (e) {
      setErreur(e as Erreur);
    } finally {
      setEnvoi(false);
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
    mettreAJour((pieces ?? []).filter((p) => p.id !== piece.id));
    setMessage(t("retiree", { nom: piece.nomFichier }));
  }

  const fournies = new Set((pieces ?? []).map((p) => p.typePiece));

  return (
    <div className="flex flex-col gap-5">
      <div>
        <p className="font-semibold">{t("attendues")}</p>
        <ul className="mt-1 flex flex-col gap-1">
          {piecesRequises.map((p) => (
            <li key={p}>
              {fournies.has(p) ? t("fournie", { piece: p }) : t("manquante", { piece: p })}
            </li>
          ))}
        </ul>
      </div>

      <fieldset className="flex flex-col gap-3 rounded-lg border border-bordure p-4">
        <legend className="px-1 font-semibold">{t("ajouter")}</legend>
        <label htmlFor={`${id}-type`} className="font-semibold">
          {t("type")}
        </label>
        <select
          id={`${id}-type`}
          value={typePiece}
          onChange={(e) => setTypePiece(e.target.value)}
          className={classesChamp}
        >
          {piecesRequises.map((p) => (
            <option key={p} value={p}>
              {p}
            </option>
          ))}
          <option value={AUTRE_PIECE}>{t("autre")}</option>
        </select>
        <label htmlFor={`${id}-fichier`} className="font-semibold">
          {t("fichier")}
        </label>
        <p id={`${id}-aide`} className="text-sm text-texte-doux">
          {t("aide")}
        </p>
        <input
          id={`${id}-fichier`}
          ref={champFichier}
          type="file"
          accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png"
          aria-describedby={`${id}-aide`}
          className="text-base"
        />
        <div>
          <Bouton type="button" variante="secondaire" onClick={joindre} disabled={envoi}>
            {envoi ? t("envoi") : t("joindre")}
          </Bouton>
        </div>
      </fieldset>

      {erreur ? (
        <Alerte nature="erreur" titre={t(titreErreur(erreur.code))}>
          {erreur.message || null}
        </Alerte>
      ) : null}
      <p role="status" className="min-h-6 text-texte-doux">
        {message}
      </p>

      {pieces === null && !erreur ? <p>{t("chargement")}</p> : null}
      {pieces !== null && pieces.length === 0 ? <p>{t("aucune")}</p> : null}
      {pieces !== null && pieces.length > 0 ? (
        <ul className="flex flex-col gap-3">
          {pieces.map((p) => (
            <li
              key={p.id}
              className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-bordure bg-white p-3"
            >
              <div className="min-w-0">
                <p className="font-semibold">{p.typePiece}</p>
                <a
                  href={`${url}/${p.id}/contenu`}
                  className="break-all text-royal underline"
                  download={p.nomFichier}
                >
                  {p.nomFichier}
                </a>
                <p className="text-sm text-texte-doux">
                  {formaterTaille(p.tailleOctets)} ·{" "}
                  <span title={p.empreinteSha256}>
                    {t("empreinte", { debut: p.empreinteSha256.slice(0, 12) })}
                  </span>
                </p>
              </div>
              <Bouton
                type="button"
                variante="discret"
                onClick={() => retirer(p)}
                aria-label={t("retirerLibelle", { nom: p.nomFichier })}
              >
                {t("retirer")}
              </Bouton>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}

const CODES_CONNUS = ["DOC-001", "DOC-002", "DOC-003", "DEM-003", "AUTH-001", "aucunFichier"];

function titreErreur(code: string): string {
  return `erreurs.${CODES_CONNUS.includes(code) ? code : "autre"}`;
}

async function lireErreur(r: Response): Promise<Erreur> {
  try {
    const corps = (await r.json()) as { code?: string; message?: string };
    return { code: corps.code ?? `HTTP-${r.status}`, message: corps.message };
  } catch {
    return { code: r.status === 401 ? "AUTH-001" : `HTTP-${r.status}` };
  }
}
