import { NextResponse } from "next/server";
import { identifiant, relayer } from "@/serveur/relais";

type Contexte = { params: Promise<{ id: string }> };

async function chemin(contexte: Contexte): Promise<string | null> {
  const id = identifiant((await contexte.params).id);
  return id ? `/api/v1/demandes/${id}/pieces` : null;
}

const introuvable = () => NextResponse.json({ code: "SYS-002" }, { status: 404 });

/** Pièces de la demande (relais BFF vers l'API, US-03.2). */
export async function GET(requete: Request, contexte: Contexte) {
  const c = await chemin(contexte);
  return c ? relayer(requete, c) : introuvable();
}

/** Ajout d'une pièce : le fichier est relayé en flux vers l'API. */
export async function POST(requete: Request, contexte: Contexte) {
  const c = await chemin(contexte);
  return c ? relayer(requete, c) : introuvable();
}
