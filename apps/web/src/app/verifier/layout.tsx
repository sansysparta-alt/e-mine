import type { ReactNode } from "react";
import { EnTeteOfficiel } from "@/composants/en-tete-officiel";

/** Pages publiques de vérification (document et reçu) : en-tête officiel commun (R6). */
export default function DispositionVerification({ children }: { children: ReactNode }) {
  return (
    <>
      <EnTeteOfficiel />
      {children}
    </>
  );
}
