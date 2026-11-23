import { ErreurApi, type FicheDto, lireFicheDocument } from "@emine/api-client";
import {
  Alerte,
  BadgeStatut,
  Bouton,
  Carte,
  EnTetePage,
  formaterDate,
  joursCalendairesDepuis,
} from "@emine/ui";
import { ArrowLeft, FilePen } from "@emine/ui/icones";
import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { DecisionVersion } from "@/composants/catalogue/decision-version";
import {
  ContenuFiche,
  LigneDecision,
  TableauChangements,
  TarifsAVenir,
} from "@/composants/catalogue/fiche-version";
import { type ActionFiche, presentationVersion } from "@/composants/catalogue/presentation";
import { deciderFiche } from "@/serveur/actions-catalogue";
import { appelPage, chargerMoi, optionsApi } from "@/serveur/donnees";

type Props = {
  params: Promise<{ code: string }>;
  searchParams: Promise<{ resultat?: string; proposee?: string }>;
};

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { code } = await params;
  const t = await getTranslations("catalogue");
  return { title: `${code} · ${t("titre")}` };
}

/** Seuil à partir duquel l'attente d'une décision est signalée (règle commune des dates). */
const ATTENTE_SIGNALEE = 5;

/**
 * Fiche d'un document du catalogue (US-02.2, US-02.4) : la modification en attente (ancienne et
 * nouvelle valeur) avec l'action attendue du profil connecté, la fiche en vigueur et l'historique
 * des versions.
 */
