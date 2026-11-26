import { relayer } from "@/serveur/relais";

/** Relevé d'exemple de démonstration (relais BFF, US-04.8), téléchargé en CSV. */
export async function GET(requete: Request) {
  return relayer(requete, "/api/v1/releves/exemple");
}
