import { type ComponentProps, type ReactNode, useId } from "react";
import { cn } from "../cn";

export type CaseACocherProps = Omit<ComponentProps<"input">, "type" | "children"> & {
  /** Libellé cliquable (toute la ligne coche la case). */
  children: ReactNode;
  /** Message d'erreur affiché sous la case et relié par `aria-describedby`. */
  erreur?: string;
  /**
   * `ligne` (par défaut) : case encadrée, libellé visible. `cellule` : case seule, sans cadre,
   * centrée dans une cellule de tableau (libellé pour les lecteurs d'écran), cible de 44 px gardée.
   */
  variante?: "ligne" | "cellule";
};

/**
 * Case à cocher du socle : case de 24 px, ligne entière cliquable d'au moins 44 px de haut
 * (WCAG 2.5.5), focus visible, état invalide annoncé et signalé autrement que par la couleur.
 */
export function CaseACocher({
  children,
  erreur,
  className,
  id,
  variante = "ligne",
  ...props
}: CaseACocherProps) {
  const auto = useId();
  const identifiant = id ?? auto;
  const idErreur = `${identifiant}-erreur`;
  return (
    <div className={cn("flex flex-col gap-1", className)}>
      <label
        htmlFor={identifiant}
        className={cn(
          variante === "cellule"
            ? "mx-auto flex min-h-11 min-w-11 cursor-pointer items-center justify-center rounded-lg hover:bg-ice"
            : "flex min-h-11 cursor-pointer items-start gap-3 rounded-lg border px-3 py-2.5",
          variante === "ligne" &&
            (erreur
              ? "border-danger bg-danger-fond"
              : "border-bordure bg-white hover:border-royal"),
        )}
      >
        <input
          id={identifiant}
          type="checkbox"
          aria-invalid={erreur ? true : undefined}
          aria-describedby={erreur ? idErreur : undefined}
          className={cn(
            "size-6 shrink-0 cursor-pointer accent-royal disabled:cursor-not-allowed",
            variante === "ligne" && "mt-0.5",
          )}
          {...props}
        />
        <span className={variante === "cellule" ? "sr-only" : undefined}>{children}</span>
      </label>
      {erreur ? (
        <p id={idErreur} className="font-semibold text-danger">
          {erreur}
        </p>
      ) : null}
    </div>
  );
}
