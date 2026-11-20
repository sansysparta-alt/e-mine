import { getIronSession } from "iron-session";
import { type NextRequest, NextResponse } from "next/server";
import { renouveler } from "@/serveur/oidc";
import { aRenouveler, optionsSession, type SessionEmine } from "@/serveur/session";

/** Pages accessibles sans être connecté. */
const PAGES_PUBLIQUES = new Set(["/"]);

/**
 * Protège les pages de l'application et renouvelle le jeton d'accès avant le rendu (un Server
 * Component ne peut pas écrire de cookie). Sans session valide : redirection vers la connexion,
 * en revenant ensuite à la page demandée.
 */
export async function proxy(requete: NextRequest) {
  const { pathname, search } = requete.nextUrl;
  if (PAGES_PUBLIQUES.has(pathname)) {
    return NextResponse.next();
  }

  const reponse = NextResponse.next();
  const session = await getIronSession<SessionEmine>(requete, reponse, optionsSession());

  if (!session.jetonAcces) {
    // Appels du navigateur aux relais BFF (/api/...) : réponse JSON, pas de redirection.
    if (pathname.startsWith("/api/")) {
      return NextResponse.json({ code: "AUTH-001" }, { status: 401 });
    }
    const connexion = new URL("/api/auth/login", requete.url);
    connexion.searchParams.set("retour", `${pathname}${search}`);
    return NextResponse.redirect(connexion);
  }

  if (aRenouveler(session)) {
    try {
      if (!session.jetonRafraichissement) throw new Error("pas de jeton de rafraîchissement");
      Object.assign(session, await renouveler(session.jetonRafraichissement));
      await session.save();
    } catch {
      const expiree = pathname.startsWith("/api/")
        ? NextResponse.json({ code: "AUTH-001" }, { status: 401 })
        : NextResponse.redirect(new URL("/?expiree=1", requete.url));
      const ancienne = await getIronSession<SessionEmine>(requete, expiree, optionsSession());
      ancienne.destroy();
      return expiree;
    }
  }
  return reponse;
}

export const config = {
  matcher: ["/((?!api/auth|_next/static|_next/image|favicon.ico).*)"],
};
