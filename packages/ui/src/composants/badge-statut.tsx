import {
  CircleAlert,
  CircleCheck,
  CircleDashed,
  CircleX,
  Clock3,
  type LucideIcon,
} from "lucide-react";
import type { ReactNode } from "react";

/**
 * Code couleur des statuts, commun à toute l'application (règle du socle) :
 * - neutre (gris) : brouillon, archivé — rien en cours ;
 * - info (bleu) : en cours de traitement ;
 * - avertissement (orange) : une action est attendue (complément, paiement) ;
 * - succes (vert) : abouti (délivré) ;
 * - danger (rouge) : refusé, rejeté.
 * Chaque ton a aussi son icône : la couleur ne porte jamais seule le sens. Contraste du texte sur
 * le fond ≥ 4,5:1 (WCAG AA), vérifié par test.
 */
export const TONS_STATUT = {
  neutre: { texte: "#3b4a66", fond: "#eef1f6", bordure: "#c9d2e0", icone: CircleDashed },
  info: { texte: "#0b3a8f", fond: "#e1ecfb", bordure: "#a9c5ee", icone: Clock3 },
  avertissement: { texte: "#8a3c00", fond: "#fdebd5", bordure: "#f2b877", icone: CircleAlert },
  succes: { texte: "#05603a", fond: "#dcf3e6", bordure: "#8fd3ad", icone: CircleCheck },
  danger: { texte: "#a11a12", fond: "#fde4e1", bordure: "#f3a59c", icone: CircleX },
} satisfies Record<string, { texte: string; fond: string; bordure: string; icone: LucideIcon }>;

export type TonBadge = keyof typeof TONS_STATUT;

/** Badge de statut : icône du ton et libellé, couleurs du code commun. */
export function BadgeStatut({ ton = "neutre", children }: { ton?: TonBadge; children: ReactNode }) {
  const { texte, fond, bordure, icone: Icone } = TONS_STATUT[ton];
  return (
    <span
      data-ton={ton}
      style={{ color: texte, backgroundColor: fond, borderColor: bordure }}
      className="inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-sm font-semibold whitespace-nowrap"
    >
      <Icone aria-hidden="true" className="size-3.5 shrink-0" />
      {children}
    </span>
  );
}
