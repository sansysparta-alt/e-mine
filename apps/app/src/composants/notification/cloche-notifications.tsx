"use client";

import type { NotificationDto } from "@emine/api-client";
import { cn, formaterDateHeure } from "@emine/ui";
import { Bell } from "@emine/ui/icones";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useTranslations } from "next-intl";
import { useEffect, useId, useRef, useState } from "react";
import { adresseOuverture } from "./liste-notifications";

/** Nombre de notifications du panneau. */
const TAILLE_PANNEAU = 5;

/** Fréquence de mise à jour du nombre de non lues pendant que la page est ouverte. */
const INTERVALLE_MS = 60_000;

/** Nombre affiché sur la pastille : au-delà de 9, « 9+ » (le nombre exact est lu en entier). */
export function pastille(nombre: number): string {
  return nombre > 9 ? "9+" : String(nombre);
}

type Panneau =
  { etat: "chargement" } | { etat: "erreur" } | { etat: "pret"; elements: NotificationDto[] };

/**
 * Cloche de la barre du haut (US-07.2), seul accès aux notifications : elle signale les non lues et
 * ouvre un panneau des dernières notifications, avec « Voir toutes les notifications ». Le nombre
 * est relu à chaque changement de page, au retour sur l'onglet et chaque minute ; une erreur réseau
 * laisse la cloche utilisable. Bouton de 44 px qui annonce son état (`aria-expanded`), libellé
 * complet pour les lecteurs d'écran (« Notifications, 3 non lues ») ; Échap ou un clic ailleurs
 * referme le panneau (Échap rend le focus à la cloche).
 */
export function ClocheNotifications({ initial = 0 }: { initial?: number }) {
  const t = useTranslations("notifications");
  const chemin = usePathname();
  const [nonLues, setNonLues] = useState(initial);
  const [ouvert, setOuvert] = useState(false);
  const [panneau, setPanneau] = useState<Panneau>({ etat: "chargement" });
  const conteneur = useRef<HTMLDivElement>(null);
  const bouton = useRef<HTMLButtonElement>(null);
  const idPanneau = useId();

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

  // Panneau : relu à chaque ouverture ; fermé par Échap (focus rendu à la cloche) ou un clic ailleurs.
  useEffect(() => {
    if (!ouvert) return;
    let actif = true;
    fetch("/api/notifications", { cache: "no-store" })
      .then(async (r) => {
        if (!r.ok) throw new Error(String(r.status));
        const corps = (await r.json()) as { elements: NotificationDto[]; nonLues?: number };
        if (!actif) return;
        setPanneau({ etat: "pret", elements: corps.elements.slice(0, TAILLE_PANNEAU) });
        if (typeof corps.nonLues === "number") setNonLues(corps.nonLues);
      })
      .catch(() => {
        if (actif) setPanneau({ etat: "erreur" });
      });
    const surTouche = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        setOuvert(false);
        bouton.current?.focus();
      }
    };
    const surClic = (e: MouseEvent) => {
      if (!conteneur.current?.contains(e.target as Node)) setOuvert(false);
    };
    document.addEventListener("keydown", surTouche);
    document.addEventListener("mousedown", surClic);
    return () => {
      actif = false;
      document.removeEventListener("keydown", surTouche);
      document.removeEventListener("mousedown", surClic);
    };
  }, [ouvert]);

  return (
    <div ref={conteneur} className="relative">
      <button
        ref={bouton}
        type="button"
        aria-expanded={ouvert}
        aria-controls={ouvert ? idPanneau : undefined}
        aria-label={
          nonLues > 0 ? t("cloche.avecNonLues", { nombre: nonLues }) : t("cloche.sansNonLues")
        }
        onClick={() => {
          // Chaque ouverture relit les dernières notifications (squelette pendant la lecture).
          if (!ouvert) setPanneau({ etat: "chargement" });
          setOuvert(!ouvert);
        }}
        className={cn(
          "relative flex size-11 shrink-0 items-center justify-center rounded-lg text-navy hover:bg-ice",
          (ouvert || chemin === "/notifications") && "bg-ice",
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
      </button>
      {ouvert ? (
        <section
          id={idPanneau}
          aria-label={t("titre")}
          className="absolute right-0 z-40 mt-2 w-[min(24rem,calc(100vw-2rem))] overflow-hidden rounded-carte border border-bordure bg-white shadow-lg"
        >
          <h2 className="border-b border-bordure px-4 py-3 font-semibold text-navy">
            {t("titre")}
          </h2>
          <div aria-live="polite" aria-busy={panneau.etat === "chargement"}>
            {panneau.etat === "chargement" ? (
              <ul aria-hidden="true" className="divide-y divide-bordure">
                {[0, 1, 2].map((i) => (
                  <li key={i} className="flex flex-col gap-2 px-4 py-3">
                    <span className="h-4 w-3/4 animate-pulse rounded bg-ice" />
                    <span className="h-3 w-1/3 animate-pulse rounded bg-ice" />
                  </li>
                ))}
              </ul>
            ) : panneau.etat === "erreur" ? (
              <p className="px-4 py-4 text-sm text-texte-doux">{t("cloche.erreur")}</p>
            ) : panneau.elements.length === 0 ? (
              <p className="px-4 py-4 text-sm text-texte-doux">{t("vide.titre")}</p>
            ) : (
              <ul className="max-h-96 divide-y divide-bordure overflow-y-auto">
                {panneau.elements.map((n) => (
                  <li key={n.id}>
                    <Link
                      href={adresseOuverture(n)}
                      aria-label={n.lue ? undefined : `${t("nonLue")} : ${n.titre}`}
                      onClick={() => setOuvert(false)}
                      className="flex min-h-11 gap-3 px-4 py-3 hover:bg-fond focus-visible:bg-fond"
                    >
                      <span
                        aria-hidden="true"
                        className={cn(
                          "mt-1.5 size-2.5 shrink-0 rounded-full",
                          n.lue ? "bg-transparent" : "bg-royal",
                        )}
                      />
                      <span className="flex min-w-0 flex-col gap-0.5">
                        <span className={cn("text-navy", n.lue ? "font-normal" : "font-semibold")}>
                          {n.titre}
                        </span>
                        <span className="text-sm text-texte-doux">
                          {formaterDateHeure(n.creeLe)}
                        </span>
                      </span>
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </div>
          <Link
            href="/notifications"
            onClick={() => setOuvert(false)}
            className="flex min-h-11 items-center justify-center border-t border-bordure px-4 font-semibold text-royal underline-offset-4 hover:underline"
          >
            {t("cloche.voirToutes")}
          </Link>
        </section>
      ) : null}
    </div>
  );
}
