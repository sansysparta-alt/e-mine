"use server";

import {
  decider,
  declarerRecevabilite,
  delivrer,
  type DocumentDelivreDto,
  revoquerDocument,
  type ResultatDecisionDto,
  type ResultatRecevabiliteDto,
} from "@emine/api-client";
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

/**
 * Rend la décision du signataire (US-05.4) : favorable (observations facultatives) ou rejet
 * (motif obligatoire). L'API vérifie que l'utilisateur est le signataire du catalogue (D4).
 */
export async function rendreDecision(
  id: number,
  favorable: boolean,
  motif: string | null,
): Promise<ResultatAction<ResultatDecisionDto>> {
  try {
    const session = await lireSession();
    const reponse = await decider(
      id,
      { favorable, motif: motif && motif.trim() ? motif.trim() : null },
      { headers: await enTetesApi(session) },
    );
    return { ok: true, valeur: reponse.data };
  } catch (e) {
    return { ok: false, erreur: versErreurFormulaire(e) };
  }
}

/**
 * Signe et délivre le document (US-06.3) : l'API vérifie que l'utilisateur est le signataire du
 * catalogue (D4) et que les droits sont payés.
 */
export async function signerEtDelivrer(id: number): Promise<ResultatAction<DocumentDelivreDto>> {
  try {
    const session = await lireSession();
    const reponse = await delivrer(id, { headers: await enTetesApi(session) });
    return { ok: true, valeur: reponse.data };
  } catch (e) {
    return { ok: false, erreur: versErreurFormulaire(e) };
  }
}

/**
 * Révoque le document délivré (US-06.6) avec un motif : l'API réserve l'action aux profils Directeur
 * et Brigade.
 */
export async function revoquer(
  id: number,
  motif: string,
): Promise<ResultatAction<DocumentDelivreDto>> {
  try {
    const session = await lireSession();
    const reponse = await revoquerDocument(
      id,
      { motif: motif.trim() },
      { headers: await enTetesApi(session) },
    );
    return { ok: true, valeur: reponse.data };
  } catch (e) {
    return { ok: false, erreur: versErreurFormulaire(e) };
  }
}
