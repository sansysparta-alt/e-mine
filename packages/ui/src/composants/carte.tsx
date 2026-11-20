import type { ReactNode } from "react";
import { cn } from "../cn";

export type CarteProps = {
  titre?: string;
  children: ReactNode;
  className?: string;
};

/** Bloc de contenu sur fond blanc, avec un titre de niveau 2 si besoin. */
export function Carte({ titre, children, className }: CarteProps) {
  return (
    <section className={cn("rounded-carte border border-bordure bg-white p-5 sm:p-6", className)}>
      {titre ? <h2 className="mb-3 text-xl">{titre}</h2> : null}
      {children}
    </section>
  );
}
