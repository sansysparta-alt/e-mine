import {
  ErreurApi,
  examinerRecevabilite,
  lireDecision,
  lireDemande,
  lireDocumentDelivre,
  compterATraiter,
  lireEtatDelivrance,
  listerDemandes,
  listerPaiementsDemande,
  listerPassages,
  listerPieces,
} from "@emine/api-client";
import {
  Alerte,
  Bouton,
  Carte,
  EnTetePage,
  formaterDateHeure,
  formaterGnf,
  joursCalendairesDepuis,
} from "@emine/ui";
import { ArrowLeft, CreditCard, TriangleAlert } from "@emine/ui/icones";
import type { Metadata } from "next";
import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { ATTENTE_LONGUE_JOURS } from "@/composants/demande/date-attente";
import { StatutDemande } from "@/composants/demande/statut-demande";
import { ListePieces } from "@/composants/documents/liste-pieces";
import { prochaineEtape } from "@/composants/demande/prochaine-etape";
import { DocumentDelivre } from "@/composants/delivrance/document-delivre";
import { peutRevoquer } from "@/composants/delivrance/revocation";
import { RevoquerDocument } from "@/composants/delivrance/revoquer-document";
import { SignerDelivrer } from "@/composants/delivrance/signer-delivrer";
import { DecisionRendue } from "@/composants/instruction/decision-rendue";
import { ExamenRecevabilite } from "@/composants/instruction/examen-recevabilite";
import { PriseDecision } from "@/composants/instruction/prise-decision";
import { TableauPaiements } from "@/composants/paiement/tableau-paiements";
import { BarreSerie } from "@/composants/travail/barre-serie";
import { HistoriquePassages } from "@/composants/travail/historique-passages";
import {
  type ContexteSerie,
  groupesDepuis,
  lireActionFaite,
  parametresListe,
  positionDansFile,
} from "@/composants/travail/file-travail";
import {
  declarer,
  rendreDecision,
  revoquer,
  signerEtDelivrer,
} from "@/serveur/actions-instruction";
import { appelPage, chargerMoi, chargerReferentiels, optionsApi } from "@/serveur/donnees";

/** Statuts d'une demande en cours : le temps d'attente à l'étape s'affiche. */
const STATUTS_EN_COURS = new Set([
  "deposee",
  "complement_requis",
  "recevable",
  "en_instruction",
  "avis_rendu",
  "decision",
  "droits_payes",
]);

/** Statuts où le document délivré existe (US-06.4). */
const STATUTS_DELIVRES = new Set(["delivree", "archivee"]);

/** Statuts où aucune décision ne peut encore exister (avant la recevabilité). */
const STATUTS_SANS_DECISION = new Set(["brouillon", "deposee", "complement_requis"]);

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("detail");
  return { title: t("titreOnglet") };
}

type Props = {
  params: Promise<{ id: string }>;
  searchParams: Promise<{
    deposee?: string;
    depuis?: string;
    /** Traitement en série : groupe de la file de travail d'où le dossier est ouvert. */
    file?: string;
    /** Action menée sur le dossier précédent de la série (confirmation). */
    fait?: string;
    resultat?: string;
    document?: string;
  }>;
};

