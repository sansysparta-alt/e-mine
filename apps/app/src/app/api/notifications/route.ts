import { relayer } from "@/serveur/relais";

/** Plus petite page acceptée par l'API (le panneau en affiche 5). */
const TAILLE_API = 10;

/** Dernières notifications de l'utilisateur connecté (panneau de la cloche, barre du haut). */
export async function GET(requete: Request) {
  return relayer(requete, `/api/v1/notifications?page=1&taille=${TAILLE_API}`);
}
