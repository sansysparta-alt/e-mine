"use server";

import {
  approuverWorkflow,
  type ModificationDto,
  proposerWorkflow,
  refuserWorkflow,
  retirerWorkflow,
  type VersionCreeeDto,
} from "@emine/api-client";
import { enTetesApi, lireSession } from "./api";
import { type ResultatAction, versErreurFormulaire } from "./erreurs-formulaire";

/**
 * Proposition d'une nouvelle version de workflow (US-02.5, US-02.8), à valider par le Ministre.
 * Exécuté côté serveur (BFF) ; la base contrôle les droits, refuse une seconde proposition ouverte
 * (DOC-008) et une version identique (DOC-010).
 */
export async function proposerVersionWorkflow(
  code: string,
  modification: ModificationDto,
): Promise<ResultatAction<VersionCreeeDto>> {
  try {
    const reponse = await proposerWorkflow(code, modification, {
      headers: await enTetesApi(await lireSession()),
    });
    return { ok: true, valeur: reponse.data };
  } catch (e) {
    return { ok: false, erreur: versErreurFormulaire(e) };
  }
}

/** Décision sur une proposition : le Ministre valide ou refuse, l'administrateur retire. */
export async function deciderWorkflow(
  code: string,
  version: number,
  decision: "approuver" | "refuser" | "retirer",
  motif: string | null,
): Promise<ResultatAction<null>> {
  try {
    const options = { headers: await enTetesApi(await lireSession()) };
    if (decision === "approuver") await approuverWorkflow(code, version, options);
    else if (decision === "retirer") await retirerWorkflow(code, version, options);
    else await refuserWorkflow(code, version, { motif: (motif ?? "").trim() }, options);
    return { ok: true, valeur: null };
  } catch (e) {
    return { ok: false, erreur: versErreurFormulaire(e) };
  }
}
