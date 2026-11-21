"use client";

import { Alerte, Bouton } from "@emine/ui";
import { LoaderCircle } from "@emine/ui/icones";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useEffect, useState } from "react";

/** Intervalle de relecture du statut et durée d'attente affichée (critère : confirmation en 10 s). */
export const INTERVALLE_MS = 2_000;
export const ATTENTE_MAX_MS = 10_000;

/**
 * Attente de la notification TrésorPay après le retour sur e-mine (US-04.3) : relit le statut toutes
 * les 2 s pendant 10 s au plus (la page serveur affiche le résultat dès qu'il change), puis
 * explique que la confirmation est en attente, sans inviter à payer une seconde fois.
 */
export function AttenteConfirmation({ lienDemande }: { lienDemande: string }) {
  const t = useTranslations("paiement.retour.attente");
  const router = useRouter();
  const [depasse, setDepasse] = useState(false);

  useEffect(() => {
    if (depasse) return;
    const debut = Date.now();
    const minuterie = setInterval(() => {
      if (Date.now() - debut >= ATTENTE_MAX_MS) {
        setDepasse(true);
        clearInterval(minuterie);
        return;
      }
      router.refresh();
    }, INTERVALLE_MS);
    return () => clearInterval(minuterie);
  }, [depasse, router]);

  if (!depasse) {
    return (
      <div role="status" aria-live="polite" className="flex items-center gap-3 text-lg">
        <LoaderCircle aria-hidden="true" className="size-6 animate-spin text-royal" />
        {t("enCours")}
      </div>
    );
  }
  return (
    <div className="flex flex-col gap-4">
      <Alerte nature="info" titre={t("longueTitre")}>
        {t("longueTexte")}
      </Alerte>
      <div className="flex flex-wrap gap-3">
        <Bouton
          type="button"
          onClick={() => {
            setDepasse(false);
            router.refresh();
          }}
        >
          {t("actualiser")}
        </Bouton>
        <Bouton asChild variante="secondaire">
          <Link href={lienDemande}>{t("retourDemande")}</Link>
        </Bouton>
      </div>
    </div>
  );
}
