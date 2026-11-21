import { ErreurApi, lireDemande, listerPieces } from "@emine/api-client";
import {
  Alerte,
  BadgeStatut,
  Bouton,
  Carte,
  EnTetePage,
  formaterDateHeure,
  formaterGnf,
} from "@emine/ui";
import { ArrowLeft } from "@emine/ui/icones";
import type { Metadata } from "next";
import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { tonStatut } from "@/composants/demande/statut";
import { appelPage, chargerReferentiels, optionsApi } from "@/serveur/donnees";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("detail");
  return { title: t("titreOnglet") };
}

type Props = {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ deposee?: string }>;
};

/** Une demande déposée (US-03.4) : numéro, statut, document, pièces et prochaine étape. */
export default async function DetailDemande({ params, searchParams }: Props) {
  const [{ id }, { deposee }] = await Promise.all([params, searchParams]);
  if (!/^\d{1,18}$/.test(id)) notFound();
  const chemin = `/demandes/${id}`;
  let demande;
  try {
    demande = (await appelPage(chemin, async () => lireDemande(Number(id), await optionsApi())))
      .data;
  } catch (e) {
    if (e instanceof ErreurApi && e.code === "DEM-002") notFound();
    throw e;
  }
  if (demande.statut === "brouillon") redirect(`/demandes/${id}/modifier`);
  const t = await getTranslations("detail");
  const tStatut = await getTranslations("statuts");
  const [{ documents }, pieces] = await Promise.all([
    chargerReferentiels(),
    appelPage(chemin, async () => listerPieces(Number(id), await optionsApi())),
  ]);
  const document = documents.find((d) => d.code === demande.typeDocumentCode);
  const aPayer = document?.tarifs.aPayerAuDepotGnf;
  const numero = demande.numero ?? "";

  return (
    <div className="flex flex-col gap-6">
      {deposee === "1" ? (
        <Alerte nature="succes" titre={t("confirmation", { numero })}>
          {t("confirmationSuite")}
        </Alerte>
      ) : null}
      <EnTetePage
        titre={t("titre", { numero })}
        description={document ? `${document.libelleFr} (${document.code})` : undefined}
        retour={
          <Link
            href="/demandes"
            className="inline-flex min-h-11 items-center gap-2 text-royal underline-offset-4 hover:underline"
          >
            <ArrowLeft aria-hidden="true" className="size-4" />
            {t("retourListe")}
          </Link>
        }
      />
      <Carte>
        <dl className="grid grid-cols-1 gap-x-6 gap-y-2 sm:grid-cols-[auto_1fr]">
          <dt className="font-semibold">{t("statut")}</dt>
          <dd>
            <BadgeStatut ton={tonStatut(demande.statut)}>
              {tStatut.has(demande.statut) ? tStatut(demande.statut) : demande.statut}
            </BadgeStatut>
          </dd>
          <dt className="font-semibold">{t("document")}</dt>
          <dd>
            {document ? `${document.libelleFr} (${document.code})` : demande.typeDocumentCode}
          </dd>
          <dt className="font-semibold">{t("deposeLe")}</dt>
          <dd>{demande.deposeLe ? formaterDateHeure(demande.deposeLe) : "—"}</dd>
          <dt className="font-semibold">{t("demandeur")}</dt>
          <dd>
            {demande.demandeur.prenom} {demande.demandeur.nom}
            {demande.demandeur.organisation ? ` · ${demande.demandeur.organisation}` : ""}
          </dd>
        </dl>
      </Carte>
      <Carte titre={t("pieces", { nombre: pieces.data.length })}>
        <ul className="list-disc pl-6">
          {pieces.data.map((p) => (
            <li key={p.id}>
              {p.typePiece} :{" "}
              <a
                href={`/api/demandes/${id}/pieces/${p.id}/contenu`}
                className="inline-flex min-h-11 items-center break-all text-royal underline"
              >
                {p.nomFichier}
              </a>
            </li>
          ))}
        </ul>
      </Carte>
      {demande.statut === "deposee" && aPayer != null ? (
        <Carte titre={t("suite.titre")}>
          <p>{t("suite.paiement", { montant: formaterGnf(aPayer) })}</p>
        </Carte>
      ) : null}
      <div>
        <Bouton asChild variante="secondaire">
          <Link href="/tableau-de-bord">{t("retour")}</Link>
        </Bouton>
      </div>
    </div>
  );
}
