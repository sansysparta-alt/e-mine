import { ArrowDown, ArrowUp, ArrowUpDown } from "lucide-react";
import type { ComponentType, ReactNode } from "react";
import { cn } from "../cn";

/** Composant de lien fourni par l'application (par exemple `next/link`), `<a>` par défaut. */
export type ComposantLien = ComponentType<{
  href: string;
  className?: string;
  children: ReactNode;
  "aria-current"?: "page" | "step" | "true";
  "aria-label"?: string;
  title?: string;
  onClick?: () => void;
}>;

const LienParDefaut: ComposantLien = ({ children, ...props }) => <a {...props}>{children}</a>;

export type SensTri = "asc" | "desc";

export type ColonneTableau<T> = {
  cle: string;
  entete: string;
  rendu: (ligne: T) => ReactNode;
  /** Colonne triable : l'en-tête devient un lien vers la liste triée. */
  triable?: boolean;
  alignement?: "gauche" | "droite";
  /**
   * Cellule contenant ses propres boutons ou liens d'action : placée au-dessus du lien de ligne,
   * elle garde son comportement (règle des lignes cliquables).
   */
  interactive?: boolean;
};

/**
 * Lien étiré (socle) : le lien couvre toute la ligne (pseudo-élément), la ligne entière est donc
 * cliquable tout en restant un vrai lien : Tab, Entrée, Ctrl+clic et clic du milieu (nouvel
 * onglet), lecteurs d'écran. Le conteneur de la ligne doit être en `relative`.
 */
export const classesLienEtire =
  "after:absolute after:inset-0 after:content-[''] focus-visible:outline-none focus-visible:after:outline-3 focus-visible:after:-outline-offset-2 focus-visible:after:outline-azure";

/** Ligne cliquable : survol visible (fond léger, curseur main). */
export const classesLigneCliquable = "relative cursor-pointer hover:bg-fond";

/** Élément d'action d'une ligne cliquable : au-dessus du lien étiré. */
export const classesAuDessusDuLien = "relative z-10";

export type TableauDonneesProps<T> = {
  /** Légende lue par les lecteurs d'écran (titre du tableau). */
  legende: string;
  colonnes: ColonneTableau<T>[];
  lignes: T[];
  cleLigne: (ligne: T) => string | number;
  tri?: { colonne: string; sens: SensTri };
  /** Adresse de la liste triée par cette colonne (sens suivant calculé par l'appelant). */
  lienTri?: (colonne: string) => string;
  libellesTri?: { croissant: string; decroissant: string; trier: string };
  Lien?: ComposantLien;
  /**
   * Adresse de l'élément d'une ligne : la ligne entière devient cliquable (lien étiré sur la
   * première colonne).
   */
  lienLigne?: (ligne: T) => string;
};

/**
 * Tableau de données du socle. Grand écran : tableau classique, en-têtes triables (`aria-sort`).
 * Mobile : chaque ligne devient une carte, chaque cellule précédée de son intitulé. Un seul
 * tableau dans le DOM (pas de doublon pour les lecteurs d'écran). Avec `lienLigne`, la ligne
 * entière ouvre l'élément (lien étiré).
 */
