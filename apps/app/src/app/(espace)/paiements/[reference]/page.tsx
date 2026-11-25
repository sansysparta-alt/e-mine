import { ErreurApi, lireDemande, lirePaiement, type PaiementDto } from "@emine/api-client";
import {
  Alerte,
  BadgeStatut,
  Bouton,
  Carte,
  EnTetePage,
  formaterDateHeure,
  formaterGnf,
} from "@emine/ui";
import { FolderOpen, RotateCcw } from "@emine/ui/icones";
import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { getLocale, getTranslations } from "next-intl/server";
import { FilArianeApp } from "@/composants/fil-ariane-app";
import { LienRecu } from "@/composants/paiement/lien-recu";
import {
  enAttenteDeConfirmation,
  peutReessayer,
  tonStatutPaiement,
} from "@/composants/paiement/statut";
import { libelleDocument } from "@/composants/referentiels";
import { appelPage, chargerMoi, chargerReferentiels, optionsApi } from "@/serveur/donnees";

const REFERENCE = /^PAY-\d{4}-\d{6}$/;

type Props = { params: Promise<{ reference: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const [t, { reference }] = await Promise.all([getTranslations("detailPaiement"), params]);
  return { title: `${t("titre")} · ${reference}` };
}

/**
 * « Détail du paiement », ouvert depuis « Paiements & reçus » (retours ergonomie) : un historique,
 * pas le retour juste après le paiement (dont le message « l'agent peut maintenant examiner »
 * reste réservé à ce moment-là). Statut, montant, moyen, date, reçu et **demande liée** ; le titre,
 * le menu et le fil d'Ariane restent sur « Paiements & reçus ».
 */
export default async function DetailPaiement({ params }: Props) {
  const { reference } = await params;
  if (!REFERENCE.test(reference)) notFound();
  const moi = await chargerMoi();
  if (moi.interne || !moi.permissions.includes("PAYER")) notFound();
  const chemin = `/paiements/${reference}`;
  let paiement: PaiementDto;
  try {
    paiement = (await appelPage(chemin, async () => lirePaiement(reference, await optionsApi())))
      .data;
  } catch (e) {
    if (e instanceof ErreurApi && e.code === "PAY-005") notFound();
    throw e;
  }
  const [demande, { documents }, langue, t, tStatut, tObjet] = await Promise.all([
    appelPage(chemin, async () => lireDemande(paiement.demandeId, await optionsApi())).then(
      (r) => r.data,
    ),
    chargerReferentiels(),
    getLocale(),
    getTranslations("detailPaiement"),
    getTranslations("statutsPaiement"),
    getTranslations("paiement.objet"),
  ]);
  const document = libelleDocument(
    documents.find((d) => d.code === demande.typeDocumentCode),
    langue,
    demande.typeDocumentCode,
  );
  const objet = tObjet.has(paiement.objet) ? tObjet(paiement.objet) : paiement.objet;
  const lienDemande = `/demandes/${paiement.demandeId}`;
  // Un nouvel essai n'a de sens que si cette somme est encore attendue pour la demande.
  const reessai =
    peutReessayer(paiement.statut) &&
    ((paiement.objet === "DROITS" && demande.statut === "decision") ||
      (paiement.objet !== "DROITS" && demande.statut === "deposee" && !demande.fraisPayes));

  return (
    <div className="flex flex-col gap-6">
      <EnTetePage
        titre={t("titre")}
        description={`${objet} · ${paiement.reference}`}
        retour={<FilArianeApp rubrique="paiements" courante={paiement.reference} />}
      />

      {enAttenteDeConfirmation(paiement.statut) ? (
        <Alerte nature="info" titre={t("attente.titre")}>
          {t("attente.texte")}
        </Alerte>
      ) : paiement.statut === "a_verifier" ? (
        <Alerte nature="avertissement" titre={t("aVerifier.titre")}>
          {t("aVerifier.texte")}
        </Alerte>
      ) : reessai ? (
        <Alerte nature="info" titre={t("nonAbouti.titre")}>
          <p>{t("nonAbouti.texte")}</p>
          <div className="mt-3">
            <Bouton asChild>
              <Link href={`${lienDemande}/paiement`}>
                <RotateCcw aria-hidden="true" className="size-4" />
                {t("nonAbouti.payer")}
              </Link>
            </Bouton>
          </div>
        </Alerte>
      ) : null}

      <div className="grid grid-cols-1 items-start gap-6 lg:grid-cols-[minmax(0,1fr)_22rem]">
        <Carte titre={t("details")}>
          <dl className="grid grid-cols-1 gap-x-6 gap-y-2 sm:grid-cols-[auto_1fr]">
            <dt className="font-semibold">{t("statut")}</dt>
            <dd>
              <BadgeStatut ton={tonStatutPaiement(paiement.statut)}>
                {tStatut.has(paiement.statut) ? tStatut(paiement.statut) : paiement.statut}
              </BadgeStatut>
            </dd>
            <dt className="font-semibold">{t("objet")}</dt>
            <dd>{objet}</dd>
            <dt className="font-semibold">{t("montant")}</dt>
            <dd className="tabular-nums">{formaterGnf(paiement.montantGnf, langue)}</dd>
            <dt className="font-semibold">{t("moyen")}</dt>
            <dd>{paiement.moyen === "TRESORPAY" ? "TrésorPay" : t("virement")}</dd>
            <dt className="font-semibold">{t("reference")}</dt>
            <dd className="tabular-nums">{paiement.reference}</dd>
            <dt className="font-semibold">{t("initieLe")}</dt>
            <dd>{formaterDateHeure(paiement.creeLe, langue)}</dd>
            {paiement.datePaiement ? (
              <>
                <dt className="font-semibold">{t("payeLe")}</dt>
                <dd>{formaterDateHeure(paiement.datePaiement, langue)}</dd>
              </>
            ) : null}
            {paiement.recuNumero ? (
              <>
                <dt className="font-semibold">{t("recu")}</dt>
                <dd className="flex flex-col items-start gap-2">
                  <span className="tabular-nums">{paiement.recuNumero}</span>
                  <LienRecu reference={paiement.reference} numero={paiement.recuNumero} />
                </dd>
              </>
            ) : null}
          </dl>
        </Carte>
        <Carte titre={t("demande.titre")}>
          <div className="flex flex-col gap-3">
            <p className="flex flex-col">
              <span className="font-semibold text-navy tabular-nums">
                {demande.numero ?? t("demande.brouillon")}
              </span>
              <span className="text-sm text-texte-doux">{document}</span>
            </p>
            <Bouton asChild variante="secondaire">
              <Link href={lienDemande}>
                <FolderOpen aria-hidden="true" className="size-4" />
                {t("demande.ouvrir")}
              </Link>
            </Bouton>
          </div>
        </Carte>
      </div>
    </div>
  );
}
