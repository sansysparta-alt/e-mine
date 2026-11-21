import { Alerte } from "@emine/ui";
import type { Metadata } from "next";
import Link from "next/link";
import { getTranslations } from "next-intl/server";
import { FormulaireDemande } from "@/composants/demande/formulaire-demande";
import { enregistrerBrouillon } from "@/serveur/actions-demande";
import { chargerMoi, chargerReferentiels } from "@/serveur/donnees";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("demande");
  return { title: t("titreNouvelle") };
}

type Props = { searchParams: Promise<{ type?: string }> };

/** Nouvelle demande (US-03.1) : réservée aux profils qui ont le droit « déposer » (US-01.6). */
export default async function NouvelleDemande({ searchParams }: Props) {
  const t = await getTranslations("demande");
  const [moi, referentiels, { type }] = await Promise.all([
    chargerMoi(),
    chargerReferentiels(),
    searchParams,
  ]);
  if (!moi.permissions.includes("DEPOSER")) {
    return (
      <Alerte nature="avertissement" titre={t("nonAutorise.titre")}>
        <Link href="/tableau-de-bord" className="underline">
          {t("nonAutorise.retour")}
        </Link>
      </Alerte>
    );
  }
  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-3xl sm:text-4xl">{t("titreNouvelle")}</h1>
      <FormulaireDemande
        demande={null}
        demandeur={{
          prenom: moi.prenom,
          nom: moi.nom,
          email: moi.email,
          organisation: moi.organisation,
        }}
        {...referentiels}
        typeInitial={type && /^[A-Z]{3}$/.test(type) ? type : undefined}
        enregistrer={enregistrerBrouillon}
      />
    </div>
  );
}
