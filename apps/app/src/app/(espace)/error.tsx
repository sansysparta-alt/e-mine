"use client";

import { Bouton, EtatErreur } from "@emine/ui";
import { useTranslations } from "next-intl";
import { useEffect } from "react";

/** Erreur inattendue d'un écran (état standard du socle), avec l'action pour réessayer. */
export default function Erreur({ error, reset }: { error: Error; reset: () => void }) {
  const t = useTranslations("etats.erreur");
  useEffect(() => {
    console.error(error);
  }, [error]);
  return (
    <EtatErreur
      titre={t("titre")}
      description={t("description")}
      action={
        <Bouton type="button" onClick={reset}>
          {t("reessayer")}
        </Bouton>
      }
    />
  );
}
