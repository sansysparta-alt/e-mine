"use server";

import {
  cloreComplement,
  type ComplementDto,
  decider,
  demanderComplement,
  relancerComplement,
  rendreAvis,
  repondreComplement,
  type ResultatAvisDto,
  type ResultatComplementDto,
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

/**
 * Demande un complément au demandeur (US-05.2) : motif obligatoire, pièces attendues facultatives.
 * L'API vérifie le droit « instruire » et l'étape de la demande.
 */
export async function demanderUnComplement(
  id: number,
  motif: string,
  pieces: string[],
): Promise<ResultatAction<ComplementDto>> {
  try {
    const session = await lireSession();
    const reponse = await demanderComplement(
      id,
      { motif: motif.trim(), pieces },
      { headers: await enTetesApi(session) },
    );
    return { ok: true, valeur: reponse.data };
  } catch (e) {
    return { ok: false, erreur: versErreurFormulaire(e) };
  }
}

/**
 * Réponse du demandeur au complément (US-05.2) : l'API vérifie que chaque pièce demandée est jointe
 * (ou qu'une réponse écrite est donnée) et renvoie la demande à l'étape d'où il a été demandé.
 */
export async function repondreAuComplement(
  id: number,
  message: string | null,
): Promise<ResultatAction<ResultatComplementDto>> {
  try {
    const session = await lireSession();
    const reponse = await repondreComplement(
      id,
      { message: message && message.trim() ? message.trim() : null },
      { headers: await enTetesApi(session) },
    );
    return { ok: true, valeur: reponse.data };
  } catch (e) {
    return { ok: false, erreur: versErreurFormulaire(e) };
  }
}

/** Relance le demandeur après le délai de réponse : nouveau délai de 5 jours (US-05.2). */
export async function relancerDemandeur(id: number): Promise<ResultatAction<ComplementDto>> {
  try {
    const session = await lireSession();
    const reponse = await relancerComplement(id, { headers: await enTetesApi(session) });
    return { ok: true, valeur: reponse.data };
  } catch (e) {
    return { ok: false, erreur: versErreurFormulaire(e) };
  }
}

/** Clôt le dossier sans réponse après le délai, avec un motif communiqué au demandeur (US-05.2). */
export async function cloreSansReponse(
  id: number,
  motif: string,
): Promise<ResultatAction<ResultatComplementDto>> {
  try {
    const session = await lireSession();
    const reponse = await cloreComplement(
      id,
      { motif: motif.trim() },
      { headers: await enTetesApi(session) },
    );
    return { ok: true, valeur: reponse.data };
  } catch (e) {
    return { ok: false, erreur: versErreurFormulaire(e) };
  }
}

/**
 * Rend l'avis motivé du profil connecté (US-05.3) : l'API vérifie que le catalogue désigne ce profil
 * pour le document et que la demande est à l'instruction.
 */
export async function rendreUnAvis(
  id: number,
  favorable: boolean,
  motif: string,
): Promise<ResultatAction<ResultatAvisDto>> {
  try {
    const session = await lireSession();
    const reponse = await rendreAvis(
      id,
      { favorable, motif: motif.trim() },
      { headers: await enTetesApi(session) },
    );
    return { ok: true, valeur: reponse.data };
  } catch (e) {
    return { ok: false, erreur: versErreurFormulaire(e) };
  }
}
