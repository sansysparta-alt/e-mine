import { getIronSession } from "iron-session";
import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { configuration } from "@/serveur/config";
import { adresseConnexion } from "@/serveur/connexion";
import { fermerSessionKeycloak } from "@/serveur/oidc";
import { optionsSession, type SessionEmine } from "@/serveur/session";

/**
 * Déconnexion (POST uniquement : un simple lien ou une image tierce ne peut pas déconnecter
 * l'utilisateur ; le cookie SameSite=Lax n'est pas envoyé par un formulaire d'un autre site).
 */
export async function POST() {
  const session = await getIronSession<SessionEmine>(await cookies(), optionsSession());
  const jeton = session.jetonRafraichissement;
  session.destroy();
  if (jeton) {
    try {
      await fermerSessionKeycloak(jeton);
    } catch (erreur) {
      console.error("Déconnexion : session Keycloak non fermée", erreur);
    }
  }
  // Retour direct à la page de connexion, avec le message « Vous avez été déconnecté ».
  return NextResponse.redirect(
    new URL(adresseConnexion("deconnecte"), configuration().urlApplication),
    303,
  );
}
