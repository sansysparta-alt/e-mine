import { ErreurApi, lireEntreeJournal } from "@emine/api-client";
import { Carte, EnTetePage, formaterDateHeure } from "@emine/ui";
import { ArrowLeft } from "@emine/ui/icones";
import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { formaterValeur, libelleChamp, trierChangements } from "@/composants/audit/journal";
import { appelPage, chargerMoi, optionsApi } from "@/serveur/donnees";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("audit.entree");
  return { title: t("titreOnglet") };
}

type Props = { params: Promise<{ id: string }> };

/**
 * Une entrée du journal d'audit (US-11.7) : qui, quand, d'où, sur quel objet, et chaque champ
 * avant / après (toutes les valeurs pour une création), en clair ; secrets masqués par l'API.
 */
export default async function EntreeJournal({ params }: Props) {
  const { id } = await params;
  if (!/^\d{1,18}$/.test(id)) notFound();
  const moi = await chargerMoi();
  if (!moi.interne || !moi.permissions.includes("PARAMETRER")) notFound();
  let entree;
  try {
    entree = (
      await appelPage(`/journal-audit/${id}`, async () =>
        lireEntreeJournal(Number(id), await optionsApi()),
      )
    ).data;
  } catch (e) {
    if (e instanceof ErreurApi && e.code === "SYS-002") notFound();
    throw e;
  }
  const t = await getTranslations("audit");
  const tStatut = await getTranslations("statuts");
  const tStatutPaiement = await getTranslations("statutsPaiement");
  const tValeurs = await getTranslations("audit.valeurs");
  const traductions = {
    statuts: tStatut,
    statutsPaiement: tStatutPaiement,
    oui: t("oui"),
    non: t("non"),
    valeurs: tValeurs,
    jours: (n: number) => tValeurs("jours", { n }),
  };
  const champ = (c: string) => libelleChamp(c, t);
  const table = t.has(`tables.${entree.table}`) ? t(`tables.${entree.table}`) : entree.table;
  const modification = entree.operation === "U";

  return (
    <div className="flex flex-col gap-6">
      <EnTetePage
        titre={t("entree.titre", { operation: t(`operations.${entree.operation}`), table })}
        description={formaterDateHeure(entree.date)}
        retour={
          <Link
            href="/journal-audit"
            className="inline-flex min-h-11 items-center gap-2 text-royal underline-offset-4 hover:underline"
          >
            <ArrowLeft aria-hidden="true" className="size-4" />
            {t("entree.retour")}
          </Link>
        }
      />
      <Carte>
        <dl className="grid grid-cols-1 gap-x-6 gap-y-2 sm:grid-cols-[auto_1fr]">
          <dt className="font-semibold">{t("colonnes.auteur")}</dt>
          <dd>
            {entree.auteur
              ? `${entree.auteur}${entree.auteurProfil ? ` · ${entree.auteurProfil}` : ""}`
              : t("systemeDetail")}
          </dd>
          <dt className="font-semibold">{t("colonnes.objet")}</dt>
          <dd className="break-all">
            {entree.objetLibelle ?? "—"}
            {entree.objetId ? (
              <span className="text-texte-doux">
                {" "}
                ({t("entree.identifiant", { id: entree.objetId })})
              </span>
            ) : null}
            {entree.demandeId != null ? (
              <>
                {" · "}
                <Link
                  href={`/demandes/${entree.demandeId}`}
                  className="text-royal underline underline-offset-4"
                >
                  {t("ouvrirDemande")}
                </Link>
              </>
            ) : null}
          </dd>
          <dt className="font-semibold">{t("entree.adresseIp")}</dt>
          <dd>{entree.adresseIp ?? "—"}</dd>
          <dt className="font-semibold">{t("entree.numero")}</dt>
          <dd>{entree.id}</dd>
        </dl>
      </Carte>
      <Carte titre={modification ? t("entree.champsModifies") : t("entree.valeurs")}>
        {entree.changements.length === 0 ? (
          <p className="text-texte-doux">{t("entree.aucunChamp")}</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-left">
              <caption className="sr-only">
                {modification ? t("entree.champsModifies") : t("entree.valeurs")}
              </caption>
              <thead>
                <tr className="border-b border-bordure">
                  <th scope="col" className="py-2 pr-4">
                    {t("entree.champ")}
                  </th>
                  {modification ? (
                    <th scope="col" className="py-2 pr-4">
                      {t("entree.avant")}
                    </th>
                  ) : null}
                  <th scope="col" className="py-2">
                    {modification ? t("entree.apres") : t("entree.valeur")}
                  </th>
                </tr>
              </thead>
              <tbody>
                {trierChangements(entree.changements).map((c) => (
                  <tr key={c.champ} className="border-b border-bordure align-top">
                    <th scope="row" className="py-2 pr-4 font-semibold">
                      {champ(c.champ)}
                    </th>
                    {modification ? (
                      <td className="py-2 pr-4 break-all whitespace-pre-line">
                        {formaterValeur(entree.table, c.champ, c.avant, traductions)}
                      </td>
                    ) : null}
                    <td className="py-2 break-all whitespace-pre-line">
                      {formaterValeur(
                        entree.table,
                        c.champ,
                        modification ? c.apres : (c.apres ?? c.avant),
                        traductions,
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Carte>
    </div>
  );
}
