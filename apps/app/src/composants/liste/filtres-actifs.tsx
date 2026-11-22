import { X } from "@emine/ui/icones";
import Link from "next/link";

export type FiltreActif = {
  cle: string;
  libelle: string;
  /** Adresse de la liste sans ce filtre. */
  effacer: string;
};

export type FiltresActifsProps = {
  titre: string;
  libelleEffacer: string;
  filtres: FiltreActif[];
};

/**
 * Filtres ouverts depuis une carte du tableau de bord (en retard, traités aujourd'hui, période,
 * document…), affichés en pastilles avec de quoi effacer chacun (correctifs ergonomie).
 */
export function FiltresActifs({ titre, libelleEffacer, filtres }: FiltresActifsProps) {
  if (filtres.length === 0) return null;
  return (
    <div className="flex flex-wrap items-center gap-2">
      <span className="text-sm font-semibold text-texte-doux">{titre}</span>
      <ul className="flex flex-wrap gap-2">
        {filtres.map((f) => (
          <li key={f.cle}>
            <span className="inline-flex min-h-11 items-center gap-1 rounded-full border border-navy bg-navy pr-1 pl-4 text-sm font-semibold text-white">
              {f.libelle}
              <Link
                href={f.effacer}
                className="inline-flex size-9 items-center justify-center rounded-full hover:bg-white/20"
              >
                <X aria-hidden="true" className="size-4" />
                <span className="sr-only">
                  {libelleEffacer} : {f.libelle}
                </span>
              </Link>
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}
