import {
  ErreurApi,
  examinerRecevabilite,
  lireDecision,
  lireDemande,
  listerPaiementsDemande,
  listerPieces,
} from "@emine/api-client";
import { Alerte, Bouton, Carte, EnTetePage, formaterDateHeure, formaterGnf } from "@emine/ui";
import { ArrowLeft, CreditCard } from "@emine/ui/icones";
import type { Metadata } from "next";
import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { StatutDemande } from "@/composants/demande/statut-demande";
import { ListePieces } from "@/composants/documents/liste-pieces";
import { prochaineEtape } from "@/composants/demande/prochaine-etape";
import { DecisionRendue } from "@/composants/instruction/decision-rendue";
import { ExamenRecevabilite } from "@/composants/instruction/examen-recevabilite";
import { PriseDecision } from "@/composants/instruction/prise-decision";
import { TableauPaiements } from "@/composants/paiement/tableau-paiements";
import { declarer, rendreDecision } from "@/serveur/actions-instruction";
import { appelPage, chargerMoi, chargerReferentiels, optionsApi } from "@/serveur/donnees";

/** Statuts où aucune décision ne peut encore exister (avant la recevabilité). */
const STATUTS_SANS_DECISION = new Set(["brouillon", "deposee", "complement_requis"]);

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
  const [moi, { documents }, pieces, paiements] = await Promise.all([
    chargerMoi(),
    chargerReferentiels(),
    appelPage(chemin, async () => listerPieces(Number(id), await optionsApi())),
    appelPage(chemin, async () => listerPaiementsDemande(Number(id), await optionsApi())),
  ]);
  const document = documents.find((d) => d.code === demande.typeDocumentCode);
  // US-05.1 : l'agent qui instruit examine la recevabilité d'une demande déposée.
  const examen =
    demande.statut === "deposee" && moi.interne && moi.permissions.includes("INSTRUIRE")
      ? (await appelPage(chemin, async () => examinerRecevabilite(Number(id), await optionsApi())))
          .data
      : null;
  // US-05.4 : décision du signataire (formulaire s'il peut décider, décision rendue pour tous).
  const etatDecision = STATUTS_SANS_DECISION.has(demande.statut)
    ? null
    : (await appelPage(chemin, async () => lireDecision(Number(id), await optionsApi()))).data;
  const decisionRendue = etatDecision?.decisionFavorable != null;
  const aPayer = document?.tarifs.aPayerAuDepotGnf;
  const etapeCalculee = prochaineEtape(
    demande.statut,
    { interne: moi.interne, permissions: moi.permissions },
    { fraisPayes: demande.fraisPayes },
  );
  const etape = etatDecision?.peutDecider
    ? { cle: "signataireDecider", pour: "agent" as const }
    : etapeCalculee;
  const numero = demande.numero ?? "";
  const actionPayer =
    etape?.cle === "demandeurPayerFrais"
      ? "FRAIS"
      : etape?.cle === "demandeurPayerDroits"
        ? "DROITS"
        : null;

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
            <StatutDemande statut={demande.statut} fraisPayes={demande.fraisPayes} />
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
          {demande.motifRejet && !decisionRendue ? (
            <>
              <dt className="font-semibold">{t("motifRejet")}</dt>
              <dd className="whitespace-pre-line">{demande.motifRejet}</dd>
            </>
          ) : null}
        </dl>
      </Carte>
      {etape ? (
        <Carte titre={t(`etape.titre.${etape.pour}`)}>
          <p>
            {t(`etape.${etape.cle}`, {
              montant: aPayer != null ? formaterGnf(aPayer) : "—",
              droits:
                document?.tarifs.droitsGnf != null ? formaterGnf(document.tarifs.droitsGnf) : "—",
              signataire: etatDecision?.signataire ?? document?.signataire ?? "—",
            })}
          </p>
          {examen ? (
            <div className="mt-4 border-t border-bordure pt-4">
              <ExamenRecevabilite
                demandeId={demande.id}
                numero={numero}
                examen={examen}
                piecesRequises={document?.piecesRequises ?? []}
                declarer={declarer}
              />
            </div>
          ) : null}
          {etatDecision?.peutDecider ? (
            <div className="mt-4 border-t border-bordure pt-4">
              <PriseDecision
                demandeId={demande.id}
                numero={numero}
                signataire={etatDecision.signataire}
                droitsGnf={etatDecision.droitsGnf}
                decider={rendreDecision}
              />
            </div>
          ) : null}
          {actionPayer && moi.permissions.includes("PAYER") ? (
            <div className="mt-4">
              <Bouton asChild taille="grande">
                <Link href={`/demandes/${demande.id}/paiement`}>
                  <CreditCard aria-hidden="true" className="size-5" />
                  {t(`etape.actionPayer.${actionPayer}`)}
                </Link>
              </Bouton>
            </div>
          ) : null}
        </Carte>
      ) : null}
      {etatDecision && decisionRendue ? (
        <Carte titre={t("decision")}>
          <DecisionRendue etat={etatDecision} />
        </Carte>
      ) : null}
      {paiements.data.length > 0 ? (
        <Carte titre={t("paiements.titre", { nombre: paiements.data.length })}>
          <TableauPaiements paiements={paiements.data} />
        </Carte>
      ) : null}
      <Carte titre={t("pieces", { nombre: pieces.data.length })}>
        <ListePieces demandeId={demande.id} pieces={pieces.data} />
      </Carte>
    </div>
  );
}
