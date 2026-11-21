import { getIronSession } from "iron-session";
import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { configuration } from "@/serveur/config";
import { renouveler } from "@/serveur/oidc";
import { origineAutorisee } from "@/serveur/relais";
import { optionsSession, type SessionEmine } from "@/serveur/session";

/**
 * Maintien de la session pendant l'activité de l'utilisateur (correctif A1) : renouvelle le jeton
 * auprès de Keycloak, ce qui repousse l'expiration pour inactivité (30 min). Appelé par le
 * navigateur seulement s'il y a eu une activité récente (frappe, clic, défilement).
 */
export async function POST(requete: Request) {
  if (!origineAutorisee(requete, configuration().urlApplication)) {
    return NextResponse.json({ code: "PERM-001" }, { status: 403 });
  }
  const session = await getIronSession<SessionEmine>(await cookies(), optionsSession());
  if (!session.jetonRafraichissement) {
    return NextResponse.json({ code: "AUTH-001" }, { status: 401 });
  }
  try {
    Object.assign(session, await renouveler(session.jetonRafraichissement));
    await session.save();
    return NextResponse.json({ expireA: session.expireA ?? null });
  } catch {
    session.destroy();
    return NextResponse.json({ code: "AUTH-001" }, { status: 401 });
  }
}
