import { relayer } from "@/serveur/relais";

/** Nombre de notifications du panneau de la cloche. */
const TAILLE_PANNEAU = 5;

/** Dernières notifications de l'utilisateur connecté (panneau de la cloche, barre du haut). */
export async function GET(requete: Request) {
  return relayer(requete, `/api/v1/notifications?page=0&taille=${TAILLE_PANNEAU}`);
}
