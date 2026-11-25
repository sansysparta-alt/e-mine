import { type DetailWorkflowDto, ErreurApi, lireWorkflow } from "@emine/api-client";
import { Alerte, Bouton, Carte, EnTetePage, formaterDate } from "@emine/ui";
import { FilePen } from "@emine/ui/icones";
import type { Metadata } from "next";
import { libelleDocument, libelleWorkflow } from "@/composants/referentiels";
import Link from "next/link";
import { notFound } from "next/navigation";
import { getLocale, getTranslations } from "next-intl/server";
import { FilArianeApp } from "@/composants/fil-ariane-app";
import { EtapesWorkflow } from "@/composants/workflow/etapes-workflow";
import { appelPage, chargerMoi, optionsApi } from "@/serveur/donnees";

type Props = {
  params: Promise<{ code: string }>;
  searchParams: Promise<{ version?: string }>;
};

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { code } = await params;
  const t = await getTranslations("workflows");
  return { title: `${code} · ${t("titre")}` };
}

/**
 * Un workflow (US-02.5) : étapes en vigueur, documents de la famille, règle de paiement, délais
 * (déduits des fiches approuvées) et historique des versions.
 */
export default async function DetailWorkflow({ params, searchParams }: Props) {
  const { code } = await params;
  const { version } = await searchParams;
  if (!/^[A-Z_]{2,30}$/.test(code)) notFound();
  const moi = await chargerMoi();
  if (
    !moi.interne ||
    (!moi.permissions.includes("PARAMETRER") && !moi.permissions.includes("VALIDER_CATALOGUE"))
  ) {
    notFound();
  }
  const t = await getTranslations("workflows");
  const tReferentiels = await getTranslations("referentiels");

  let d: DetailWorkflowDto;
  try {
    d = (await appelPage(`/workflows/${code}`, async () => lireWorkflow(code, await optionsApi())))
      .data;
  } catch (e) {
    if (e instanceof ErreurApi && e.code === "SYS-002") notFound();
    throw e;
  }
  const w = d.workflow;
  const langue = await getLocale();
  const v = w.enVigueur;
  const delai =
    w.delaiMinJours === w.delaiMaxJours
      ? t("jours", { n: w.delaiMinJours })
      : t("fourchette", { min: w.delaiMinJours, max: w.delaiMaxJours });

  return (
    <div className="flex flex-col gap-6">
      <EnTetePage
        retour={
          <FilArianeApp
            rubrique="workflows"
            courante={libelleWorkflow(w.code, v.libelle, tReferentiels)}
          />
        }
        titre={libelleWorkflow(w.code, v.libelle, tReferentiels)}
        description={t("etapesNombre", { n: v.etapes.length })}
        actions={
          d.peutModifier ? (
            <Bouton asChild>
              <Link href={`/workflows/${w.code}/modifier`}>
                <FilePen aria-hidden="true" className="size-5" />
                {t("modifier")}
              </Link>
            </Bouton>
          ) : undefined
        }
      />

      {version ? (
        <Alerte nature="succes" titre={t("enregistre.titre")}>
          {t("enregistre.texte", { n: Number(version) })}
        </Alerte>
      ) : null}

      <Carte titre={t("enVigueur", { n: v.version })}>
        <div className="flex flex-col gap-5">
          <EtapesWorkflow etapes={v.etapes} />
          <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div className="sm:col-span-2">
              <dt className="font-semibold text-navy">{t("documentsFamille")}</dt>
              <dd>
                <ul className="mt-1 flex flex-col gap-1">
                  {w.documents.map((c) => (
                    <li key={c.code}>
                      <Link
                        href={`/catalogue/${c.code}`}
                        className="inline-flex min-h-11 items-center text-royal underline-offset-4 hover:underline"
                      >
                        <span className="font-mono font-semibold">{c.code}</span>
                        &nbsp;· {libelleDocument(c, langue)}
                      </Link>
                      {c.actif ? null : (
                        <span className="text-texte-doux"> ({t("desactive")})</span>
                      )}
                    </li>
                  ))}
                </ul>
              </dd>
            </div>
            <div>
              <dt className="font-semibold text-navy">{t("colonnes.paiements")}</dt>
              <dd>{v.reglePaiement}</dd>
            </div>
            <div>
              <dt className="font-semibold text-navy">{t("colonnes.delai")}</dt>
              <dd>
                {delai}
                <span className="block text-sm text-texte-doux">{t("delaiAide")}</span>
              </dd>
            </div>
          </dl>
          <p className="text-sm text-texte-doux">{t("affichage")}</p>
        </div>
      </Carte>

      <Carte titre={t("historique")}>
        <ol className="flex flex-col">
          {d.versions.map((x) => (
            <li
              key={x.version}
              className="flex flex-col gap-2 border-b border-bordure py-4 first:pt-0 last:border-0 last:pb-0"
            >
              <p className="font-semibold text-navy">
                {t("version", { n: x.version })}
                {x.version === v.version ? ` · ${t("actuelle")}` : ""}
              </p>
              <p className="text-sm text-texte-doux">
                {x.modifieePar
                  ? t("par", { date: formaterDate(x.modifieeLe), nom: x.modifieePar })
                  : t("initiale")}
              </p>
              <p>{x.motif}</p>
              {x.version !== v.version ? (
                <details>
                  <summary className="inline-flex min-h-11 cursor-pointer items-center text-royal">
                    {t("voirEtapes", { n: x.etapes.length })}
                  </summary>
                  <EtapesWorkflow etapes={x.etapes} />
                </details>
              ) : null}
            </li>
          ))}
        </ol>
      </Carte>
    </div>
  );
}
