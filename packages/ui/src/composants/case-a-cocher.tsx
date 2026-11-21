import { type ComponentProps, type ReactNode, useId } from "react";
import { cn } from "../cn";

export type CaseACocherProps = Omit<ComponentProps<"input">, "type" | "children"> & {
  /** Libellé cliquable (toute la ligne coche la case). */
  children: ReactNode;
  /** Message d'erreur affiché sous la case et relié par `aria-describedby`. */
  erreur?: string;
};

/**
 * Case à cocher du socle : case de 24 px, ligne entière cliquable d'au moins 44 px de haut
 * (WCAG 2.5.5), focus visible, état invalide annoncé et signalé autrement que par la couleur.
 */
export function CaseACocher({ children, erreur, className, id, ...props }: CaseACocherProps) {
  const auto = useId();
  const identifiant = id ?? auto;
  const idErreur = `${identifiant}-erreur`;
  return (
    <div className={cn("flex flex-col gap-1", className)}>
      <label
        htmlFor={identifiant}
        className={cn(
          "flex min-h-11 cursor-pointer items-start gap-3 rounded-lg border px-3 py-2.5",
          erreur ? "border-danger bg-danger-fond" : "border-bordure bg-white hover:border-royal",
        )}
      >
        <input
          id={identifiant}
          type="checkbox"
          aria-invalid={erreur ? true : undefined}
          aria-describedby={erreur ? idErreur : undefined}
          className="mt-0.5 size-6 shrink-0 cursor-pointer accent-royal"
          {...props}
        />
        <span>{children}</span>
      </label>
      {erreur ? (
        <p id={idErreur} className="font-semibold text-danger">
          {erreur}
        </p>
      ) : null}
    </div>
  );
}
