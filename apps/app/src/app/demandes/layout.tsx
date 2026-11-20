import { ErreurApi } from "@emine/api-client";
import { Alerte } from "@emine/ui";
import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import type { ReactNode } from "react";
import { EnTete } from "@/composants/en-tete";
import { chargerMoi } from "@/serveur/donnees";

/** Espace « Demandes » : en-tête de l'utilisateur connecté et zone de contenu. */
export default async function DispositionDemandes({ children }: { children: ReactNode }) {
  const t = await getTranslations("tableauDeBord.erreurs");
  let moi;
  try {
    moi = await chargerMoi();
  } catch (e) {
    if (e instanceof ErreurApi && e.status === 401)
      redirect("/api/auth/login?retour=/tableau-de-bord");
    const motif =
      e instanceof ErreurApi && e.code === "AUTH-003" ? "compteNonActive" : "indisponible";
    return (
      <main id="contenu" className="mx-auto max-w-5xl px-4 py-8 sm:px-6">
        <Alerte nature="erreur" titre={t(`${motif}.titre`)}>
          {t(`${motif}.conseil`)}
        </Alerte>
      </main>
    );
  }
  return (
    <>
      <EnTete nomComplet={`${moi.prenom} ${moi.nom}`} profil={moi.profil.libelle} />
      <main id="contenu" className="mx-auto max-w-5xl px-4 py-8 sm:px-6 sm:py-10">
        {children}
      </main>
    </>
  );
}
