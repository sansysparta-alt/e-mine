import { NextResponse } from "next/server";
import { referencePaiement, relayer } from "@/serveur/relais";

type Contexte = { params: Promise<{ reference: string }> };

/**
 * Reçu PDF d'un paiement confirmé (relais BFF en flux, US-04.6) : téléchargement, ou affichage dans
 * le visualiseur (`?inline=1`, correctifs J4). Seul ce paramètre est transmis à l'API.
 */
export async function GET(requete: Request, contexte: Contexte) {
  const { reference } = await contexte.params;
  const ref = referencePaiement(reference);
  if (!ref) return NextResponse.json({ code: "SYS-002" }, { status: 404 });
  const inline = new URL(requete.url).searchParams.get("inline") === "1";
  return relayer(requete, `/api/v1/paiements/${ref}/recu${inline ? "?inline=true" : ""}`);
}
