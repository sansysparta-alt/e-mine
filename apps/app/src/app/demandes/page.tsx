import { listerDemandes } from "@emine/api-client";
import { Bouton, classesChamp } from "@emine/ui";
import type { Metadata } from "next";
import Link from "next/link";
import { getTranslations } from "next-intl/server";
import { ListeDemandes } from "@/composants/demande/liste-demandes";
import { appelPage, chargerMoi, chargerReferentiels, optionsApi } from "@/serveur/donnees";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("mesDemandes");
  return { title: t("titre") };
}

/** Statuts proposés dans le filtre (cycle de vie de la base). */
const STATUTS = [
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

/** « Mes demandes » (US-03.5) : liste filtrable par statut ; la RLS limite aux siennes. */
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

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-3xl sm:text-4xl">{t("titre")}</h1>
        {moi.permissions.includes("DEPOSER") ? (
          <Bouton asChild>
            <Link href="/demandes/nouvelle">{t("nouvelle")}</Link>
          </Bouton>
        ) : null}
      </div>
      <form method="get" className="flex flex-wrap items-end gap-3">
        <div className="flex flex-col gap-1">
          <label htmlFor="filtre-statut" className="font-semibold">
            {t("filtreStatut")}
          </label>
          <select
            id="filtre-statut"
            name="statut"
            defaultValue={filtre ?? ""}
            className={`${classesChamp} w-auto`}
          >
            <option value="">{t("tous")}</option>
            {STATUTS.map((s) => (
              <option key={s} value={s}>
                {tStatut(s)}
              </option>
            ))}
          </select>
        </div>
        <Bouton type="submit" variante="secondaire">
          {t("filtrer")}
        </Bouton>
      </form>
      <p role="status" className="text-texte-doux">
        {t("nombre", { nombre: demandes.data.length })}
      </p>
      {demandes.data.length === 0 ? (
        <p>{filtre ? t("aucuneFiltre") : t("aucune")}</p>
      ) : (
        <ListeDemandes demandes={demandes.data} libelles={libelles} />
      )}
    </div>
  );
}
