import { cva, type VariantProps } from "class-variance-authority";
import type { ReactNode } from "react";
import { cn } from "../cn";

const styles = cva("rounded-lg border-l-4 p-4", {
  variants: {
    nature: {
      info: "border-azure bg-ice text-navy",
      succes: "border-succes bg-succes-fond text-succes",
      avertissement: "border-avertissement bg-avertissement-fond text-avertissement",
      erreur: "border-danger bg-danger-fond text-danger",
    },
  },
  defaultVariants: { nature: "info" },
});

export type AlerteProps = VariantProps<typeof styles> & {
  titre: string;
  children?: ReactNode;
  className?: string;
};

/**
 * Message à l'utilisateur. Une erreur est annoncée tout de suite par les lecteurs d'écran
 * (role="alert"), les autres messages poliment (role="status"). Le titre dit ce qui s'est passé,
 * le contenu dit quoi faire.
 */
export function Alerte({ nature, titre, children, className }: AlerteProps) {
  return (
    <div
      role={nature === "erreur" ? "alert" : "status"}
      className={cn(styles({ nature }), className)}
    >
      <p className="font-semibold">{titre}</p>
      {children ? <div className="mt-1 text-texte">{children}</div> : null}
    </div>
  );
}
