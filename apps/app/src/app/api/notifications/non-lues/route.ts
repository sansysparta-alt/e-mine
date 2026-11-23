import { relayer } from "@/serveur/relais";

/** Nombre de notifications non lues de l'utilisateur connecté (cloche de la barre du haut). */
export async function GET(requete: Request) {
  return relayer(requete, "/api/v1/notifications/non-lues");
}
