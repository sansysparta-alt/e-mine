import { getIronSession } from "iron-session";
import { type NextRequest, NextResponse } from "next/server";
import { renouveler } from "@/serveur/oidc";
import { aRenouveler, optionsSession, type SessionEmine } from "@/serveur/session";

/** Pages accessibles sans être connecté. */
const PAGES_PUBLIQUES = new Set(["/"]);

/**
 * Prolonge la requête en cours avec le cookie de session renouvelé : la page, l'action serveur ou
 * le relais appelé par cette même requête lit ainsi le nouveau jeton d'accès (et non l'ancien,
 * expiré). Le cookie est aussi renvoyé au navigateur pour les requêtes suivantes.
 */
export function prolongerAvecCookie(
  requete: NextRequest,
  provisoire: NextResponse,
  nomCookie: string,
): NextResponse {
  const definitions = provisoire.headers.getSetCookie();
  const valeur = definitions
    .find((d) => d.startsWith(`${nomCookie}=`))
    ?.split(";")[0]
    ?.slice(nomCookie.length + 1);
  if (!valeur) return provisoire;
  const enTetes = new Headers(requete.headers);
  const cookies = requete.cookies
    .getAll()
    .filter((c) => c.name !== nomCookie)
    .map((c) => `${c.name}=${c.value}`);
  cookies.push(`${nomCookie}=${valeur}`);
  enTetes.set("cookie", cookies.join("; "));
  const finale = NextResponse.next({ request: { headers: enTetes } });
  for (const definition of definitions) {
    finale.headers.append("set-cookie", definition);
  }
  return finale;
}

/**
 * Protège les pages de l'application et renouvelle le jeton d'accès avant le rendu (un Server
 * Component ne peut pas écrire de cookie). Sans session valide : redirection vers la connexion,
 * en revenant ensuite à la page demandée ; pour les relais du BFF (/api/...), réponse 401 JSON.
 */
export async function proxy(requete: NextRequest) {
  const { pathname, search } = requete.nextUrl;
  if (PAGES_PUBLIQUES.has(pathname)) {
    return NextResponse.next();
  }

  const options = optionsSession();
  const provisoire = NextResponse.next();
  const session = await getIronSession<SessionEmine>(requete, provisoire, options);

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
      return prolongerAvecCookie(requete, provisoire, options.cookieName);
    } catch {
      const expiree = pathname.startsWith("/api/")
        ? NextResponse.json({ code: "AUTH-001" }, { status: 401 })
        : NextResponse.redirect(new URL("/?expiree=1", requete.url));
      const ancienne = await getIronSession<SessionEmine>(requete, expiree, options);
      ancienne.destroy();
      return expiree;
    }
  }
  return provisoire;
}

export const config = {
  matcher: ["/((?!api/auth|_next/static|_next/image|favicon.ico).*)"],
};
