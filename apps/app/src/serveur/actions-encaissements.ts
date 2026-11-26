"use server";

import { deciderLigneReleve, deciderPaiementAVerifier } from "@emine/api-client";
import { enTetesApi, lireSession } from "./api";
import { type ResultatAction, versErreurFormulaire } from "./erreurs-formulaire";

/** Décision du Responsable fiscal sur un élément « À vérifier » (US-04.9). */
export type DecisionEcart = "VALIDER" | "REJETER" | "ASSOCIER" | "CLASSER";

/**
 * Décision sur un élément « À vérifier » (US-04.9) : ligne de relevé (identifiant) ou paiement en
 * ligne (référence). Exécuté côté serveur (BFF) ; l'API contrôle les droits et la règle des
 * décisions possibles.
 */
export async function deciderEcart(
  cible: { ligneId: number } | { reference: string },
  decision: DecisionEcart,
  motif: string,
  reference: string,
): Promise<ResultatAction<null>> {
  try {
    const options = { headers: await enTetesApi(await lireSession()) };
    const corps = {
      decision,
      motif: motif.trim() || null,
      reference: reference.trim() || null,
    };
    if ("ligneId" in cible) await deciderLigneReleve(cible.ligneId, corps, options);
    else await deciderPaiementAVerifier(cible.reference, corps, options);
    return { ok: true, valeur: null };
  } catch (e) {
    return { ok: false, erreur: versErreurFormulaire(e) };
  }
}
