import { NextResponse } from "next/server";
import { identifiant, relayer } from "@/serveur/relais";

type Contexte = { params: Promise<{ id: string; pieceId: string }> };

/** Téléchargement d'une pièce (relais BFF en flux, US-03.2). */
export async function GET(requete: Request, contexte: Contexte) {
  const { id, pieceId } = await contexte.params;
  const d = identifiant(id);
  const p = identifiant(pieceId);
  if (!d || !p) return NextResponse.json({ code: "SYS-002" }, { status: 404 });
  return relayer(requete, `/api/v1/demandes/${d}/pieces/${p}/contenu`);
}
