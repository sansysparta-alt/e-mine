import { NextResponse } from "next/server";
import { enTetesApi, lireSession } from "./api";
import { configuration } from "./config";

/** En-têtes de réponse de l'API recopiés vers le navigateur. */
const EN_TETES_RECOPIES = [
  "content-type",
  "content-length",
  "content-disposition",
  "x-content-type-options",
];

/**
 * Une requête qui modifie des données doit venir de l'application elle-même (en plus du cookie
 * SameSite=Lax) : protection contre la falsification de requête intersite.
 */
export function origineAutorisee(requete: Request, urlApplication: string): boolean {
  if (requete.method === "GET" || requete.method === "HEAD") return true;
  const origine = requete.headers.get("origin");
  return origine !== null && origine === new URL(urlApplication).origin;
}

/** Identifiant numérique de chemin, sinon `null` (aucune autre valeur n'est relayée à l'API). */
export function identifiant(valeur: string): string | null {
  return /^\d{1,18}$/.test(valeur) ? valeur : null;
}

/** Référence de paiement `PAY-AAAA-NNNNNN`, sinon `null` (aucune autre valeur n'est relayée). */
export function referencePaiement(valeur: string): string | null {
  return /^PAY-\d{4}-\d{6}$/.test(valeur) ? valeur : null;
}

/**
 * Relaie une requête du navigateur vers l'API au nom de l'utilisateur connecté (BFF) : le jeton
 * d'accès est ajouté ici, côté serveur, et ne parvient jamais au navigateur.
 */
export async function relayer(requete: Request, chemin: string): Promise<Response> {
  if (!origineAutorisee(requete, configuration().urlApplication)) {
    return NextResponse.json(
      { code: "PERM-001", message: "Origine non autorisée." },
      { status: 403 },
    );
  }
  const session = await lireSession();
  if (!session.jetonAcces) {
    return NextResponse.json({ code: "AUTH-001", message: "Session expirée." }, { status: 401 });
  }
  const enTetes = new Headers(await enTetesApi(session));
  const type = requete.headers.get("content-type");
  if (type) enTetes.set("content-type", type);
  const avecCorps = requete.method !== "GET" && requete.method !== "HEAD";
  const reponse = await fetch(`${process.env.API_BASE_URL ?? "http://localhost:8080"}${chemin}`, {
    method: requete.method,
    headers: enTetes,
    body: avecCorps ? requete.body : undefined,
    // Corps transmis en flux (fichiers jusqu'à 10 Mo) sans être chargé en mémoire.
    ...(avecCorps ? { duplex: "half" } : {}),
    cache: "no-store",
  } as RequestInit);
  const retour = new Headers();
  for (const nom of EN_TETES_RECOPIES) {
    const valeur = reponse.headers.get(nom);
    if (valeur) retour.set(nom, valeur);
  }
  return new Response(reponse.body, { status: reponse.status, headers: retour });
}
