import { relayer } from "@/serveur/relais";

/** Alertes de l'administrateur technique (cloche de la barre du haut). */
export async function GET(requete: Request) {
  return relayer(requete, "/api/v1/administration/alertes");
}
