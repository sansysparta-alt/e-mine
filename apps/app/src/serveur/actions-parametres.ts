"use server";

import {
  approuverRegleDelai,
  type CanauxDto,
  type ChoixDto,
  modifierCanauxNotification,
  type PropositionRegleDelaiDto,
  proposerRegleDelai,
  refuserRegleDelai,
  retirerRegleDelai,
  type VersionProposeeDto,
} from "@emine/api-client";
import { enTetesApi, lireSession } from "./api";
import { type ResultatAction, versErreurFormulaire } from "./erreurs-formulaire";

/**
 * Proposition d'une nouvelle règle des délais (US-02.6, US-02.8), à valider par le Ministre ; la
 * base contrôle les droits, refuse une seconde proposition ouverte (DOC-008) ou identique (DOC-010).
 */
export async function proposerDelais(
  p: PropositionRegleDelaiDto,
): Promise<ResultatAction<VersionProposeeDto>> {
  try {
    const r = await proposerRegleDelai(p, { headers: await enTetesApi(await lireSession()) });
    return { ok: true, valeur: r.data };
  } catch (e) {
    return { ok: false, erreur: versErreurFormulaire(e) };
  }
}

/** Décision sur une proposition : le Ministre valide ou refuse, l'administrateur retire. */
export async function deciderDelais(
  _code: string,
  version: number,
  decision: "approuver" | "refuser" | "retirer",
  motif: string | null,
): Promise<ResultatAction<null>> {
  try {
    const options = { headers: await enTetesApi(await lireSession()) };
    if (decision === "approuver") await approuverRegleDelai(version, options);
    else if (decision === "retirer") await retirerRegleDelai(version, options);
    else await refuserRegleDelai(version, { motif: (motif ?? "").trim() }, options);
    return { ok: true, valeur: null };
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
