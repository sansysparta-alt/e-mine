"use client";

import { Bouton, Dialogue } from "@emine/ui";
import { useTranslations } from "next-intl";
import { useCallback, useEffect, useRef, useState } from "react";

/**
 * Délais (en ms). Keycloak ferme la session après 30 min sans renouvellement du jeton ; le jeton
 * est renouvelé au plus toutes les 2 min tant que l'utilisateur agit. La déconnexion côté
 * navigateur intervient donc à 28 min d'inactivité (marge de 2 min), avec un avertissement 2 min
 * avant.
 */
export const DELAIS_SESSION = {
  maintien: 2 * 60_000,
  avertissement: 26 * 60_000,
  expiration: 28 * 60_000,
  verification: 15_000,
};

const EVENEMENTS = ["keydown", "pointerdown", "wheel", "touchstart", "input"] as const;

type Etat = "active" | "avertissement" | "expiree";

export type SurveillanceSessionProps = {
  delais?: typeof DELAIS_SESSION;
  /** Remplaçable dans les tests. */
  naviguer?: (url: string) => void;
};

/**
 * Maintient la session tant que l'utilisateur est actif, l'avertit avant la déconnexion pour
 * inactivité (« Rester connecté »), puis le déconnecte. Les saisies en cours sont conservées dans
 * le navigateur (sessionStorage) par les formulaires et restaurées après reconnexion.
 */
export function SurveillanceSession({
  delais = DELAIS_SESSION,
  naviguer = (url) => window.location.assign(url),
}: SurveillanceSessionProps) {
  const t = useTranslations("session");
  const [etat, setEtat] = useState<Etat>("active");
  // Horodatages initialisés au montage (dans l'effet), pas pendant le rendu.
  const derniereActivite = useRef(0);
  const dernierMaintien = useRef(0);
  const etatCourant = useRef<Etat>("active");

  const changerEtat = useCallback((e: Etat) => {
    etatCourant.current = e;
    setEtat(e);
  }, []);

  const maintenir = useCallback(async () => {
    dernierMaintien.current = Date.now();
    try {
      const r = await fetch("/api/session", { method: "POST" });
      if (r.status === 401) changerEtat("expiree");
    } catch {
      // Réseau momentanément indisponible : nouvelle tentative à la prochaine activité.
    }
  }, [changerEtat]);

  const deconnecter = useCallback(async () => {
    changerEtat("expiree");
    try {
      await fetch("/api/auth/logout", { method: "POST", redirect: "manual" });
    } finally {
      naviguer("/?expiree=1");
    }
  }, [changerEtat, naviguer]);

  useEffect(() => {
    derniereActivite.current = Date.now();
    dernierMaintien.current = Date.now();
    function surActivite() {
      if (etatCourant.current !== "active") return;
      derniereActivite.current = Date.now();
      if (Date.now() - dernierMaintien.current >= delais.maintien) void maintenir();
    }
    for (const e of EVENEMENTS) window.addEventListener(e, surActivite, { passive: true });
    const minuterie = setInterval(() => {
      if (etatCourant.current === "expiree") return;
      const inactif = Date.now() - derniereActivite.current;
      if (inactif >= delais.expiration) void deconnecter();
      else if (inactif >= delais.avertissement && etatCourant.current === "active") {
        changerEtat("avertissement");
      }
    }, delais.verification);
    return () => {
      for (const e of EVENEMENTS) window.removeEventListener(e, surActivite);
      clearInterval(minuterie);
    };
  }, [changerEtat, deconnecter, delais, maintenir]);

  function resterConnecte() {
    derniereActivite.current = Date.now();
    changerEtat("active");
    void maintenir();
  }

  function seReconnecter() {
    const retour = `${window.location.pathname}${window.location.search}`;
    naviguer(`/api/auth/login?retour=${encodeURIComponent(retour)}`);
  }

  return (
    <>
      <Dialogue
        ouvert={etat === "avertissement"}
        titre={t("avertissement.titre")}
        surFermeture={resterConnecte}
        actions={
          <>
            <Bouton type="button" variante="secondaire" onClick={() => void deconnecter()}>
              {t("avertissement.deconnecter")}
            </Bouton>
            <Bouton type="button" onClick={resterConnecte}>
              {t("avertissement.rester")}
            </Bouton>
          </>
        }
      >
        <p>{t("avertissement.texte")}</p>
      </Dialogue>
      <Dialogue
        ouvert={etat === "expiree"}
        titre={t("expiree.titre")}
        actions={
          <Bouton type="button" onClick={seReconnecter}>
            {t("expiree.reconnecter")}
          </Bouton>
        }
      >
        <p>{t("expiree.texte")}</p>
      </Dialogue>
    </>
  );
}
