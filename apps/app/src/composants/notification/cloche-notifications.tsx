"use client";

import { cn } from "@emine/ui";
import { Bell } from "@emine/ui/icones";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useTranslations } from "next-intl";
import { useEffect, useState } from "react";

/** Fréquence de mise à jour du nombre de non lues pendant que la page est ouverte. */
const INTERVALLE_MS = 60_000;

/** Nombre affiché sur la pastille : au-delà de 9, « 9+ » (le nombre exact est lu en entier). */
export function pastille(nombre: number): string {
  return nombre > 9 ? "9+" : String(nombre);
}

/**
 * Cloche de la barre du haut (US-07.2) : mène au centre de notifications et signale les non lues.
 * Le nombre est relu à chaque changement de page, au retour sur l'onglet et chaque minute ; une
 * erreur réseau laisse simplement la cloche sans pastille. Cible de 44 px, libellé complet pour
 * les lecteurs d'écran (« Notifications, 3 non lues »).
 */
export function ClocheNotifications({ initial = 0 }: { initial?: number }) {
  const t = useTranslations("notifications.cloche");
  const chemin = usePathname();
  const [nonLues, setNonLues] = useState(initial);

  useEffect(() => {
    let actif = true;
    const lire = async () => {
      try {
        const reponse = await fetch("/api/notifications/non-lues", { cache: "no-store" });
        if (!reponse.ok) return;
        const corps = (await reponse.json()) as { nombre?: number };
        if (actif && typeof corps.nombre === "number") setNonLues(corps.nombre);
      } catch {
        // Hors ligne ou API indisponible : la cloche reste utilisable, sans pastille à jour.
      }
    };
    void lire();
    const minuterie = window.setInterval(() => void lire(), INTERVALLE_MS);
    const auRetour = () => {
      if (document.visibilityState === "visible") void lire();
    };
    document.addEventListener("visibilitychange", auRetour);
    return () => {
      actif = false;
      window.clearInterval(minuterie);
      document.removeEventListener("visibilitychange", auRetour);
    };
  }, [chemin]);

  const actif = chemin === "/notifications";
  return (
    <Link
      href="/notifications"
      aria-current={actif ? "page" : undefined}
      aria-label={nonLues > 0 ? t("avecNonLues", { nombre: nonLues }) : t("sansNonLues")}
      className={cn(
        "relative flex size-11 shrink-0 items-center justify-center rounded-lg text-navy hover:bg-ice",
        actif && "bg-ice",
      )}
    >
      <Bell aria-hidden="true" className="size-6" />
      {nonLues > 0 ? (
        <span
          aria-hidden="true"
          className="absolute top-1 right-1 flex min-w-5 items-center justify-center rounded-full bg-royal px-1 text-xs leading-5 font-bold text-white"
        >
          {pastille(nonLues)}
        </span>
      ) : null}
    </Link>
  );
}
