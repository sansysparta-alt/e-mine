"use client";

import { Bouton, EtatErreur } from "@emine/ui";
import { useTranslations } from "next-intl";
import { useEffect } from "react";

/**
 * Erreur inattendue hors de l'espace connecté (page d'accueil, connexion) : même message que dans
 * l'espace, sur fond clair, avec l'action pour réessayer. Aucun détail technique affiché.
 */
export default function ErreurRacine({ error, reset }: { error: Error; reset: () => void }) {
  const t = useTranslations("etats.erreur");
  useEffect(() => {
    console.error(error);
  }, [error]);
  return (
    <main id="contenu" className="mx-auto flex min-h-dvh max-w-2xl items-center bg-fond px-4">
      <div className="w-full">
        <EtatErreur
          titre={t("titre")}
          description={t("description")}
          action={
            <Bouton type="button" onClick={reset}>
              {t("reessayer")}
            </Bouton>
          }
        />
      </div>
    </main>
  );
}
