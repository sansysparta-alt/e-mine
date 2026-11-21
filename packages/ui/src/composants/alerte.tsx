import { cva, type VariantProps } from "class-variance-authority";
import { CircleAlert, CircleCheck, Info, TriangleAlert } from "lucide-react";
import type { ReactNode } from "react";
import { cn } from "../cn";

/**
 * Style institutionnel sobre : fond très léger, bordure fine de la même teinte, icône discrète.
 * Pas de barre colorée épaisse. Le sens n'est jamais porté par la seule couleur : icône et titre
 * le disent aussi.
 */
const styles = cva("flex gap-3 rounded-lg border p-4 text-texte", {
  variants: {
    nature: {
      info: "border-azure/25 bg-ice",
      succes: "border-succes/25 bg-succes-fond",
      avertissement: "border-avertissement/25 bg-avertissement-fond",
      erreur: "border-danger/25 bg-danger-fond",
    },
  },
  defaultVariants: { nature: "info" },
});

const ICONES = {
  info: { Icone: Info, couleur: "text-azure" },
  succes: { Icone: CircleCheck, couleur: "text-succes" },
  avertissement: { Icone: TriangleAlert, couleur: "text-avertissement" },
  erreur: { Icone: CircleAlert, couleur: "text-danger" },
} as const;

export type AlerteProps = VariantProps<typeof styles> & {
  titre: string;
  children?: ReactNode;
  className?: string;
};

/**
 * Message à l'utilisateur (succès, erreur, avertissement, information). Une erreur est annoncée
 * tout de suite par les lecteurs d'écran (role="alert"), les autres messages poliment
 * (role="status"). Le titre dit ce qui s'est passé, le contenu dit quoi faire.
 */
export function Alerte({ nature, titre, children, className }: AlerteProps) {
  const { Icone, couleur } = ICONES[nature ?? "info"];
  return (
    <div
      role={nature === "erreur" ? "alert" : "status"}
      data-nature={nature ?? "info"}
      className={cn(styles({ nature }), className)}
    >
      <Icone
        aria-hidden="true"
        focusable="false"
        className={cn("mt-0.5 size-5 shrink-0", couleur)}
      />
      <div className="min-w-0">
        <p className="font-semibold">{titre}</p>
        {children ? <div className="mt-1">{children}</div> : null}
      </div>
    </div>
  );
}
