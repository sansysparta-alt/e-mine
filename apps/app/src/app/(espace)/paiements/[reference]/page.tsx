import { ErreurApi, lireDemande, lirePaiement, type PaiementDto } from "@emine/api-client";
import {
  Alerte,
  BadgeStatut,
  Bouton,
  Carte,
  EnTetePage,
  formaterDate,
  formaterDateHeure,
  formaterGnf,
} from "@emine/ui";
import { presentationStatutRemboursement } from "@/composants/remboursement/statut";
import { FolderOpen, RotateCcw } from "@emine/ui/icones";
import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { getLocale, getTranslations } from "next-intl/server";
import { FilArianeApp } from "@/composants/fil-ariane-app";
import { BadgeStatutPaiement } from "@/composants/paiement/badge-statut-paiement";
import { CoordonneesVirement } from "@/composants/paiement/coordonnees-virement";
import { DeclarationVirement } from "@/composants/paiement/declaration-virement";
import { LienJustificatif } from "@/composants/paiement/lien-justificatif";
import { LienRecu } from "@/composants/paiement/lien-recu";
import {
  enAttenteDeConfirmation,
  peutReessayer,
  referenceVirementExpiree,
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
 *
 * Virement (US-04.7) : tant qu'il n'est pas déclaré, la page donne les coordonnées du compte, le
 * montant exact, la référence et la date limite, et recueille le justificatif (« J'ai effectué le
 * virement ») ; une fois déclaré, elle dit qu'il est en cours de vérification.
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
  const [demande, { documents }, langue, t, tObjet] = await Promise.all([
    appelPage(chemin, async () => lireDemande(paiement.demandeId, await optionsApi())).then(
      (r) => r.data,
    ),
    chargerReferentiels(),
    getLocale(),
    getTranslations("detailPaiement"),
    getTranslations("paiement.objet"),
  ]);
  const tRemb = await getTranslations("remboursements");
  const document = libelleDocument(
    documents.find((d) => d.code === demande.typeDocumentCode),
    langue,
    demande.typeDocumentCode,
  );
  const objet = tObjet.has(paiement.objet) ? tObjet(paiement.objet) : paiement.objet;
  const lienDemande = `/demandes/${paiement.demandeId}`;
  const virement = paiement.moyen === "VIREMENT";
  // Référence de virement dépassée : la base la passe à « expiré » (tâche planifiée), l'écran le
  // dit dès la date limite passée.
  const referenceExpiree = referenceVirementExpiree(paiement);
  const aEffectuer = virement && paiement.statut === "initie" && !referenceExpiree;
  const declare = virement && paiement.statut === "en_attente";
  // Un nouvel essai n'a de sens que si cette somme est encore attendue pour la demande.
  const rejete = paiement.statut === "rejete";
  const reessai =
    (peutReessayer(paiement.statut) || referenceExpiree || rejete) &&
    ((paiement.objet === "DROITS" && demande.statut === "decision") ||
      (paiement.objet !== "DROITS" && demande.statut === "deposee" && !demande.fraisPayes));

  return (
    <div className="flex flex-col gap-6">
      <EnTetePage
        titre={t("titre")}
        description={`${objet} · ${paiement.reference}`}
        retour={<FilArianeApp rubrique="paiements" courante={paiement.reference} />}
      />

      {aEffectuer && paiement.dateExpiration ? (
        <Alerte
          nature="avertissement"
          titre={t("virementAEffectuer.titre", {
            date: formaterDateHeure(paiement.dateExpiration, langue),
          })}
        >
          {t("virementAEffectuer.texte", { reference: paiement.reference })}
        </Alerte>
      ) : declare ? (
        <Alerte nature="info" titre={t("virementDeclare.titre")}>
          {t("virementDeclare.texte", {
            date: formaterDateHeure(paiement.virementDeclareLe ?? paiement.creeLe, langue),
          })}
        </Alerte>
      ) : enAttenteDeConfirmation(paiement) ? (
        <Alerte nature="info" titre={t("attente.titre")}>
          {t("attente.texte")}
        </Alerte>
      ) : paiement.statut === "a_verifier" ? (
        <Alerte nature="info" titre={t("aVerifier.titre")}>
          {t("aVerifier.texte")}
        </Alerte>
      ) : reessai ? (
        <Alerte
          nature={rejete ? "avertissement" : "info"}
          titre={
            rejete
              ? t("rejete.titre")
              : referenceExpiree
                ? t("virementExpire.titre")
                : t("nonAbouti.titre")
          }
        >
          {rejete ? (
            <>
              {paiement.motifRejet ? (
                <p>{t("rejete.motif", { motif: paiement.motifRejet })}</p>
              ) : null}
              <p>{t("rejete.texte")}</p>
            </>
          ) : (
            <p>{referenceExpiree ? t("virementExpire.texte") : t("nonAbouti.texte")}</p>
          )}
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
              <BadgeStatutPaiement paiement={paiement} profil="usager" />
            </dd>
            <dt className="font-semibold">{t("objet")}</dt>
            <dd>{objet}</dd>
            <dt className="font-semibold">{t("montant")}</dt>
            <dd className="tabular-nums">{formaterGnf(paiement.montantGnf, langue)}</dd>
            <dt className="font-semibold">{t("moyen")}</dt>
            <dd>{virement ? t("virement") : "TrésorPay"}</dd>
            <dt className="font-semibold">{t("reference")}</dt>
            <dd className="tabular-nums">{paiement.reference}</dd>
            <dt className="font-semibold">{t("initieLe")}</dt>
            <dd>{formaterDateHeure(paiement.creeLe, langue)}</dd>
            {aEffectuer && paiement.dateExpiration ? (
              <>
                <dt className="font-semibold">{t("dateLimite")}</dt>
                <dd>{formaterDateHeure(paiement.dateExpiration, langue)}</dd>
              </>
            ) : null}
            {paiement.virementDeclareLe ? (
              <>
                <dt className="font-semibold">{t("declareLe")}</dt>
                <dd>{formaterDateHeure(paiement.virementDeclareLe, langue)}</dd>
              </>
            ) : null}
            {paiement.justificatifFichier ? (
              <>
                <dt className="font-semibold">{t("justificatif")}</dt>
                <dd className="flex flex-col items-start gap-2">
                  <LienJustificatif
                    reference={paiement.reference}
                    fichier={paiement.justificatifFichier}
                  />
                </dd>
              </>
            ) : null}
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
      {paiement.remboursements && paiement.remboursements.length > 0 ? (
        <Carte titre={t("remboursements.titre")}>
          <ul className="flex flex-col divide-y divide-bordure">
            {paiement.remboursements.map((r) => {
              const p = presentationStatutRemboursement(r.statut, false);
              return (
                <li key={r.numero} className="flex flex-col gap-1 py-3 first:pt-0 last:pb-0">
                  <span className="flex flex-wrap items-center gap-3">
                    <span className="font-semibold tabular-nums">
                      {formaterGnf(r.montantGnf, langue)}
                    </span>
                    <BadgeStatut ton={p.ton}>{tRemb(`statuts.${p.cle}`)}</BadgeStatut>
                  </span>
                  <span className="text-sm text-texte-doux">
                    {r.executeLe
                      ? t("remboursements.effectue", {
                          numero: r.numero,
                          date: formaterDate(r.executeLe),
                          ordre: r.referenceOrdre ?? "",
                        })
                      : r.motifRefus
                        ? t("remboursements.refuse", { numero: r.numero, motif: r.motifRefus })
                        : t("remboursements.enCours", {
                            numero: r.numero,
                            date: formaterDate(r.demandeLe),
                          })}
                  </span>
                </li>
              );
            })}
          </ul>
        </Carte>
      ) : null}
      {aEffectuer && paiement.coordonnees ? (
        <div className="grid grid-cols-1 items-start gap-6 lg:grid-cols-2">
          <CoordonneesVirement
            coordonnees={paiement.coordonnees}
            montantGnf={paiement.montantGnf}
            reference={paiement.reference}
          />
          <DeclarationVirement reference={paiement.reference} />
        </div>
      ) : null}
    </div>
  );
}
