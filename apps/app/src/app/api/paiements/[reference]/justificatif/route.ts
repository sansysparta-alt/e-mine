import { NextResponse } from "next/server";
import { referencePaiement, relayer } from "@/serveur/relais";

type Contexte = { params: Promise<{ reference: string }> };

/** Justificatif d'un virement déclaré (relais BFF en flux, US-04.7), affiché dans le visualiseur. */
export async function GET(requete: Request, contexte: Contexte) {
  const ref = referencePaiement((await contexte.params).reference);
  if (!ref) return NextResponse.json({ code: "SYS-002" }, { status: 404 });
  return relayer(requete, `/api/v1/paiements/${ref}/justificatif`);
}
