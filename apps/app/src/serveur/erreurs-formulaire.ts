import { ErreurApi } from "@emine/api-client";

/** Erreur renvoyée au formulaire : code du catalogue de l'API, message affichable, champs. */
export type ErreurFormulaire = {
  code: string;
  message: string;
  champs: { champ: string; message: string }[];
  /** Éléments manquants signalés au dépôt (DEM-004). */
  manques?: string[];
  /** Pièces obligatoires du catalogue sans fichier joint (DEM-004). */
  piecesManquantes?: string[];
};

export type ResultatAction<T> = { ok: true; valeur: T } | { ok: false; erreur: ErreurFormulaire };

/** Traduit une erreur de l'API (ou une panne) en erreur affichable par le formulaire. */
export function versErreurFormulaire(e: unknown): ErreurFormulaire {
  if (e instanceof ErreurApi) {
    const champs = Array.isArray(e.details["champs"])
      ? (e.details["champs"] as { champ: string; message: string }[])
      : [];
    const manques = Array.isArray(e.details["manques"]) ? (e.details["manques"] as string[]) : [];
    const piecesManquantes = Array.isArray(e.details["piecesManquantes"])
      ? (e.details["piecesManquantes"] as string[])
      : [];
    return {
      code: e.code ?? `HTTP-${e.status}`,
      message: e.message,
      champs,
      manques,
      piecesManquantes,
    };
  }
  console.error("Formulaire de demande : API injoignable", e);
  return { code: "INDISPONIBLE", message: "", champs: [] };
}
