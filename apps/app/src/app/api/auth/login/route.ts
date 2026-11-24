import { getIronSession } from "iron-session";
import { cookies } from "next/headers";
import { type NextRequest, NextResponse } from "next/server";
import { COOKIE_LANGUE } from "@/i18n/langues";
import { supplementsConnexion } from "@/serveur/connexion";
import { preparerConnexion } from "@/serveur/oidc";
import { cheminDeRetourSur } from "@/serveur/retour";
import { type FluxConnexion, optionsFluxConnexion } from "@/serveur/session";

/**
 * Démarre la connexion : redirection vers Keycloak (Authorization Code + PKCE), seule porte
 * d'entrée de l'application. Transmet la langue choisie, le motif du message à afficher
 * (déconnexion, session expirée, échec) et l'action demandée (changement de mot de passe).
 */
export async function GET(requete: NextRequest) {
  const p = requete.nextUrl.searchParams;
  try {
    const { url, verificateur, etat, nonce } = await preparerConnexion(
      supplementsConnexion({
        motif: p.get("motif"),
        action: p.get("action"),
        langue: (await cookies()).get(COOKIE_LANGUE)?.value,
      }),
    );
    const flux = await getIronSession<FluxConnexion>(await cookies(), optionsFluxConnexion());
    flux.verificateur = verificateur;
    flux.etat = etat;
    flux.nonce = nonce;
    flux.retour = cheminDeRetourSur(p.get("retour"));
    await flux.save();
    return NextResponse.redirect(url);
  } catch (erreur) {
    console.error("Connexion : Keycloak injoignable", erreur);
    return NextResponse.redirect(new URL("/?erreur=indisponible", requete.url));
  }
}
