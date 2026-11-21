"use client";

import { Bouton } from "@emine/ui";
import { LogOut } from "@emine/ui/icones";
import { effacerToutesLesSaisies } from "../demande/sauvegarde-locale";

/**
 * Déconnexion volontaire (POST) : efface d'abord les saisies e-mine gardées dans le navigateur,
 * pour qu'un poste partagé ne conserve rien du compte qui part.
 */
export function FormulaireDeconnexion({ libelle }: { libelle: string }) {
  return (
    <form action="/api/auth/logout" method="post" onSubmit={() => effacerToutesLesSaisies()}>
      <Bouton type="submit" variante="secondaire" className="px-3 sm:px-4">
        <LogOut aria-hidden="true" className="size-5" />
        <span className="sr-only sm:not-sr-only">{libelle}</span>
      </Bouton>
    </form>
  );
}
