import { listerDemandes } from "@emine/api-client";
import { Bouton, EnTetePage, EtatVide, FiltresPastilles } from "@emine/ui";
import type { Metadata } from "next";
import Link from "next/link";
import { getTranslations } from "next-intl/server";
import { TableauDemandes } from "@/composants/demande/tableau-demandes";
import { appelPage, chargerMoi, chargerReferentiels, optionsApi } from "@/serveur/donnees";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("mesDemandes");
  return { title: t("titre") };
}

/** Statuts proposés dans le filtre (cycle de vie de la base). */
export const STATUTS = [
  "brouillon",
  "deposee",
  "complement_requis",
  "recevable",
  "en_instruction",
  "avis_rendu",
  "decision",
  "droits_payes",
  "delivree",
  "archivee",
  "rejetee",
] as const;

type Props = { searchParams: Promise<{ statut?: string }> };

/** « Mes demandes » (US-03.5) sur le gabarit : filtres en pastilles, tableau, état vide. */
export default async function MesDemandes({ searchParams }: Props) {
  const { statut } = await searchParams;
  const filtre = STATUTS.find((s) => s === statut);
  const t = await getTranslations("mesDemandes");
  const tStatut = await getTranslations("statuts");
  const [moi, { documents }, demandes] = await Promise.all([
    chargerMoi(),
    chargerReferentiels(),
    appelPage("/demandes", async () =>
      listerDemandes(filtre ? { statut: filtre } : undefined, await optionsApi()),
    ),
  ]);
  const libelles = Object.fromEntries(documents.map((d) => [d.code, d.libelleFr]));
  const titre = moi.interne ? t("titreInterne") : t("titre");
  const nouvelle = moi.permissions.includes("DEPOSER") ? (
    <Bouton asChild>
      <Link href="/demandes/nouvelle">{t("nouvelle")}</Link>
    </Bouton>
  ) : null;

  return (
    <div className="flex flex-col gap-6">
      <EnTetePage titre={titre} description={t("description")} actions={nouvelle} />
      <FiltresPastilles
        libelle={t("filtreStatut")}
        options={[
          { valeur: "", libelle: t("tous"), href: "/demandes", actif: !filtre },
          ...STATUTS.map((s) => ({
            valeur: s,
            libelle: tStatut(s),
            href: `/demandes?statut=${s}`,
            actif: filtre === s,
          })),
        ]}
      />
      <p role="status" className="text-texte-doux">
        {t("nombre", { nombre: demandes.data.length })}
      </p>
      {demandes.data.length === 0 ? (
        <EtatVide
          titre={filtre ? t("aucuneFiltre") : t("aucune")}
          action={filtre ? undefined : nouvelle}
        />
      ) : (
        <TableauDemandes demandes={demandes.data} libelles={libelles} legende={titre} />
      )}
    </div>
  );
}
