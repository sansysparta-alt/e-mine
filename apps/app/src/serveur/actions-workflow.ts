"use server";

import { type ModificationDto, modifierWorkflow, type VersionCreeeDto } from "@emine/api-client";
import { enTetesApi, lireSession } from "./api";
import { type ResultatAction, versErreurFormulaire } from "./erreurs-formulaire";

/**
 * Nouvelle version d'un workflow (US-02.5) : immédiate et tracée. Exécuté côté serveur (BFF) ;
 * la base contrôle les droits et refuse une version identique (DOC-010).
 */
export async function enregistrerWorkflow(
  code: string,
  modification: ModificationDto,
): Promise<ResultatAction<VersionCreeeDto>> {
  try {
    const reponse = await modifierWorkflow(code, modification, {
      headers: await enTetesApi(await lireSession()),
    });
    return { ok: true, valeur: reponse.data };
  } catch (e) {
    return { ok: false, erreur: versErreurFormulaire(e) };
  }
}