export function TableauDonnees<T>({
  legende,
  colonnes,
  lignes,
  cleLigne,
  tri,
  lienTri,
  libellesTri = { croissant: "tri croissant", decroissant: "tri décroissant", trier: "Trier par" },
  Lien = LienParDefaut,
  lienLigne,
}: TableauDonneesProps<T>) {
  return (
    <div className="rounded-carte border border-bordure bg-white">
      <table className="w-full border-collapse text-left">
        <caption className="sr-only">{legende}</caption>
        <thead className="hidden border-b border-bordure bg-fond sm:table-header-group">
          <tr>
            {colonnes.map((c) => {
              const actif = tri?.colonne === c.cle;
              const sens = actif ? tri?.sens : undefined;
              const Icone = sens === "asc" ? ArrowUp : sens === "desc" ? ArrowDown : ArrowUpDown;
              return (
                <th
                  key={c.cle}
                  scope="col"
                  aria-sort={
                    actif
                      ? sens === "asc"
                        ? "ascending"
                        : "descending"
                      : c.triable
                        ? "none"
                        : undefined
                  }
                  className={cn(
                    "px-4 py-3 text-sm font-semibold text-texte-doux",
                    c.alignement === "droite" && "text-right",
                  )}
                >
                  {c.triable && lienTri ? (
                    <Lien
                      href={lienTri(c.cle)}
                      className="inline-flex min-h-11 items-center gap-1.5 hover:text-royal"
                      aria-label={`${libellesTri.trier} ${c.entete}${
                        actif
                          ? `, ${sens === "asc" ? libellesTri.croissant : libellesTri.decroissant}`
                          : ""
                      }`}
                    >
                      {c.entete}
                      <Icone aria-hidden="true" className={cn("size-4", !actif && "opacity-50")} />
                    </Lien>
                  ) : (
                    c.entete
                  )}
                </th>
              );
            })}
          </tr>
        </thead>
        <tbody>
          {lignes.map((ligne) => (
            <tr
              key={cleLigne(ligne)}
              className={cn(
                "block border-b border-bordure px-4 py-3 last:border-b-0 sm:table-row sm:p-0",
                lienLigne ? classesLigneCliquable : "sm:hover:bg-fond",
              )}
            >
              {colonnes.map((c, i) => (
                <td
                  key={c.cle}
                  className={cn(
                    "flex gap-3 py-1 sm:table-cell sm:px-4 sm:py-3",
                    c.alignement === "droite" && "sm:text-right",
                    i === 0 && "font-semibold",
                  )}
                >
                  <span
                    aria-hidden="true"
                    className="w-28 shrink-0 text-sm text-texte-doux sm:hidden"
                  >
                    {c.entete}
                  </span>
                  <span className={cn("min-w-0", c.interactive && classesAuDessusDuLien)}>
                    {i === 0 && lienLigne ? (
                      <Lien
                        href={lienLigne(ligne)}
                        className={cn(
                          "text-royal underline-offset-4 hover:underline",
                          classesLienEtire,
                        )}
                      >
                        {c.rendu(ligne)}
                      </Lien>
                    ) : (
                      c.rendu(ligne)
                    )}
                  </span>
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export type OptionFiltre = {
  valeur: string;
  libelle: string;
  href: string;
  actif: boolean;
  /** Nombre d'éléments correspondants, affiché après le libellé. */
  nombre?: number;
};

/** Filtres en pastilles (liens) : la valeur active est annoncée (`aria-current`). */
export function FiltresPastilles({
  libelle,
  options,
  Lien = LienParDefaut,
  fin,
}: {
  libelle: string;
  options: OptionFiltre[];
  Lien?: ComposantLien;
  /** Élément ajouté au bout de la rangée (ex. « Plus de filtres »), aligné sur les pastilles. */
  fin?: ReactNode;
}) {
  return (
    <nav aria-label={libelle}>
      <ul className="flex flex-wrap gap-2">
        {options.map((o) => (
          <li key={o.valeur}>
            <Lien
              href={o.href}
              aria-current={o.actif ? "true" : undefined}
              className={cn(
                "inline-flex min-h-11 items-center rounded-full border px-4 text-sm font-semibold",
                o.actif
                  ? "border-navy bg-navy text-white"
                  : "border-bordure bg-white text-texte hover:border-royal",
              )}
            >
              {o.libelle}
              {o.nombre !== undefined ? (
                <span
                  className={cn(
                    "ml-2 rounded-full px-2 text-xs",
                    o.actif ? "bg-white/20 text-white" : "bg-fond text-texte-doux",
                  )}
                >
                  {o.nombre}
                </span>
              ) : null}
            </Lien>
          </li>
        ))}
        {fin ? <li className="relative">{fin}</li> : null}
      </ul>
    </nav>
  );
}
