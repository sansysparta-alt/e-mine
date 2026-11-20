import type { SessionOptions } from "iron-session";
import { configuration } from "./config";

/**
 * Session du BFF : jetons Keycloak chiffrés dans un cookie httpOnly. Ils ne sont jamais lisibles
 * par le JavaScript du navigateur et ne transitent que vers le serveur Next.
 */
export type SessionEmine = {
  jetonAcces?: string;
  jetonRafraichissement?: string;
  /** Expiration du jeton d'accès, en secondes depuis l'époque Unix. */
  expireA?: number;
};

/** État temporaire du flux OIDC (entre la redirection vers Keycloak et le retour). */
export type FluxConnexion = {
  verificateur?: string;
  etat?: string;
  nonce?: string;
  retour?: string;
};

function optionsCookie(production: boolean) {
  return { httpOnly: true, secure: production, sameSite: "lax" as const, path: "/" };
}

export function optionsSession(): SessionOptions {
  const c = configuration();
  return {
    cookieName: "emine_session",
    password: c.secretSession,
    ttl: 10 * 60 * 60,
    cookieOptions: optionsCookie(c.production),
  };
}

export function optionsFluxConnexion(): SessionOptions {
  const c = configuration();
  return {
    cookieName: "emine_connexion",
    password: c.secretSession,
    ttl: 10 * 60,
    cookieOptions: optionsCookie(c.production),
  };
}

/** Le jeton d'accès doit être renouvelé s'il expire dans moins de 30 secondes. */
export function aRenouveler(session: SessionEmine, maintenant = Date.now() / 1000): boolean {
  return !session.expireA || session.expireA - 30 <= maintenant;
}
