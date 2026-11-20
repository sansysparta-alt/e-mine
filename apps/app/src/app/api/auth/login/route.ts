import { getIronSession } from "iron-session";
import { cookies } from "next/headers";
import { type NextRequest, NextResponse } from "next/server";
import { preparerConnexion } from "@/serveur/oidc";
import { cheminDeRetourSur } from "@/serveur/retour";
import { type FluxConnexion, optionsFluxConnexion } from "@/serveur/session";

/** Démarre la connexion : redirection vers Keycloak (Authorization Code + PKCE). */
export async function GET(requete: NextRequest) {
  try {
    const { url, verificateur, etat, nonce } = await preparerConnexion();
    const flux = await getIronSession<FluxConnexion>(await cookies(), optionsFluxConnexion());
    flux.verificateur = verificateur;
    flux.etat = etat;
    flux.nonce = nonce;
    flux.retour = cheminDeRetourSur(requete.nextUrl.searchParams.get("retour"));
    await flux.save();
    return NextResponse.redirect(url);
  } catch (erreur) {
    console.error("Connexion : Keycloak injoignable", erreur);
    return NextResponse.redirect(new URL("/?erreur=indisponible", requete.url));
  }
}
