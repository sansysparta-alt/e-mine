"use client";

import { Check, Copy } from "lucide-react";
import { useEffect, useState, useSyncExternalStore } from "react";
import { cn } from "../cn";

const aucunAbonnement = () => () => undefined;

/**
 * Bouton « Copier » du socle (lot 5) : recopie une valeur exacte (IBAN, référence de paiement)
 * dans le presse-papiers, pour éviter les erreurs de saisie. Le résultat est annoncé aux lecteurs
 * d'écran ; cible de 44 px. Sans presse-papiers (navigateur ancien, page non sécurisée), le bouton
 * ne s'affiche pas : la valeur reste sélectionnable à la main.
 */
export function BoutonCopier({
  valeur,
  libelle,
  libelleCopie,
  className,
}: {
  valeur: string;
  /** « Copier l'IBAN » : nom accessible du bouton. */
  libelle: string;
  /** « IBAN copié » : annoncé une fois la copie faite. */
  libelleCopie: string;
  className?: string;
}) {
  // Presse-papiers disponible côté navigateur seulement (rendu serveur : pas de bouton).
  const disponible = useSyncExternalStore(
    aucunAbonnement,
    () => navigator.clipboard !== undefined,
    () => false,
  );
  const [copie, setCopie] = useState(false);

  useEffect(() => {
    if (!copie) return;
    const minuterie = setTimeout(() => setCopie(false), 3000);
    return () => clearTimeout(minuterie);
  }, [copie]);

  if (!disponible) return null;

  return (
    <button
      type="button"
      onClick={() => {
        void navigator.clipboard.writeText(valeur).then(
          () => setCopie(true),
          () => setCopie(false),
        );
      }}
      className={cn(
        "inline-flex min-h-11 min-w-11 items-center justify-center gap-1 rounded-lg px-2 text-sm font-semibold text-royal hover:bg-ice",
        className,
      )}
    >
      {copie ? (
        <Check aria-hidden="true" className="size-4 text-succes" />
      ) : (
        <Copy aria-hidden="true" className="size-4" />
      )}
      <span className="sr-only">{libelle}</span>
      <span role="status" className="sr-only">
        {copie ? libelleCopie : ""}
      </span>
    </button>
  );
}
