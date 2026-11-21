import { cva, type VariantProps } from "class-variance-authority";
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
  precision?: string;
  icone?: LucideIcon;
  /** Rend la carte cliquable (lien vers la liste filtrée correspondante). */
  href?: string;
};

/** Chiffre clé : valeur en grand, libellé, précision ; sobre (pas de bandeau coloré). */
export function CarteChiffre({
  libelle,
  valeur,
  precision,
  icone: Icone,
  href,
}: CarteChiffreProps) {
  const contenu = (
    <>
      <div className="flex items-start justify-between gap-3">
        <p className="text-sm font-semibold text-texte-doux">{libelle}</p>
        {Icone ? <Icone aria-hidden="true" className="size-5 shrink-0 text-royal" /> : null}
      </div>
      <p className="mt-2 font-titre text-3xl font-bold text-navy">{valeur}</p>
      {precision ? <p className="mt-1 text-sm text-texte-doux">{precision}</p> : null}
    </>
  );
  const classes = "block rounded-carte border border-bordure bg-white p-4 sm:p-5";
  return href ? (
    <a href={href} className={cn(classes, "hover:border-royal")}>
      {contenu}
    </a>
  ) : (
    <div className={classes}>{contenu}</div>
  );
}

/** Grille responsive de chiffres clés (1 colonne sur mobile, jusqu'à 4). */
export function GrilleChiffres({ children }: { children: ReactNode }) {
  return <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">{children}</div>;
}

const badge = cva(
  "inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-sm font-semibold whitespace-nowrap",
  {
    variants: {
      ton: {
        neutre: "border-bordure bg-fond text-texte-doux",
        info: "border-azure/30 bg-ice text-royal",
        succes: "border-succes/30 bg-succes-fond text-succes",
        avertissement: "border-avertissement/30 bg-avertissement-fond text-avertissement",
        danger: "border-danger/30 bg-danger-fond text-danger",
      },
    },
    defaultVariants: { ton: "neutre" },
  },
);

export type TonBadge = NonNullable<VariantProps<typeof badge>["ton"]>;

/** Badge de statut : pastille et libellé (le sens ne repose pas sur la seule couleur). */
export function BadgeStatut({ ton, children }: { ton?: TonBadge; children: ReactNode }) {
  return (
    <span className={badge({ ton })}>
      <span aria-hidden="true" className="size-1.5 rounded-full bg-current" />
      {children}
    </span>
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
export function EtatChargement({ libelle, lignes = 3 }: { libelle: string; lignes?: number }) {
  return (
    <div role="status" aria-live="polite" className="flex flex-col gap-3">
      <span className="sr-only">{libelle}</span>
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
