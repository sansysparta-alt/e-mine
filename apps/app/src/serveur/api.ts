import { getIronSession } from "iron-session";
import { cookies, headers } from "next/headers";
import { optionsSession, type SessionEmine } from "./session";

/** Session courante en lecture (Server Components). Le renouvellement est fait par le proxy. */
export async function lireSession(): Promise<SessionEmine> {
  return getIronSession<SessionEmine>(await cookies(), optionsSession());
}

/**
 * En-têtes d'un appel à l'API au nom de l'utilisateur : jeton d'accès (côté serveur uniquement)
 * et adresse IP du client pour le journal d'audit.
 */
export async function enTetesApi(session: SessionEmine): Promise<HeadersInit> {
  const entrants = await headers();
  const ip = entrants.get("x-forwarded-for")?.split(",")[0]?.trim() ?? entrants.get("x-real-ip");
  return {
    Authorization: `Bearer ${session.jetonAcces ?? ""}`,
    ...(ip ? { "X-Forwarded-For": ip } : {}),
  };
}
