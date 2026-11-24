"use client";

import "./globals.css";
import { useEffect } from "react";

/**
 * Dernier recours : erreur dans la disposition racine elle-même. Next remplace alors toute la page
 * (html et body) ; les traductions ne sont plus disponibles, d'où un texte fixe en français suivi de
 * l'anglais. Aucun détail technique affiché ; l'utilisateur peut recharger ou revenir à l'accueil.
 */
export default function ErreurGlobale({ error, reset }: { error: Error; reset: () => void }) {
  useEffect(() => {
    console.error(error);
  }, [error]);
  return (
    <html lang="fr">
      <body className="min-h-dvh bg-fond antialiased">
        <main
          id="contenu"
          className="mx-auto flex min-h-dvh max-w-xl flex-col justify-center gap-4 px-4 text-center"
        >
          <h1 className="text-2xl text-navy">La page n’a pas pu s’afficher.</h1>
          <p className="text-texte-doux">
            Une erreur inattendue est survenue. Vos données ne sont pas perdues. Réessayez dans un
            instant.
          </p>
          <p lang="en" className="text-sm text-texte-doux">
            The page could not be displayed. Your data is safe. Please try again shortly.
          </p>
          <div className="flex flex-col justify-center gap-3 sm:flex-row">
            <button
              type="button"
              onClick={reset}
              className="min-h-11 rounded-lg bg-royal px-5 font-semibold text-white"
            >
              Réessayer
            </button>
            <a
              href="/tableau-de-bord"
              className="inline-flex min-h-11 items-center justify-center rounded-lg border border-bordure bg-white px-5 font-semibold text-royal"
            >
              Revenir au tableau de bord
            </a>
          </div>
        </main>
      </body>
    </html>
  );
}
