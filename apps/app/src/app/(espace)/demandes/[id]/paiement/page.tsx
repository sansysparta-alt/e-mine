import { ErreurApi, lireDemande, lirePaiementAttendu } from "@emine/api-client";
import { Alerte, Bouton, Carte, EnTetePage, formaterDate, formaterGnf } from "@emine/ui";
import { Eye, Landmark, ShieldCheck } from "@emine/ui/icones";
import type { Metadata } from "next";
import { libelleDocument } from "@/composants/referentiels";
import Link from "next/link";
import { notFound } from "next/navigation";
import { getLocale, getTranslations } from "next-intl/server";
import { ChoixMoyen } from "@/composants/paiement/choix-moyen";
import { choisirMoyen } from "@/serveur/actions-paiement";
import { FilArianeApp } from "@/composants/fil-ariane-app";
import { appelPage, chargerReferentiels, optionsApi } from "@/serveur/donnees";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("paiement");
  return { title: t("titreOnglet") };
}

type Props = { params: Promise<{ id: string }> };

/**
 * Paiement d'une demande (US-04.2) : montant fixé par le barème, choix du moyen sans
 * présélection. Un virement choisi mais pas encore déclaré renvoie à ses coordonnées ; un paiement
 * en cours de vérification (virement déclaré, US-04.7) ne se remplace pas. Si rien n'est à payer (déjà payé, ou pas à ce stade), la page le dit et ramène à
 * la demande.
 */
export default async function PaiementDemande({ params }: Props) {
  const { id } = await params;
  if (!/^\d{1,18}$/.test(id)) notFound();
  const chemin = `/demandes/${id}/paiement`;
  let demande;
  let attendu;
  try {
    [demande, attendu] = await Promise.all([
      appelPage(chemin, async () => lireDemande(Number(id), await optionsApi())).then(
        (r) => r.data,
      ),
      appelPage(chemin, async () => lirePaiementAttendu(Number(id), await optionsApi())).then(
        (r) => r.data,
      ),
    ]);
  } catch (e) {
    if (e instanceof ErreurApi && e.code === "DEM-002") notFound();
    throw e;
  }
  const t = await getTranslations("paiement");
  const { documents } = await chargerReferentiels();
  const document = documents.find((d) => d.code === demande.typeDocumentCode);
  const retour = (
    <FilArianeApp
      rubrique="mesDemandes"
      parents={[{ libelle: demande.numero ?? t("retourDemande"), href: `/demandes/${id}` }]}
      courante={t("filAriane")}
    />
  );
  const langue = await getLocale();
  const description = [
    demande.numero,
    document ? `${libelleDocument(document, langue)} (${document.code})` : null,
  ]
    .filter(Boolean)
    .join(" · ");

  if (attendu.objet == null || attendu.montantGnf == null) {
    return (
      <div className="flex flex-col gap-6">
        <EnTetePage titre={t("titreRien")} description={description} retour={retour} />
        <Alerte nature="info" titre={t("rienTitre")}>
          {t("rienTexte")}
        </Alerte>
        <div>
          <Bouton asChild variante="secondaire">
            <Link href={`/demandes/${id}`}>{t("retourDemande")}</Link>
          </Bouton>
        </div>
      </div>
    );
  }

  // Paiement en cours de vérification (virement déclaré, US-04.7) : rien à payer pour l'instant,
  // le moyen ne peut plus être changé.
  if (attendu.enVerification && attendu.referenceOuverte) {
    return (
      <div className="flex flex-col gap-6">
        <EnTetePage titre={t(`titre.${attendu.objet}`)} description={description} retour={retour} />
        <Alerte
          nature="info"
          titre={t("enVerificationTitre", { reference: attendu.referenceOuverte })}
        >
          {t("enVerificationTexte")}
        </Alerte>
        <div className="flex flex-wrap gap-3">
          <Bouton asChild>
            <Link href={`/paiements/${attendu.referenceOuverte}`}>
              <Eye aria-hidden="true" className="size-4" />
              {t("voirPaiement")}
            </Link>
          </Bouton>
          <Bouton asChild variante="secondaire">
            <Link href={`/demandes/${id}`}>{t("retourDemande")}</Link>
          </Bouton>
        </div>
      </div>
    );
  }

  const montant = formaterGnf(attendu.montantGnf);
  const virementOuvert = attendu.referenceOuverte != null && attendu.moyenOuvert === "VIREMENT";
  // US-04.12 : complément d'un paiement insuffisant ; seul le reste est à payer.
  const complement = attendu.complement;
  return (
    <div className="flex flex-col gap-6">
      <EnTetePage
        titre={complement ? t("complement.titre") : t(`titre.${attendu.objet}`)}
        description={description}
        retour={retour}
      />
      {complement ? (
        <Alerte
          nature="avertissement"
          titre={t("complement.alerteTitre", { reference: complement.reference })}
        >
          <div className="flex flex-col gap-2">
            <p>
              {t("complement.alerteTexte", {
                recu: formaterGnf(complement.montantRecuGnf),
                du: formaterGnf(complement.montantGnf),
                reste: formaterGnf(complement.resteGnf),
              })}
            </p>
            {complement.motif ? <p>{t("complement.motif", { motif: complement.motif })}</p> : null}
            {complement.echeance ? (
              <p>{t("complement.echeance", { date: formaterDate(complement.echeance) })}</p>
            ) : null}
          </div>
        </Alerte>
      ) : null}
      <div className="grid gap-6 lg:grid-cols-[1fr_2fr]">
        <Carte titre={t("montantTitre")} className="h-fit">
          <p className="text-3xl font-semibold text-navy tabular-nums">{montant}</p>
          <p className="mt-2 text-texte-doux">
            {complement
              ? t("complement.objet", { reference: complement.reference })
              : t(`objet.${attendu.objet}`)}
          </p>
          <p className="mt-4 flex items-start gap-2 text-sm text-texte-doux">
            <ShieldCheck aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-succes" />
            {complement ? t("complement.recu") : t("montantBareme")}
          </p>
        </Carte>
        <Carte titre={t("moyenTitre")}>
          {virementOuvert ? (
            <Alerte
              nature="avertissement"
              titre={t("virementOuvertTitre", { reference: attendu.referenceOuverte ?? "" })}
              className="mb-4"
            >
              <p>{t("virementOuvertTexte")}</p>
              <div className="mt-3">
                <Bouton asChild>
                  <Link href={`/paiements/${attendu.referenceOuverte}`}>
                    <Landmark aria-hidden="true" className="size-4" />
                    {t("voirVirement")}
                  </Link>
                </Bouton>
              </div>
            </Alerte>
          ) : attendu.referenceOuverte ? (
            <Alerte
              nature="info"
              titre={t("enCoursTitre", { reference: attendu.referenceOuverte })}
              className="mb-4"
            >
              {t("enCoursTexte")}
            </Alerte>
          ) : null}
          <ChoixMoyen
            demandeId={demande.id}
            moyens={attendu.moyens}
            montant={montant}
            tentativesRestantes={attendu.tentativesRestantes}
            choisir={choisirMoyen}
          />
        </Carte>
      </div>
    </div>
  );
}