export default async function FicheDocument({ params, searchParams }: Props) {
  const { code } = await params;
  const { resultat, proposee } = await searchParams;
  if (!/^[A-Z]{3}$/.test(code)) notFound();
  const moi = await chargerMoi();
  if (
    !moi.interne ||
    (!moi.permissions.includes("PARAMETRER") && !moi.permissions.includes("VALIDER_CATALOGUE"))
  ) {
    notFound();
  }
  const t = await getTranslations("catalogue");

  let fiche: FicheDto;
  try {
    fiche = (
      await appelPage(`/catalogue/${code}`, async () => lireFicheDocument(code, await optionsApi()))
    ).data;
  } catch (e) {
    if (e instanceof ErreurApi && e.code === "SYS-002") notFound();
    throw e;
  }
  const v = fiche.enVigueur;
  const proposition = fiche.proposition ?? null;
  const action = fiche.action as ActionFiche;
  const attente = proposition ? joursCalendairesDepuis(proposition.proposeeLe) : 0;
  const message =
    resultat && ["approuver", "refuser", "retirer"].includes(resultat)
      ? t(`fiche.resultat.${resultat}`)
      : proposee
        ? t("fiche.resultat.proposee", { numero: Number(proposee) })
        : null;

  return (
    <div className="flex flex-col gap-6">
      <EnTetePage
        retour={
          <Link
            href="/catalogue"
            className="inline-flex min-h-11 items-center gap-2 text-royal underline-offset-4 hover:underline"
          >
            <ArrowLeft aria-hidden="true" className="size-4" />
            {t("fiche.retour")}
          </Link>
        }
        titre={`${fiche.code} · ${v.libelleFr}`}
        description={fiche.famille}
        actions={
          fiche.peutProposer ? (
            <Bouton asChild>
              <Link href={`/catalogue/${fiche.code}/modifier`}>
                <FilePen aria-hidden="true" className="size-5" />
                {t("fiche.proposer")}
              </Link>
            </Bouton>
          ) : undefined
        }
      />

      {message ? (
        <Alerte nature="succes" titre={t("fiche.resultat.titre")}>
          {message}
        </Alerte>
      ) : null}

      {proposition ? (
        <Carte titre={t("fiche.proposition.titre", { numero: proposition.numero })}>
          <div className="flex flex-col gap-4">
            <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap sm:items-center sm:gap-4">
              {(() => {
                const p = presentationVersion("a_valider", action);
                return <BadgeStatut ton={p.ton}>{t(`statuts.${p.cle}`)}</BadgeStatut>;
              })()}
              <span>
                {t("fiche.proposition.proposee", {
                  date: formaterDate(proposition.proposeeLe),
                  nom: proposition.proposeePar ?? "",
                })}
              </span>
              <span
                className={
                  attente >= ATTENTE_SIGNALEE
                    ? "font-semibold text-avertissement"
                    : "text-texte-doux"
                }
              >
                {t("fiche.proposition.attente", { n: attente })}
              </span>
            </div>
            <p className="text-sm text-texte-doux">
              {action === "DECIDER"
                ? t("fiche.proposition.aide.ministre")
                : t("fiche.proposition.aide.admin")}
            </p>
            <div>
              <h3 className="font-semibold text-navy">{t("fiche.motif")}</h3>
              <p className="whitespace-pre-line">{proposition.motif}</p>
            </div>
            <TableauChangements
              changements={proposition.changements}
              legende={t("fiche.proposition.legende")}
            />
            {proposition.tarifsDateEffet ? (
              <p className="font-semibold">
                {t("fiche.proposition.tarifsDateEffet", {
                  date: formaterDate(proposition.tarifsDateEffet),
                })}
              </p>
            ) : null}
            {action !== "AUCUNE" ? (
              <DecisionVersion
                code={fiche.code}
                numero={proposition.numero}
                action={action}
                changeSignataire={proposition.changements.some((c) => c.champ === "signataire")}
                desactive={proposition.changements.some(
                  (c) => c.champ === "actif" && c.apres === "false",
                )}
                decider={deciderFiche}
              />
            ) : null}
          </div>
        </Carte>
      ) : null}

      {!fiche.actif ? (
        <Alerte nature="info" titre={t("fiche.inactif.titre")}>
          {t("fiche.inactif.texte")}
        </Alerte>
      ) : null}

      <Carte titre={t("fiche.enVigueur.titre", { numero: v.numero })}>
        <p className="mb-2 text-sm text-texte-doux">
          <LigneDecision version={v} />
        </p>
        <ContenuFiche version={v} />
        {fiche.tarifsAVenir.length ? (
          <div className="mt-4">
            <Alerte nature="info" titre={t("fiche.tarifsAVenir.titre")}>
              <TarifsAVenir tarifs={fiche.tarifsAVenir} />
            </Alerte>
          </div>
        ) : null}
      </Carte>

      <Carte titre={t("fiche.historique.titre")}>
        <ol className="flex flex-col">
          {fiche.historique.map((h) => {
            const p = presentationVersion(h.statut, h.statut === "a_valider" ? action : "AUCUNE");
            return (
              <li
                key={h.numero}
                className="flex flex-col gap-2 border-b border-bordure py-4 first:pt-0 last:border-0 last:pb-0"
              >
                <div className="flex flex-wrap items-center gap-3">
                  <span className="font-semibold text-navy">{t("version", { n: h.numero })}</span>
                  <BadgeStatut ton={p.ton}>{t(`statuts.${p.cle}`)}</BadgeStatut>
                </div>
                <p className="text-sm text-texte-doux">
                  {h.proposeePar
                    ? t("fiche.proposition.proposee", {
                        date: formaterDate(h.proposeeLe),
                        nom: h.proposeePar,
                      })
                    : null}
                  {h.proposeePar && h.statut !== "a_valider" ? " · " : null}
                  {h.statut !== "a_valider" ? <LigneDecision version={h} /> : null}
                </p>
                <p>{h.motif}</p>
                {h.motifRefus ? (
                  <p>
                    <span className="font-semibold">{t("fiche.motifRefus")} : </span>
                    {h.motifRefus}
                  </p>
                ) : null}
                {h.changements.length ? (
                  <details className="group">
                    <summary className="inline-flex min-h-11 cursor-pointer items-center text-royal">
                      {t("fiche.historique.voir", { n: h.changements.length })}
                    </summary>
                    <TableauChangements
                      changements={h.changements}
                      legende={t("fiche.historique.legende", { numero: h.numero })}
                    />
                  </details>
                ) : null}
              </li>
            );
          })}
        </ol>
      </Carte>
    </div>
  );
}
