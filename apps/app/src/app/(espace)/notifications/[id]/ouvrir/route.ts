import { marquerNotificationLue } from "@emine/api-client";
import { NextResponse } from "next/server";
import { optionsApi } from "@/serveur/donnees";
import { identifiant } from "@/serveur/relais";

type Contexte = { params: Promise<{ id: string }> };

/**
 * Ouverture d'une notification (US-07.2) : la marque comme lue (sans effet si elle l'est déjà)
 * puis ouvre le dossier concerné, ou revient à la liste. Un vrai lien : Ctrl+clic et clic du
 * milieu fonctionnent. Seuls des identifiants numériques sont acceptés.
 */
export async function GET(requete: Request, contexte: Contexte) {
  const { id } = await contexte.params;
  const notification = identifiant(id);
  const demande = identifiant(new URL(requete.url).searchParams.get("demande") ?? "");
  if (notification) {
    // Une erreur (notification d'un autre compte, API indisponible) n'empêche pas d'ouvrir le
    // dossier : la RLS de l'API protège déjà l'accès.
    await marquerNotificationLue(Number(notification), await optionsApi()).catch(() => undefined);
  }
  return NextResponse.redirect(
    new URL(demande ? `/demandes/${demande}` : "/notifications", requete.url),
  );
}
