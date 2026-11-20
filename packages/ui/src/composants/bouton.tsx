import { Slot } from "@radix-ui/react-slot";
import { cva, type VariantProps } from "class-variance-authority";
import type { ComponentProps } from "react";
import { cn } from "../cn";

const styles = cva(
  "inline-flex min-h-11 items-center justify-center gap-2 rounded-lg px-5 font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-60",
  {
    variants: {
      variante: {
        primaire: "bg-royal text-white hover:bg-navy",
        secondaire: "border-2 border-royal bg-white text-royal hover:bg-ice",
        discret: "text-royal underline-offset-4 hover:underline",
      },
      taille: {
        normale: "text-base",
        grande: "min-h-12 px-6 text-lg",
      },
    },
    defaultVariants: { variante: "primaire", taille: "normale" },
  },
);

export type BoutonProps = ComponentProps<"button"> &
  VariantProps<typeof styles> & {
    /** Rend l'enfant (un lien par exemple) avec le style du bouton. */
    asChild?: boolean;
  };

/** Bouton accessible : zone cliquable de 44 px minimum, focus visible, état désactivé lisible. */
export function Bouton({ className, variante, taille, asChild = false, ...props }: BoutonProps) {
  const Composant = asChild ? Slot : "button";
  return <Composant className={cn(styles({ variante, taille }), className)} {...props} />;
}
