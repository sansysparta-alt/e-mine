import { compterNotificationsNonLues, ErreurApi } from "@emine/api-client";
import { Alerte } from "@emine/ui";
import { redirect, unstable_rethrow } from "next/navigation";
import { getTranslations } from "next-intl/server";
import type { ReactNode } from "react";
import { NavigationApplication } from "@/navigation/navigation-application";
import { chargerMoi, optionsApi } from "@/serveur/donnees";
import { profilAffiche } from "@/serveur/profil";

/**
 * Espace connecté : tous les écrans de l'application passent par ce gabarit (navigation selon le
 * profil, barre du haut, session surveillée). Règle de CLAUDE.md : tout nouvel écran y est placé.
 */
export default async function DispositionEspace({ children }: { children: ReactNode }) {
  let moi;
  try {
    moi = await chargerMoi();
  } catch (e) {
    unstable_rethrow(e);
    if (e instanceof ErreurApi && e.status === 401) {
      redirect("/api/auth/login?retour=/tableau-de-bord");
    }
    const t = await getTranslations("tableauDeBord.erreurs");
    const motif =
      e instanceof ErreurApi && e.code === "AUTH-003" ? "compteNonActive" : "indisponible";
    if (motif === "indisponible") console.error("Espace connecté : API injoignable", e);
    return (
      <main id="contenu" className="mx-auto max-w-3xl px-4 py-10 sm:px-6">
        <Alerte nature="erreur" titre={t(`${motif}.titre`)}>
          {t(`${motif}.conseil`)}
        </Alerte>
      </main>
    );
  }
  // Pastille de la cloche au premier affichage ; sans réponse de l'API, la cloche reste sans pastille.
  // Les agents ne reçoivent pas de notification (US-07.2) : pas d'appel inutile.
  const nonLues = moi.interne
    ? 0
    : await compterNotificationsNonLues(await optionsApi())
        .then((r) => r.data.nombre)
        .catch(() => 0);
  return (
    <NavigationApplication
      nonLues={nonLues}
      utilisateur={{ nomComplet: `${moi.prenom} ${moi.nom}`, profil: await profilAffiche(moi) }}
      profil={{ permissions: moi.permissions, interne: moi.interne }}
    >
      {children}
    </NavigationApplication>
  );
}
