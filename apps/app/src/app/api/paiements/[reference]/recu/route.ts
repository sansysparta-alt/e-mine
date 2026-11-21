import { NextResponse } from "next/server";
import { referencePaiement, relayer } from "@/serveur/relais";

type Contexte = { params: Promise<{ reference: string }> };

/** Téléchargement du reçu PDF d'un paiement confirmé (relais BFF en flux, US-04.6). */
export async function GET(requete: Request, contexte: Contexte) {
  const { reference } = await contexte.params;
  const ref = referencePaiement(reference);
  if (!ref) return NextResponse.json({ code: "SYS-002" }, { status: 404 });
  return relayer(requete, `/api/v1/paiements/${ref}/recu`);
}
