import {
  type DetailWorkflowDto,
  ErreurApi,
  lireWorkflow,
  type VersionWorkflowDto,
} from "@emine/api-client";
import { Alerte, Bouton, Carte, EnTetePage, formaterDate } from "@emine/ui";
import { FilePen } from "@emine/ui/icones";
import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { getLocale, getTranslations } from "next-intl/server";
import { DecisionVersion } from "@/composants/catalogue/decision-version";
import { FilArianeApp } from "@/composants/fil-ariane-app";
import {
  CarteProposition,
  HistoriqueVersions,
  type VersionGouvernee,
} from "@/composants/gouvernance/gouvernance";
import { libelleDocument, libelleWorkflow } from "@/composants/referentiels";
import { ContenuVersionWorkflow } from "@/composants/workflow/contenu-version";
import { EtapesWorkflow } from "@/composants/workflow/etapes-workflow";
import { deciderWorkflow } from "@/serveur/actions-workflow";
import { appelPage, chargerMoi, optionsApi } from "@/serveur/donnees";

type Props = {
  params: Promise<{ code: string }>;
  searchParams: Promise<{ proposee?: string; resultat?: string }>;
};

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { code } = await params;
  const t = await getTranslations("workflows");
  return { title: `${code} · ${t("titre")}` };
}

const RESULTATS = ["approuver", "refuser", "retirer"] as const;

/** Version de workflow au format commun de la gouvernance. */
function gouvernee(v: VersionWorkflowDto): VersionGouvernee & VersionWorkflowDto {
  return { ...v, proposeePar: v.modifieePar, proposeeLe: v.modifieeLe };
}

/**
 * Un workflow (US-02.5) : étapes en vigueur, documents de la famille, règle de paiement, délais
 * (déduits des fiches approuvées). Gouvernance (US-02.8) : proposition à valider (le Ministre
 * décide, l'administrateur peut retirer), version programmée, historique des versions.
 */
export default async function DetailWorkflow({ params, searchParams }: Props) {
  const { code } = await params;
  const { proposee, resultat } = await searchParams;
  if (!/^[A-Z_]{2,30}$/.test(code)) notFound();
  const moi = await chargerMoi();
  if (
    !moi.interne ||
    (!moi.permissions.includes("PARAMETRER") && !moi.permissions.includes("VALIDER_CATALOGUE"))
  ) {
    notFound();
  }
  const t = await getTranslations("workflows");
  const tGouvernance = await getTranslations("gouvernance");
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
  const nom = libelleWorkflow(w.code, v.libelle, tReferentiels);
  const admin = moi.permissions.includes("PARAMETRER");
  const delai =
    w.delaiMinJours === w.delaiMaxJours
      ? t("jours", { n: w.delaiMinJours })
      : t("fourchette", { min: w.delaiMinJours, max: w.delaiMaxJours });
  const resultatConnu = RESULTATS.find((r) => r === resultat);

  return (
    <div className="flex flex-col gap-6">
      <EnTetePage
        retour={<FilArianeApp rubrique="workflows" courante={nom} />}
        titre={nom}
        description={t("etapesNombre", { n: v.etapes.length })}
        actions={
          d.peutProposer ? (
            <Bouton asChild>
              <Link href={`/workflows/${w.code}/modifier`}>
                <FilePen aria-hidden="true" className="size-5" />
                {t("modifier")}
              </Link>
            </Bouton>
          ) : undefined
        }
      />

      {proposee && /^\d{1,4}$/.test(proposee) ? (
        <Alerte nature="succes" titre={tGouvernance("resultat.titre")}>
          {tGouvernance("resultat.proposee", { n: Number(proposee) })}
        </Alerte>
      ) : null}
      {resultatConnu ? (
        <Alerte nature="succes" titre={tGouvernance("resultat.titre")}>
          {tGouvernance(`resultat.${resultatConnu}`)}
        </Alerte>
      ) : null}

      {w.aValider ? (
        <CarteProposition
          v={gouvernee(w.aValider)}
          decideur={d.peutDecider}
          decision={
            d.peutDecider || admin ? (
              <DecisionVersion
                code={w.code}
                numero={w.aValider.version}
                action={d.peutDecider ? "DECIDER" : "ATTENDRE"}
                changeSignataire={false}
                desactive={false}
                decider={deciderWorkflow}
                adresseApres={`/workflows/${w.code}`}
                texteApprobation={tGouvernance("approbation")}
              />
            ) : undefined
          }
        >
          <ContenuVersionWorkflow version={w.aValider} />
        </CarteProposition>
      ) : null}

      {w.programmee ? (
        <Alerte
          nature="info"
          titre={tGouvernance("programmee.titre", {
            n: w.programmee.version,
            date: formaterDate(w.programmee.dateEffet),
          })}
        >
          {tGouvernance("programmee.texte", { actuelle: v.version })}
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
              <dd>{(langue === "en" && v.reglePaiementEn) || v.reglePaiement}</dd>
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

      <HistoriqueVersions
        versions={d.versions.map(gouvernee)}
        decideur={d.peutDecider}
        contenu={(x) => <ContenuVersionWorkflow version={x} />}
      />
    </div>
  );
}
