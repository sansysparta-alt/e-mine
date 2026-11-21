import { ErreurApi, lireMoi, type MoiDto } from "@emine/api-client";
import { Alerte } from "@emine/ui";
import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { EnTete } from "@/composants/en-tete";
import { VueTableauDeBord } from "@/composants/vue-tableau-de-bord";
import { enTetesApi, lireSession } from "@/serveur/api";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("tableauDeBord");
  return { title: t("titreOnglet") };
}

type Resultat = { moi: MoiDto } | { erreur: "compteNonActive" | "indisponible" };

async function chargerUtilisateur(): Promise<Resultat> {
  const session = await lireSession();
  try {
    const reponse = await lireMoi({ headers: await enTetesApi(session) });
    return { moi: reponse.data };
  } catch (e) {
    if (e instanceof ErreurApi && e.status === 401)
      redirect("/api/auth/login?retour=/tableau-de-bord");
    if (e instanceof ErreurApi && e.code === "AUTH-003") return { erreur: "compteNonActive" };
    console.error("Tableau de bord : API injoignable", e);
    return { erreur: "indisponible" };
  }
}

export default async function TableauDeBord() {
  const resultat = await chargerUtilisateur();
  const t = await getTranslations("tableauDeBord.erreurs");
  return (
    <>
      {"moi" in resultat ? (
        <EnTete
          nomComplet={`${resultat.moi.prenom} ${resultat.moi.nom}`}
          profil={resultat.moi.profil.libelle}
        />
      ) : null}
      <main id="contenu" className="mx-auto max-w-5xl px-4 py-8 sm:px-6 sm:py-10">
        {"moi" in resultat ? (
          <VueTableauDeBord
            prenom={resultat.moi.prenom}
            nom={resultat.moi.nom}
            profilLibelle={resultat.moi.profil.libelle}
            interne={resultat.moi.interne}
            peutDeposer={resultat.moi.permissions.includes("DEPOSER")}
          />
        ) : (
          <Alerte nature="erreur" titre={t(`${resultat.erreur}.titre`)}>
            {t(`${resultat.erreur}.conseil`)}
          </Alerte>
        )}
      </main>
    </>
  );
}
