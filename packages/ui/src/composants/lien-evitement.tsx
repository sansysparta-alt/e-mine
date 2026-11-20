import type { ReactNode } from "react";

/** Lien « Aller au contenu » : invisible jusqu'au premier appui sur Tab (navigation au clavier). */
export function LienEvitement({
  cible = "#contenu",
  children,
}: {
  cible?: string;
  children: ReactNode;
}) {
  return (
    <a
      href={cible}
      className="sr-only rounded-md bg-royal px-4 py-2 text-white focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:z-50"
    >
      {children}
    </a>
  );
}
