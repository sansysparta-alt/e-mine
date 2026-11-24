import type { DocumentAffiche } from "@emine/ui";

// Module ordinaire (sans « use client ») : ces adresses servent aussi aux composants serveur, qui ne
// peuvent pas appeler une fonction exportée par un module client.

/** Adresse du reçu (relais BFF : le jeton reste côté serveur). */
export function adresseRecu(reference: string): string {
  return `/api/paiements/${encodeURIComponent(reference)}/recu`;
}

/** Reçu présenté au visualiseur : affichage en ligne, téléchargement depuis le panneau. */
export function documentDeRecu(reference: string, numero: string): DocumentAffiche {
  const base = adresseRecu(reference);
  return {
    nomFichier: `recu-${numero}.pdf`,
    typeMime: "application/pdf",
    urlAffichage: `${base}?inline=1`,
    urlTelechargement: base,
  };
}
