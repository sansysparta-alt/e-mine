"use server";

import {
  approuverVersionFiche,
  type PropositionCreeeDto,
  type PropositionDto,
  proposerVersionFiche,
  refuserVersionFiche,
  retirerVersionFiche,
} from "@emine/api-client";
import { enTetesApi, lireSession } from "./api";
import { type ResultatAction, versErreurFormulaire } from "./erreurs-formulaire";

/**
 * Propose une nouvelle version de la fiche d'un document (US-02.2), soumise à l'approbation du
 * Ministre. Exécuté côté serveur (BFF) ; l'API et la base contrôlent les droits.
 */
export async function proposerFiche(
  code: string,
  proposition: PropositionDto,
): Promise<ResultatAction<PropositionCreeeDto>> {
  try {
    const reponse = await proposerVersionFiche(code, proposition, {
      headers: await enTetesApi(await lireSession()),
    });
    return { ok: true, valeur: reponse.data };
  } catch (e) {
    return { ok: false, erreur: versErreurFormulaire(e) };
  }
}

/** Décision sur une proposition : approbation ou refus (Ministre), retrait (administrateur). */
export async function deciderFiche(
  code: string,
  numero: number,
  decision: "approuver" | "refuser" | "retirer",
  motif: string | null,
): Promise<ResultatAction<null>> {
  try {
    const options = { headers: await enTetesApi(await lireSession()) };
    if (decision === "approuver") await approuverVersionFiche(code, numero, options);
    else if (decision === "retirer") await retirerVersionFiche(code, numero, options);
    else await refuserVersionFiche(code, numero, { motif: (motif ?? "").trim() }, options);
    return { ok: true, valeur: null };
  } catch (e) {
    return { ok: false, erreur: versErreurFormulaire(e) };
  }
}
