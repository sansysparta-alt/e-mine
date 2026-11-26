import { NextResponse } from "next/server";
import { referencePaiement, relayer } from "@/serveur/relais";

type Contexte = { params: Promise<{ reference: string }> };

/**
 * « J'ai effectué le virement » (relais BFF, US-04.7) : le justificatif est relayé en flux vers
 * l'API, qui contrôle format et taille et passe le paiement en attente du relevé bancaire.
 */
export async function POST(requete: Request, contexte: Contexte) {
  const ref = referencePaiement((await contexte.params).reference);
  if (!ref) return NextResponse.json({ code: "SYS-002" }, { status: 404 });
  return relayer(requete, `/api/v1/paiements/${ref}/virement`);
}
