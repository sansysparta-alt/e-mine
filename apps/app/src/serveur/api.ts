import { getIronSession } from "iron-session";
import { cookies, headers } from "next/headers";
import { getLocale } from "next-intl/server";
import { optionsSession, type SessionEmine } from "./session";

/** Session courante en lecture (Server Components). Le renouvellement est fait par le proxy. */
export async function lireSession(): Promise<SessionEmine> {
  return getIronSession<SessionEmine>(await cookies(), optionsSession());
}

/**
 * En-têtes d'un appel à l'API au nom de l'utilisateur : jeton d'accès (côté serveur uniquement),
 * adresse IP du client pour le journal d'audit et langue de l'interface (messages d'erreur et
 * notifications rédigés par l'API dans cette langue).
 */
export async function enTetesApi(session: SessionEmine): Promise<HeadersInit> {
  const entrants = await headers();
  const ip = entrants.get("x-forwarded-for")?.split(",")[0]?.trim() ?? entrants.get("x-real-ip");
  const langue = await getLocale().catch(() => "fr");
  return {
    Authorization: `Bearer ${session.jetonAcces ?? ""}`,
    "Accept-Language": langue,
    ...(ip ? { "X-Forwarded-For": ip } : {}),
  };
}