/** Une demande déposée (US-03.4) : numéro, statut, document, pièces et prochaine étape. */
export default async function DetailDemande({ params, searchParams }: Props) {
  const [{ id }, recherche] = await Promise.all([params, searchParams]);
  const { deposee, depuis, file } = recherche;
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
  // Passages dans le traitement en série : visibles de tous les agents (correctifs ergonomie).
  const passages = moi.interne
    ? (await appelPage(chemin, async () => listerPassages(Number(id), await optionsApi()))).data
    : null;
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
  // US-06.3 : le signataire du catalogue signe et délivre quand les droits sont payés.
  const etatDelivrance =
    demande.statut === "droits_payes" && moi.interne && moi.permissions.includes("SIGNER")
      ? (await appelPage(chemin, async () => lireEtatDelivrance(Number(id), await optionsApi())))
          .data
      : null;
  // US-06.4 : le document délivré, consultable par qui voit la demande.
  let documentDelivre = null;
  if (STATUTS_DELIVRES.has(demande.statut)) {
    try {
      documentDelivre = (
        await appelPage(chemin, async () => lireDocumentDelivre(Number(id), await optionsApi()))
      ).data;
    } catch (e) {
      if (!(e instanceof ErreurApi && e.code === "DOC-005")) throw e;
    }
  }
  const aPayer = document?.tarifs.aPayerAuDepotGnf;
  const etapeCalculee = prochaineEtape(
    demande.statut,
    { interne: moi.interne, permissions: moi.permissions },
    { fraisPayes: demande.fraisPayes },
  );
  const etape = etatDecision?.peutDecider
    ? { cle: "signataireDecider", pour: "agent" as const }
    : etatDelivrance?.peutDelivrer
      ? { cle: "signataireDelivrer", pour: "agent" as const }
      : etapeCalculee;
  const numero = demande.numero ?? "";
  const attenteEtape = joursCalendairesDepuis(demande.etapeDepuis);
  // Traitement en série (correctifs J5) : position dans la file, dossier suivant après l'action.
  const groupeSerie = file
    ? groupesDepuis(
        (await appelPage(chemin, async () => compterATraiter(await optionsApi()))).data,
        moi,
      ).find((g) => g.cle === file)
    : undefined;
  let barre = null;
  let serie: ContexteSerie | null = null;
  if (groupeSerie) {
    const enFile = await appelPage(chemin, async () =>
      listerDemandes({ ...parametresListe(groupeSerie), page: 1, taille: 100 }, await optionsApi()),
    );
    const ids = enFile.data.elements.map((d) => d.id);
    const position = positionDansFile(ids, demande.id);
    const suivant = position.dansLaFile
      ? position.suivant
      : (ids.find((x) => x !== demande.id) ?? null);
    serie = { groupe: groupeSerie.cle, suivant };
    barre = (
      <BarreSerie
        demandeId={demande.id}
        groupe={groupeSerie.cle}
        position={position.dansLaFile ? position.position : null}
        total={enFile.data.total}
        precedent={position.dansLaFile ? position.precedent : null}
        suivant={suivant}
      />
    );
  }
  const faite = lireActionFaite({
    numero: recherche.fait,
    resultat: recherche.resultat,
    document: recherche.document,
  });
  const tTravail = await getTranslations("travail");
  const tDates = await getTranslations("dates");
  // Retour vers la file de travail d'où vient l'agent (correctifs J4 et J5).
  const retour = groupeSerie
    ? { href: `/a-traiter?groupe=${groupeSerie.cle}`, cle: "retourTravail" }
    : depuis === "guichet" || depuis === "decisions"
      ? { href: "/a-traiter", cle: "retourTravail" }
      : { href: "/demandes", cle: "retourListe" };
  /** L'agent a une action à mener ici : mise en page dossier + panneau d'action. */
  const actionAgent =
    examen !== null || etatDecision?.peutDecider === true || etatDelivrance?.peutDelivrer === true;
  const actionPayer =
    etape?.cle === "demandeurPayerFrais"
      ? "FRAIS"
      : etape?.cle === "demandeurPayerDroits"
        ? "DROITS"
        : null;

  // Blocs de la fiche, disposés selon le profil (dossier + panneau d'action pour l'agent).
  const blocInfos = (
    <Carte>
      <dl className="grid grid-cols-1 gap-x-6 gap-y-2 sm:grid-cols-[auto_1fr]">
        <dt className="font-semibold">{t("statut")}</dt>
        <dd>
          <StatutDemande demande={demande} interne={moi.interne} />
        </dd>
        <dt className="font-semibold">{t("document")}</dt>
        <dd>{document ? `${document.libelleFr} (${document.code})` : demande.typeDocumentCode}</dd>
        <dt className="font-semibold">{t("deposeLe")}</dt>
        <dd>{demande.deposeLe ? formaterDateHeure(demande.deposeLe) : "—"}</dd>
        {STATUTS_EN_COURS.has(demande.statut) ? (
          <>
            <dt className="font-semibold">{tDates("etapeDepuis")}</dt>
            <dd>
              {formaterDateHeure(demande.etapeDepuis)}
              {" · "}
              <span
                className={
                  attenteEtape >= ATTENTE_LONGUE_JOURS
                    ? "font-semibold text-avertissement"
                    : "text-texte-doux"
                }
              >
                {tDates("attente", { jours: attenteEtape })}
              </span>
            </dd>
          </>
        ) : null}
        {document && STATUTS_EN_COURS.has(demande.statut) ? (
          <>
            <dt className="font-semibold">{t("delai")}</dt>
            <dd>
              {t("delaiValeur", { jours: document.delaiJours })}
              {demande.enRetard ? (
                <span className="ml-2 inline-flex items-center gap-1 font-semibold text-danger">
                  <TriangleAlert aria-hidden="true" className="size-4" />
                  {t("delaiDepasse")}
                </span>
              ) : null}
            </dd>
          </>
        ) : null}
        <dt className="font-semibold">{t("demandeur")}</dt>
        <dd>
          {demande.demandeur.prenom} {demande.demandeur.nom}
          {demande.demandeur.organisation ? ` · ${demande.demandeur.organisation}` : ""}
          {moi.interne && demande.demandeur.email ? (
            <>
              {" · "}
              <a
                href={`mailto:${demande.demandeur.email}`}
                className="text-royal underline underline-offset-4"
              >
                {demande.demandeur.email}
              </a>
            </>
          ) : null}
        </dd>
        {demande.motifRejet && !decisionRendue ? (
          <>
            <dt className="font-semibold">{t("motifRejet")}</dt>
            <dd className="whitespace-pre-line">{demande.motifRejet}</dd>
          </>
        ) : null}
      </dl>
    </Carte>
  );
  const blocPassages = passages ? <HistoriquePassages historique={passages} /> : null;
  const blocPieces = (
    <Carte titre={t("pieces", { nombre: pieces.data.length })}>
      <ListePieces demandeId={demande.id} pieces={pieces.data} />
    </Carte>
  );
  const blocDocument = documentDelivre ? (
    <Carte titre={t("documentDelivre")}>
      <DocumentDelivre
        demandeId={demande.id}
        document={documentDelivre}
        actions={
          documentDelivre.valide && peutRevoquer(moi) ? (
            <RevoquerDocument
              demandeId={demande.id}
              numero={documentDelivre.numero}
              revoquer={revoquer}
            />
          ) : undefined
        }
      />
    </Carte>
  ) : null;
  const blocDecision =
    etatDecision && decisionRendue ? (
      <Carte titre={t("decision")}>
        <DecisionRendue etat={etatDecision} />
      </Carte>
    ) : null;
  const blocPaiements =
    paiements.data.length > 0 ? (
      <Carte titre={t("paiements.titre", { nombre: paiements.data.length })}>
        <TableauPaiements paiements={paiements.data} />
      </Carte>
    ) : null;
  const blocEtape = etape ? (
    <Carte titre={t(`etape.titre.${etape.pour}`)}>
      <p>
        {t(`etape.${etape.cle}`, {
          montant: aPayer != null ? formaterGnf(aPayer) : "—",
          droits: document?.tarifs.droitsGnf != null ? formaterGnf(document.tarifs.droitsGnf) : "—",
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
            serie={serie}
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
            serie={serie}
          />
        </div>
      ) : null}
      {etatDelivrance?.peutDelivrer ? (
        <div className="mt-4 border-t border-bordure pt-4">
          <SignerDelivrer
            demandeId={demande.id}
            numero={numero}
            document={document?.libelleFr ?? demande.typeDocumentCode}
            signataire={etatDelivrance.signataire}
            signerEtDelivrer={signerEtDelivrer}
            serie={serie}
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
  ) : null;

  return (
    <div className="flex flex-col gap-6">
      {faite ? (
        <Alerte
          nature="succes"
          titre={tTravail(`fait.${faite.resultat}`, {
            numero: faite.numero,
            document: faite.document ?? "",
          })}
        >
          {tTravail("suivantOuvert", { numero })}
        </Alerte>
      ) : null}
      {barre}
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
            href={retour.href}
            className="inline-flex min-h-11 items-center gap-2 text-royal underline-offset-4 hover:underline"
          >
            <ArrowLeft aria-hidden="true" className="size-4" />
            {t(retour.cle)}
          </Link>
        }
      />
      {actionAgent ? (
        <div className="grid grid-cols-1 items-start gap-6 lg:grid-cols-[minmax(0,1fr)_26rem]">
          <div className="flex flex-col gap-6">
            {blocInfos}
            {blocPassages}
            {blocDocument}
            {blocPieces}
            {blocDecision}
            {blocPaiements}
          </div>
          <aside
            aria-label={t("panneauAction")}
            className="flex flex-col gap-6 lg:sticky lg:top-20"
          >
            {blocEtape}
          </aside>
        </div>
      ) : (
        <>
          {blocInfos}
          {blocPassages}
          {blocDocument}
          {blocEtape}
          {blocDecision}
          {blocPaiements}
          {blocPieces}
        </>
      )}
    </div>
  );
}
