import { ErreurApi } from "@emine/api-client";

/** Erreur renvoyée au formulaire : code du catalogue de l'API, message affichable, champs. */
export type ErreurFormulaire = {
  code: string;
  message: string;
  champs: { champ: string; message: string }[];
};

export type ResultatAction<T> = { ok: true; valeur: T } | { ok: false; erreur: ErreurFormulaire };

/** Traduit une erreur de l'API (ou une panne) en erreur affichable par le formulaire. */
export function versErreurFormulaire(e: unknown): ErreurFormulaire {
  if (e instanceof ErreurApi) {
    const champs = Array.isArray(e.details["champs"])
      ? (e.details["champs"] as { champ: string; message: string }[])
      : [];
    return { code: e.code ?? `HTTP-${e.status}`, message: e.message, champs };
  }
  console.error("Formulaire de demande : API injoignable", e);
  return { code: "INDISPONIBLE", message: "", champs: [] };
}
