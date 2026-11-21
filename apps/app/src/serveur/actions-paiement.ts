"use server";

import { choisirMoyenPaiement, type PaiementDto } from "@emine/api-client";
import { redirect } from "next/navigation";
import { enTetesApi, lireSession } from "./api";
import { type ResultatAction, versErreurFormulaire } from "./erreurs-formulaire";

/**
 * Choix du moyen de paiement (US-04.2) et paiement en ligne (US-04.3) : l'API crée le paiement au
 * montant du barème, ouvre la session TrésorPay et renvoie l'adresse de la page de paiement ; le
 * navigateur y est redirigé. Le navigateur ne transmet jamais de montant ; le jeton reste côté
 * serveur (BFF).
 */
export async function choisirMoyen(
  demandeId: number,
  moyen: "TRESORPAY" | "VIREMENT",
): Promise<ResultatAction<PaiementDto>> {
  let urlPaiement: string | null;
  let paiement: PaiementDto;
  try {
    const session = await lireSession();
    const reponse = await choisirMoyenPaiement(
      demandeId,
      { moyen },
      { headers: await enTetesApi(session) },
    );
    urlPaiement = reponse.data.urlPaiement;
    paiement = reponse.data.paiement;
  } catch (e) {
    return { ok: false, erreur: versErreurFormulaire(e) };
  }
  // Hors du try : redirect() interrompt l'action par une exception que Next.js doit recevoir.
  if (urlPaiement && /^https?:\/\//.test(urlPaiement)) {
    redirect(urlPaiement);
  }
  return { ok: true, valeur: paiement };
}
