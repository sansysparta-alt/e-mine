"use server";

import {
  creerBrouillon,
  type DemandeDto,
  deposerDemande,
  modifierBrouillon,
} from "@emine/api-client";
import {
  schemaBrouillon,
  type ValeursBrouillon,
  versBrouillonDto,
} from "@/composants/demande/schema";
import { enTetesApi, lireSession } from "./api";
import { type ResultatAction, versErreurFormulaire } from "./erreurs-formulaire";

/**
 * Enregistre le brouillon (US-03.1) : le crée s'il n'a pas encore d'identifiant, le met à jour
 * sinon. Exécuté côté serveur (BFF) : le jeton ne quitte jamais le serveur Next.
 */
export async function enregistrerBrouillon(
  id: number | null,
  valeurs: ValeursBrouillon,
): Promise<ResultatAction<DemandeDto>> {
  const saisie = schemaBrouillon.safeParse(valeurs);
  if (!saisie.success) {
    return {
      ok: false,
      erreur: {
        code: "VAL-001",
        message: "",
        champs: saisie.error.issues.map((i) => ({
          champ: String(i.path[0]),
          message: i.message,
        })),
      },
    };
  }
  try {
    const session = await lireSession();
    const options = { headers: await enTetesApi(session) };
    const corps = versBrouillonDto(saisie.data);
    const reponse =
      id === null
        ? await creerBrouillon(corps, options)
        : await modifierBrouillon(id, corps, options);
    return { ok: true, valeur: reponse.data };
  } catch (e) {
    return { ok: false, erreur: versErreurFormulaire(e) };
  }
}

/**
 * Dépose la demande (US-03.4) après acceptation de la déclaration sur l'honneur. La base attribue
 * le numéro DEM-AAAA-NNNNNN.
 */
export async function deposer(
  id: number,
  declarationHonneur: boolean,
): Promise<ResultatAction<DemandeDto>> {
  try {
    const session = await lireSession();
    const reponse = await deposerDemande(
      id,
      { declarationHonneur },
      { headers: await enTetesApi(session) },
    );
    return { ok: true, valeur: reponse.data };
  } catch (e) {
    return { ok: false, erreur: versErreurFormulaire(e) };
  }
}
