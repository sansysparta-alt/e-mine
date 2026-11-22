import { NextResponse } from "next/server";
import { identifiant, relayer } from "@/serveur/relais";

type Contexte = { params: Promise<{ id: string }> };

/**
 * Document délivré (PDF/A signé) d'une demande, relayé en flux (US-06.4) : téléchargement, ou
 * affichage dans le visualiseur (`?inline=1`). Seul ce paramètre est transmis à l'API.
 */
export async function GET(requete: Request, contexte: Contexte) {
  const { id } = await contexte.params;
  const d = identifiant(id);
  if (!d) return NextResponse.json({ code: "SYS-002" }, { status: 404 });
  const inline = new URL(requete.url).searchParams.get("inline") === "1";
  return relayer(requete, `/api/v1/demandes/${d}/document/contenu${inline ? "?inline=true" : ""}`);
}
