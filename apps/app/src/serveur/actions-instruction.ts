"use server";

import { declarerRecevabilite, type ResultatRecevabiliteDto } from "@emine/api-client";
import { enTetesApi, lireSession } from "./api";
import { type ResultatAction, versErreurFormulaire } from "./erreurs-formulaire";

/**
 * Déclare une demande recevable, ou non recevable avec un motif (US-05.1). Exécuté côté serveur
 * (BFF) : le jeton ne quitte jamais le serveur Next ; les règles sont vérifiées par l'API.
 */
export async function declarer(
  id: number,
  recevable: boolean,
  motif: string | null,
): Promise<ResultatAction<ResultatRecevabiliteDto>> {
  try {
    const session = await lireSession();
    const reponse = await declarerRecevabilite(
      id,
      { recevable, motif: recevable ? null : motif },
      { headers: await enTetesApi(session) },
    );
    return { ok: true, valeur: reponse.data };
  } catch (e) {
    return { ok: false, erreur: versErreurFormulaire(e) };
  }
}
