"use client";

import { type ReactNode, useEffect, useId, useRef } from "react";
import { cn } from "../cn";

export type DialogueProps = {
  ouvert: boolean;
  titre: string;
  children: ReactNode;
  /** Boutons d'action, alignés en bas à droite. */
  actions?: ReactNode;
  /** Appelé à la fermeture par Échap ; sans gestionnaire, Échap est sans effet. */
  surFermeture?: () => void;
  className?: string;
};

/**
 * Boîte de dialogue modale accessible, sur l'élément natif `<dialog>` : focus gardé dans la boîte,
 * fond inerte, titre annoncé (`aria-labelledby`), fermeture par Échap si autorisée.
 */
export function Dialogue({
  ouvert,
  titre,
  children,
  actions,
  surFermeture,
  className,
}: DialogueProps) {
  const ref = useRef<HTMLDialogElement>(null);
  const idTitre = useId();

  useEffect(() => {
    const d = ref.current;
    if (!d) return;
    if (ouvert && !d.open) {
      if (typeof d.showModal === "function") d.showModal();
      else d.setAttribute("open", "");
    } else if (!ouvert && d.open) {
      if (typeof d.close === "function") d.close();
      else d.removeAttribute("open");
    }
  }, [ouvert]);

  return (
    <dialog
      ref={ref}
      aria-labelledby={idTitre}
      onCancel={(e) => {
        e.preventDefault();
        surFermeture?.();
      }}
      className={cn(
        "m-auto w-[min(32rem,calc(100vw-2rem))] rounded-carte border border-bordure bg-white p-0 text-texte shadow-xl backdrop:bg-navy/40",
        className,
      )}
    >
      {ouvert ? (
        <div className="flex flex-col gap-4 p-6">
          <h2 id={idTitre} className="text-xl">
            {titre}
          </h2>
          <div>{children}</div>
          {actions ? <div className="flex flex-wrap justify-end gap-3">{actions}</div> : null}
        </div>
      ) : null}
    </dialog>
  );
}
