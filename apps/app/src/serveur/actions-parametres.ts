"use server";

import {
  type CanauxDto,
  type ChoixDto,
  modifierCanauxNotification,
  modifierParametresDelai,
  type ParametresDelaiDto,
} from "@emine/api-client";
import { enTetesApi, lireSession } from "./api";
import { type ResultatAction, versErreurFormulaire } from "./erreurs-formulaire";

/** Règle des délais (US-02.6) : immédiat et tracé ; la base contrôle les droits. */
export async function enregistrerDelais(
  p: ParametresDelaiDto,
): Promise<ResultatAction<ParametresDelaiDto>> {
  try {
    const r = await modifierParametresDelai(p, { headers: await enTetesApi(await lireSession()) });
    return { ok: true, valeur: r.data };
  } catch (e) {
    return { ok: false, erreur: versErreurFormulaire(e) };
  }
}

/** Canaux des notifications (US-02.7) : l’espace en ligne reste toujours actif. */
export async function enregistrerCanaux(choix: ChoixDto[]): Promise<ResultatAction<CanauxDto[]>> {
  try {
    const r = await modifierCanauxNotification(choix, {
      headers: await enTetesApi(await lireSession()),
    });
    return { ok: true, valeur: r.data };
  } catch (e) {
    return { ok: false, erreur: versErreurFormulaire(e) };
  }
}
