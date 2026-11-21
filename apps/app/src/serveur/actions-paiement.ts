"use server";

import { choisirMoyenPaiement, type PaiementDto } from "@emine/api-client";
import { enTetesApi, lireSession } from "./api";
import { type ResultatAction, versErreurFormulaire } from "./erreurs-formulaire";

/**
 * Choix du moyen de paiement (US-04.2) : l'API crée le paiement au montant du barème et attribue la
 * référence PAY-AAAA-NNNNNN. Le navigateur ne transmet jamais de montant ; le jeton reste côté
 * serveur (BFF).
 */
export async function choisirMoyen(
  demandeId: number,
  moyen: "TRESORPAY" | "VIREMENT",
): Promise<ResultatAction<PaiementDto>> {
  try {
    const session = await lireSession();
    const reponse = await choisirMoyenPaiement(
      demandeId,
      { moyen },
      { headers: await enTetesApi(session) },
    );
    return { ok: true, valeur: reponse.data };
  } catch (e) {
    return { ok: false, erreur: versErreurFormulaire(e) };
  }
}
