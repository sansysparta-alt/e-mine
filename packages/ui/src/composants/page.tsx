import { CircleAlert, Inbox, type LucideIcon } from "lucide-react";
import type { ReactNode } from "react";
import { cn } from "../cn";

/* ---------------------------------------------------------------------------------------------
 * Composants de page du socle : en-tête, chiffres clés, badges de statut, états vide, chargement
 * et erreur. Tout nouvel écran les utilise (règle de CLAUDE.md).
 * ------------------------------------------------------------------------------------------- */

export type EnTetePageProps = {
  titre: string;
  description?: ReactNode;
  /** Boutons d'action principaux, à droite sur grand écran, dessous sur mobile. */
  actions?: ReactNode;
  /** Fil d'Ariane ou lien de retour, au-dessus du titre. */
  retour?: ReactNode;
};

/** En-tête d'une page : titre de niveau 1 (un seul par page), description, actions. */
export function EnTetePage({ titre, description, actions, retour }: EnTetePageProps) {
  return (
    <div className="flex flex-col gap-3">
      {retour ? <div>{retour}</div> : null}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <h1 className="text-2xl sm:text-3xl">{titre}</h1>
          {description ? <p className="mt-1 max-w-3xl text-texte-doux">{description}</p> : null}
        </div>
        {actions ? <div className="flex shrink-0 flex-wrap gap-3">{actions}</div> : null}
      </div>
    </div>
  );
}

export type CarteChiffreProps = {
  libelle: string;
  valeur: string | number;
  /** Période du chiffre (« Ce mois-ci », « À ce jour », « Aujourd'hui ») : toujours affichée. */
  periode?: string;
  precision?: string;
  icone?: LucideIcon;
  /** Rend la carte cliquable (lien vers la liste filtrée correspondante). */
  href?: string;
};

/**
 * Chiffre clé : libellé, période, valeur en grand, précision ; sobre (pas de bandeau coloré, une
 * bordure fine neutre qui ne change qu'au survol d'une carte cliquable).
 */
export function CarteChiffre({
  libelle,
  valeur,
  periode,
  precision,
  icone: Icone,
  href,
}: CarteChiffreProps) {
  const contenu = (
    <>
      <div className="flex items-start justify-between gap-3">
        <p className="flex flex-col">
          <span className="text-sm font-semibold text-texte-doux">{libelle}</span>
          {periode ? <span className="text-xs text-texte-doux">{periode}</span> : null}
        </p>
        {Icone ? <Icone aria-hidden="true" className="size-5 shrink-0 text-royal" /> : null}
      </div>
      <p className="mt-2 font-titre text-3xl font-bold text-navy">{valeur}</p>
      {precision ? <p className="mt-1 text-sm text-texte-doux">{precision}</p> : null}
    </>
  );
  const classes = "block h-full rounded-carte border border-bordure bg-white p-4 sm:p-5";
  return href ? (
    <a href={href} className={cn(classes, "hover:border-royal focus-visible:border-royal")}>
      {contenu}
    </a>
  ) : (
    <div className={classes}>{contenu}</div>
  );
}

/**
 * Grille responsive de chiffres clés (1 colonne sur mobile, 2 sur tablette, 3 ou 4 sur grand
 * écran) : choisir le nombre de colonnes qui remplit des rangées complètes.
 */
export function GrilleChiffres({
  children,
  colonnes = 4,
}: {
  children: ReactNode;
  colonnes?: 3 | 4;
}) {
  return (
    <div
      className={cn(
        "grid grid-cols-1 gap-4 sm:grid-cols-2",
        colonnes === 3 ? "lg:grid-cols-3" : "lg:grid-cols-4",
      )}
    >
      {children}
    </div>
  );
}

export type EtatVideProps = {
  titre: string;
  description?: string;
  action?: ReactNode;
  icone?: LucideIcon;
};

/** Liste ou page sans contenu : explique pourquoi et propose l'action suivante. */
export function EtatVide({ titre, description, action, icone: Icone = Inbox }: EtatVideProps) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-carte border border-dashed border-bordure bg-white px-6 py-10 text-center">
      <Icone aria-hidden="true" className="size-8 text-texte-doux" />
      <p className="font-semibold text-navy">{titre}</p>
      {description ? <p className="max-w-md text-texte-doux">{description}</p> : null}
      {action ? <div className="mt-2">{action}</div> : null}
    </div>
  );
}

/** Chargement : lignes fantômes, annoncé aux lecteurs d'écran. */
export function EtatChargement({
  libelle,
  lignes = 3,
  forme = "liste",
}: {
  libelle: string;
  lignes?: number;
  /** « page » : squelette d'un écran entier (titre, cartes chiffrées, liste) ; « liste » : lignes seules. */
  forme?: "liste" | "page";
}) {
  const bloc = "animate-pulse rounded-lg bg-white border border-bordure";
  return (
    <div role="status" aria-live="polite" className="flex flex-col gap-3">
      <span className="sr-only">{libelle}</span>
      {forme === "page" ? (
        <div aria-hidden="true" className="mb-3 flex flex-col gap-3">
          <div className="h-4 w-40 animate-pulse rounded bg-bordure/60" />
          <div className="h-8 w-2/3 max-w-sm animate-pulse rounded bg-bordure/60" />
          <div className="h-4 w-full max-w-md animate-pulse rounded bg-bordure/40" />
          <div className="mt-3 grid gap-4 sm:grid-cols-3">
            {[0, 1, 2].map((i) => (
              <div key={i} className={`h-24 ${bloc}`} />
            ))}
          </div>
        </div>
      ) : null}
      {Array.from({ length: lignes }, (_, i) => (
        <div
          key={i}
          aria-hidden="true"
          className="h-14 animate-pulse rounded-lg border border-bordure bg-white"
        />
      ))}
    </div>
  );
}

export type EtatErreurProps = { titre: string; description?: string; action?: ReactNode };

/** Erreur de chargement d'une page ou d'une liste, avec l'action pour réessayer. */
export function EtatErreur({ titre, description, action }: EtatErreurProps) {
  return (
    <div
      role="alert"
      className="flex flex-col items-center gap-3 rounded-carte border border-danger/25 bg-danger-fond px-6 py-10 text-center"
    >
      <CircleAlert aria-hidden="true" className="size-8 text-danger" />
      <p className="font-semibold text-navy">{titre}</p>
      {description ? <p className="max-w-md text-texte">{description}</p> : null}
      {action ? <div className="mt-2">{action}</div> : null}
    </div>
  );
}
