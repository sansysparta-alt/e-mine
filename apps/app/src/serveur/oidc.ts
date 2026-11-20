import * as client from "openid-client";
import { configuration } from "./config";
import type { SessionEmine } from "./session";

let decouverte: Promise<client.Configuration> | undefined;

/** Métadonnées OIDC de Keycloak (mises en cache) ; HTTP accepté uniquement pour localhost. */
export function configurationOidc(): Promise<client.Configuration> {
  if (!decouverte) {
    const c = configuration();
    const emetteur = new URL(c.emetteur);
    const local =
      emetteur.protocol === "http:" && ["localhost", "127.0.0.1"].includes(emetteur.hostname);
    decouverte = client
      .discovery(
        emetteur,
        c.clientId,
        c.secretClient,
        undefined,
        local ? { execute: [client.allowInsecureRequests] } : undefined,
      )
      .catch((e: unknown) => {
        decouverte = undefined;
        throw e;
      });
  }
  return decouverte;
}

/** Prépare la redirection vers Keycloak : Authorization Code + PKCE (S256), état et nonce. */
export async function preparerConnexion() {
  const oidc = await configurationOidc();
  const verificateur = client.randomPKCECodeVerifier();
  const etat = client.randomState();
  const nonce = client.randomNonce();
  const url = client.buildAuthorizationUrl(oidc, {
    redirect_uri: configuration().urlRetourOidc,
    scope: "openid profile email",
    code_challenge: await client.calculatePKCECodeChallenge(verificateur),
    code_challenge_method: "S256",
    state: etat,
    nonce,
  });
  return { url, verificateur, etat, nonce };
}

function versSession(jetons: client.TokenEndpointResponse & client.TokenEndpointResponseHelpers) {
  const session: SessionEmine = {
    jetonAcces: jetons.access_token,
    jetonRafraichissement: jetons.refresh_token,
    expireA: Math.floor(Date.now() / 1000) + (jetons.expiresIn() ?? 60),
  };
  return session;
}

/** Échange le code reçu au retour de Keycloak contre les jetons (vérifie état, nonce et PKCE). */
export async function terminerConnexion(
  urlDeRetour: URL,
  attendu: { verificateur: string; etat: string; nonce: string },
): Promise<SessionEmine> {
  const oidc = await configurationOidc();
  const jetons = await client.authorizationCodeGrant(oidc, urlDeRetour, {
    pkceCodeVerifier: attendu.verificateur,
    expectedState: attendu.etat,
    expectedNonce: attendu.nonce,
    idTokenExpected: true,
  });
  return versSession(jetons);
}

/** Renouvelle le jeton d'accès avec le jeton de rafraîchissement. */
export async function renouveler(jetonRafraichissement: string): Promise<SessionEmine> {
  const oidc = await configurationOidc();
  const jetons = await client.refreshTokenGrant(oidc, jetonRafraichissement);
  return {
    ...versSession(jetons),
    jetonRafraichissement: jetons.refresh_token ?? jetonRafraichissement,
  };
}

/** Ferme la session Keycloak côté serveur (sans page de confirmation dans le navigateur). */
export async function fermerSessionKeycloak(jetonRafraichissement: string): Promise<void> {
  const oidc = await configurationOidc();
  const fin = oidc.serverMetadata().end_session_endpoint;
  if (!fin) return;
  const c = configuration();
  await fetch(fin, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      client_id: c.clientId,
      client_secret: c.secretClient,
      refresh_token: jetonRafraichissement,
    }),
    cache: "no-store",
  });
}
