import { relayer } from "@/serveur/relais";

/** Import d'un relevé bancaire (relais BFF en flux, US-04.8) : réservé au Responsable fiscal. */
export async function POST(requete: Request) {
  return relayer(requete, "/api/v1/releves");
}
