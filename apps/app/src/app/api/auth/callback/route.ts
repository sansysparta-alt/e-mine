import { getIronSession } from "iron-session";
import { cookies } from "next/headers";
import { type NextRequest, NextResponse } from "next/server";
import { configuration } from "@/serveur/config";
import { terminerConnexion } from "@/serveur/oidc";
import { cheminDeRetourSur } from "@/serveur/retour";
import {
  type FluxConnexion,
  optionsFluxConnexion,
  optionsSession,
  type SessionEmine,
} from "@/serveur/session";

/** Retour de Keycloak : vérifie état, nonce et PKCE, puis ouvre la session (cookie httpOnly). */
export async function GET(requete: NextRequest) {
  const c = configuration();
  const magasin = await cookies();
  const flux = await getIronSession<FluxConnexion>(magasin, optionsFluxConnexion());
  const { verificateur, etat, nonce } = flux;
  const retour = cheminDeRetourSur(flux.retour);
  flux.destroy();

  if (!verificateur || !etat || !nonce || requete.nextUrl.searchParams.has("error")) {
    return NextResponse.redirect(new URL("/?erreur=connexion", c.urlApplication));
  }
  try {
    const urlDeRetour = new URL(c.urlRetourOidc);
    urlDeRetour.search = requete.nextUrl.search;
    const jetons = await terminerConnexion(urlDeRetour, { verificateur, etat, nonce });
    const session = await getIronSession<SessionEmine>(magasin, optionsSession());
    Object.assign(session, jetons);
    await session.save();
    return NextResponse.redirect(new URL(retour, c.urlApplication));
  } catch (erreur) {
    console.error("Connexion : échec de l'échange du code", erreur);
    return NextResponse.redirect(new URL("/?erreur=connexion", c.urlApplication));
  }
}
