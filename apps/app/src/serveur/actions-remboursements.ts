"use server";

import {
  approuverRegleRemboursement,
  executerRemboursement,
  proposerRegleRemboursement,
  refuserRegleRemboursement,
  refuserRemboursement,
  retirerRegleRemboursement,
  validerRemboursement,
} from "@emine/api-client";
import { enTetesApi, lireSession } from "./api";
import { type ResultatAction, versErreurFormulaire } from "./erreurs-formulaire";

/** Décision du Responsable fiscal sur un remboursement (US-04.10). */
export type DecisionRemboursement = "valider" | "refuser" | "executer";

/**
 * Décision sur un remboursement (US-04.10) : validation, refus motivé, ou enregistrement de l'ordre
 * de remboursement émis par le Trésor (exécution hors plateforme). Côté serveur (BFF) ; l'API
 * contrôle les droits et le cycle de vie.
 */
export async function deciderRemboursement(
  id: number,
  decision: DecisionRemboursement,
  texte: string,
): Promise<ResultatAction<null>> {
  try {
    const options = { headers: await enTetesApi(await lireSession()) };
    if (decision === "valider") await validerRemboursement(id, options);
    else if (decision === "refuser")
      await refuserRemboursement(id, { motif: texte.trim() }, options);
    else await executerRemboursement(id, { referenceOrdre: texte.trim() }, options);
    return { ok: true, valeur: null };
  } catch (e) {
    return { ok: false, erreur: versErreurFormulaire(e) };
  }
}

/** Proposition d'une règle de remboursement des frais (administrateur, décision V1-4). */
export async function proposerRegle(
  rembourserSiRejet: boolean,
  dateEffet: string,
  motif: string,
): Promise<ResultatAction<{ version: number }>> {
  try {
    const r = await proposerRegleRemboursement(
      { rembourserSiRejet, dateEffet, motif: motif.trim() },
      { headers: await enTetesApi(await lireSession()) },
    );
    return { ok: true, valeur: r.data };
  } catch (e) {
    return { ok: false, erreur: versErreurFormulaire(e) };
  }
}

/** Décision sur une proposition de règle : le Ministre valide ou refuse, l'administrateur retire. */
export async function deciderRegle(
  _code: string,
  version: number,
  decision: "approuver" | "refuser" | "retirer",
  motif: string | null,
): Promise<ResultatAction<null>> {
  try {
    const options = { headers: await enTetesApi(await lireSession()) };
    if (decision === "approuver") await approuverRegleRemboursement(version, options);
    else if (decision === "retirer") await retirerRegleRemboursement(version, options);
    else await refuserRegleRemboursement(version, { motif: (motif ?? "").trim() }, options);
    return { ok: true, valeur: null };
  } catch (e) {
    return { ok: false, erreur: versErreurFormulaire(e) };
  }
}
