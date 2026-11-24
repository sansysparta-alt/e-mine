import { ChevronRight } from "lucide-react";
import type { ComposantLien } from "./liste";

export type EtapeFilAriane = { libelle: string; href?: string };

const LienParDefaut: ComposantLien = ({ children, ...props }) => <a {...props}>{children}</a>;

/**
 * Fil d'Ariane, au-dessus du titre de la page (« Demandes › DEM-2026-000009 ») : liste ordonnée,
 * séparateurs décoratifs, dernière étape = page courante (`aria-current`). Cibles de 44 px.
 */
export function FilAriane({
  etapes,
  libelle,
  Lien = LienParDefaut,
}: {
  etapes: EtapeFilAriane[];
  /** Nom de la zone pour les lecteurs d'écran (« Fil d'Ariane »). */
  libelle: string;
  Lien?: ComposantLien;
}) {
  return (
    <nav aria-label={libelle}>
      <ol className="flex flex-wrap items-center gap-x-1 text-sm text-texte-doux">
        {etapes.map((e, i) => {
          const derniere = i === etapes.length - 1;
          return (
            <li key={`${i}-${e.libelle}`} className="flex min-w-0 items-center gap-1">
              {i > 0 ? <ChevronRight aria-hidden="true" className="size-4 shrink-0" /> : null}
              {e.href && !derniere ? (
                <Lien
                  href={e.href}
                  className="inline-flex min-h-11 items-center text-royal underline-offset-4 hover:underline"
                >
                  {e.libelle}
                </Lien>
              ) : (
                <span aria-current={derniere ? "page" : undefined} className="truncate">
                  {e.libelle}
                </span>
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
