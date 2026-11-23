import {
  ErreurApi,
  listerPrefectures,
  listerSubstances,
  listerTypesDocuments,
  lireMoi,
  type MoiDto,
} from "@emine/api-client";
import { notFound, redirect } from "next/navigation";
import { cache } from "react";
import { enTetesApi, lireSession } from "./api";

/** Options d'appel à l'API au nom de l'utilisateur connecté (jeton côté serveur uniquement). */
export async function optionsApi(): Promise<{ headers: HeadersInit }> {
  return { headers: await enTetesApi(await lireSession()) };
}

/**
 * Appel à l'API depuis une page : session expirée (401) → reconnexion puis retour à la page ;
 * accès refusé (403) ou objet inconnu (404) → page « introuvable », la même qu'une adresse inconnue
 * (rien n'indique qu'un écran ou un objet existe pour un autre profil). Les autres erreurs
 * remontent à l'appelant (page d'erreur de l'espace).
 */
export async function appelPage<T>(retour: string, appel: () => Promise<T>): Promise<T> {
  try {
    return await appel();
  } catch (e) {
    if (e instanceof ErreurApi && e.status === 401) {
      redirect(`/api/auth/login?retour=${encodeURIComponent(retour)}`);
    }
    if (e instanceof ErreurApi && (e.status === 403 || e.status === 404)) notFound();
    throw e;
  }
}

/** Utilisateur connecté, lu une seule fois par requête (en-tête et page). */
export const chargerMoi = cache(async (): Promise<MoiDto> => {
  return (await lireMoi(await optionsApi())).data;
});

/** Données de référence du formulaire de demande (catalogue et référentiels publics). */
export const chargerReferentiels = cache(async () => {
  const [documents, prefectures, substances] = await Promise.all([
    listerTypesDocuments(),
    listerPrefectures(),
    listerSubstances(),
  ]);
  return {
    documents: documents.data,
    prefectures: prefectures.data,
    substances: substances.data,
  };
});
