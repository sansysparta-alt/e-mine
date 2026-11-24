import { ErreurApi, listerDemandes } from "@emine/api-client";
import { NextResponse } from "next/server";
import { getLocale } from "next-intl/server";
import { libelleDocument } from "@/composants/referentiels";
import type { ResultatDemande } from "@/composants/recherche/recherche-globale";
import { chargerMoi, chargerReferentiels, optionsApi } from "@/serveur/donnees";

/** Nombre de dossiers proposés par la recherche globale. */
const PROPOSES = 6;
/** Plus petite page acceptée par l'API (tailles 10, 20, 50 ou 100). */
const TAILLE_API = 10;

/**
 * Recherche globale (barre du haut, Ctrl+K) : dossiers dont le numéro, le code ou le nom du
 * document contient le texte saisi. L'API ne renvoie que ce que le profil connecté a le droit de
 * voir (ses demandes pour un usager ; aucun brouillon d'usager pour un agent).
 */
export async function GET(requete: Request) {
  const q = new URL(requete.url).searchParams.get("q")?.trim() ?? "";
  if (q.length < 2 || q.length > 100) return NextResponse.json({ demandes: [] });
  try {
    const options = await optionsApi();
    const [page, moi, { documents }, langue] = await Promise.all([
      listerDemandes({ q, taille: TAILLE_API, tri: "date", sens: "desc" }, options),
      chargerMoi(),
      chargerReferentiels(),
      getLocale(),
    ]);
    const demandes: ResultatDemande[] = page.data.elements.slice(0, PROPOSES).map((d) => ({
      id: d.id,
      numero: d.numero,
      document: libelleDocument(
        documents.find((doc) => doc.code === d.typeDocumentCode),
        langue,
        d.typeDocumentCode,
      ),
      // Le demandeur n'a pas besoin de son propre nom ; l'agent, si.
      demandeur: moi.interne
        ? (d.demandeur.organisation ?? `${d.demandeur.prenom} ${d.demandeur.nom}`)
        : null,
    }));
    return NextResponse.json(
      { demandes, total: page.data.total },
      { headers: { "cache-control": "no-store" } },
    );
  } catch (e) {
    const statut = e instanceof ErreurApi && e.status === 401 ? 401 : 503;
    if (statut !== 401) console.error("Recherche globale : réponse de l'API inattendue", e);
    return NextResponse.json({ code: statut === 401 ? "AUTH-001" : "SYS-001" }, { status: statut });
  }
}
