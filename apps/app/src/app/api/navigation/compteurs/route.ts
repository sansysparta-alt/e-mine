import { ErreurApi } from "@emine/api-client";
import { NextResponse } from "next/server";
import { compteursNavigation } from "@/serveur/compteurs-navigation";
import { chargerMoi } from "@/serveur/donnees";

/** Compteurs du menu de l'utilisateur connecté (relus par le navigateur à chaque changement de page). */
export async function GET() {
  try {
    return NextResponse.json(await compteursNavigation(await chargerMoi()), {
      headers: { "cache-control": "no-store" },
    });
  } catch (e) {
    const statut = e instanceof ErreurApi && e.status === 401 ? 401 : 503;
    return NextResponse.json({ code: statut === 401 ? "AUTH-001" : "SYS-001" }, { status: statut });
  }
}
