"use client";

import { Bouton } from "@emine/ui";
import { CheckCheck } from "@emine/ui/icones";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useTransition } from "react";
import { marquerToutesLues } from "@/serveur/actions-notification";

/** « Tout marquer comme lu » : action serveur (BFF), puis la liste et la cloche se mettent à jour. */
export function ToutMarquerLu() {
  const t = useTranslations("notifications");
  const router = useRouter();
  const [enCours, demarrer] = useTransition();
  return (
    <Bouton
      variante="secondaire"
      disabled={enCours}
      onClick={() =>
        demarrer(async () => {
          await marquerToutesLues();
          router.refresh();
        })
      }
    >
      <CheckCheck aria-hidden="true" className="size-5" />
      {t("toutMarquerLu")}
    </Bouton>
  );
}
