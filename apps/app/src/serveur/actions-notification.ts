"use server";

import { marquerNotificationsLues } from "@emine/api-client";
import { enTetesApi, lireSession } from "./api";

/**
 * Marque toutes les notifications de l'utilisateur connecté comme lues (US-07.2). Exécuté côté
 * serveur (BFF) : le jeton ne quitte jamais le serveur Next. Renvoie le nombre de non lues restant.
 */
export async function marquerToutesLues(): Promise<number> {
  const reponse = await marquerNotificationsLues({
    headers: await enTetesApi(await lireSession()),
  });
  return reponse.data.nombre;
}
