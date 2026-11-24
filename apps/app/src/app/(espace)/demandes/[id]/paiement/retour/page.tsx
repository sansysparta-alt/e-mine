import {
  ErreurApi,
  lireDemande,
  lirePaiement,
  lirePaiementAttendu,
  type PaiementDto,
} from "@emine/api-client";
import {
  Alerte,
  BadgeStatut,
  Bouton,
  Carte,
  EnTetePage,
  formaterDateHeure,
  formaterGnf,
} from "@emine/ui";
import { RotateCcw } from "@emine/ui/icones";
import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { AttenteConfirmation } from "@/composants/paiement/attente-confirmation";
import { LienRecu } from "@/composants/paiement/lien-recu";
import {
  enAttenteDeConfirmation,
  peutReessayer,
  tonStatutPaiement,
} from "@/composants/paiement/statut";
import { FilArianeApp } from "@/composants/fil-ariane-app";
import { appelPage, optionsApi } from "@/serveur/donnees";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("paiement.retour");
  return { title: t("titreOnglet") };
}

type Props = {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ reference?: string }>;
};

const REFERENCE = /^PAY-\d{4}-\d{6}$/;

/**
 * Retour de TrésorPay (US-04.3). Le retour du navigateur ne prouve rien : la page lit le statut en
 * base, mis à jour par la seule notification signée (US-04.4), et l'attend quelques secondes si
 * besoin. Chaque issue dit clairement ce qui s'est passé et quoi faire.
 */
export default async function RetourPaiement({ params, searchParams }: Props) {
  const [{ id }, { reference }] = await Promise.all([params, searchParams]);
  if (!/^\d{1,18}$/.test(id) || !reference || !REFERENCE.test(reference)) notFound();
  const chemin = `/demandes/${id}/paiement/retour?reference=${reference}`;
  let paiement: PaiementDto;
  try {
    paiement = (await appelPage(chemin, async () => lirePaiement(reference, await optionsApi())))
      .data;
  } catch (e) {
    if (e instanceof ErreurApi && e.code === "PAY-005") notFound();
    throw e;
  }
  if (String(paiement.demandeId) !== id) notFound();

  const t = await getTranslations("paiement.retour");
  const tStatut = await getTranslations("statutsPaiement");
  const tObjet = await getTranslations("paiement.objet");
  const lienDemande = `/demandes/${id}`;
  const lienPayer = `/demandes/${id}/paiement`;
  const numeroDemande =
    (await appelPage(chemin, async () => lireDemande(Number(id), await optionsApi()))).data
      .numero ?? t("retourDemande");
  const tentatives = peutReessayer(paiement.statut)
    ? (await lirePaiementAttendu(Number(id), await optionsApi())).data.tentativesRestantes
    : null;

  return (
    <div className="flex flex-col gap-6">
      <EnTetePage
        titre={t("titre")}
        description={`${tObjet.has(paiement.objet) ? tObjet(paiement.objet) : paiement.objet} · ${paiement.reference}`}
        retour={
          <FilArianeApp
            rubrique="mesDemandes"
            parents={[{ libelle: numeroDemande, href: lienDemande }]}
            courante={paiement.reference}
          />
        }
      />

      <Carte>
        {enAttenteDeConfirmation(paiement.statut) ? (
          <AttenteConfirmation lienDemande={lienDemande} />
        ) : (
          <Issue
            statut={paiement.statut}
            objet={paiement.objet}
            reference={paiement.reference}
            recuNumero={paiement.recuNumero}
            tentatives={tentatives}
            lienPayer={lienPayer}
            t={t}
          />
        )}
      </Carte>

      <Carte titre={t("details")}>
        <dl className="grid grid-cols-1 gap-x-6 gap-y-2 sm:grid-cols-[auto_1fr]">
          <dt className="font-semibold">{t("statut")}</dt>
          <dd>
            <BadgeStatut ton={tonStatutPaiement(paiement.statut)}>
              {tStatut.has(paiement.statut) ? tStatut(paiement.statut) : paiement.statut}
            </BadgeStatut>
          </dd>
          <dt className="font-semibold">{t("reference")}</dt>
          <dd className="tabular-nums">{paiement.reference}</dd>
          <dt className="font-semibold">{t("montant")}</dt>
          <dd className="tabular-nums">{formaterGnf(paiement.montantGnf)}</dd>
          <dt className="font-semibold">{t("moyen")}</dt>
          <dd>{paiement.moyen === "TRESORPAY" ? "TrésorPay" : t("virement")}</dd>
          {paiement.datePaiement ? (
            <>
              <dt className="font-semibold">{t("payeLe")}</dt>
              <dd>{formaterDateHeure(paiement.datePaiement)}</dd>
            </>
          ) : null}
          {paiement.recuNumero ? (
            <>
              <dt className="font-semibold">{t("recu")}</dt>
              <dd className="tabular-nums">{paiement.recuNumero}</dd>
            </>
          ) : null}
        </dl>
      </Carte>
    </div>
  );
}

type IssueProps = {
  statut: string;
  /** Objet payé : les droits mènent à la signature, les frais à l'examen du guichet. */
  objet: string;
  reference: string;
  recuNumero: string | null;
  tentatives: number | null;
  lienPayer: string;
  t: Awaited<ReturnType<typeof getTranslations<"paiement.retour">>>;
};

/** Message et actions selon l'issue du paiement. */
function Issue({ statut, objet, reference, recuNumero, tentatives, lienPayer, t }: IssueProps) {
  if (statut === "paye") {
    const cle = objet === "DROITS" ? "payeDroits" : "paye";
    return (
      <div className="flex flex-col gap-4">
        <Alerte nature="succes" titre={t(`issue.${cle}.titre`)}>
          {t(`issue.${cle}.texte`)}
        </Alerte>
        {recuNumero ? (
          <div className="flex flex-wrap gap-3">
            <LienRecu reference={reference} numero={recuNumero} />
          </div>
        ) : null}
      </div>
    );
  }
  if (statut === "a_verifier") {
    return (
      <div className="flex flex-col gap-4">
        <Alerte nature="avertissement" titre={t("issue.a_verifier.titre")}>
          {t("issue.a_verifier.texte")}
        </Alerte>
      </div>
    );
  }
  const cle = ["echoue", "annule", "expire"].includes(statut) ? statut : "autre";
  const reessai = peutReessayer(statut);
  return (
    <div className="flex flex-col gap-4">
      <Alerte nature={statut === "echoue" ? "erreur" : "info"} titre={t(`issue.${cle}.titre`)}>
        <p>{t(`issue.${cle}.texte`)}</p>
        {reessai && tentatives != null ? (
          <p className="mt-1">
            {tentatives > 0
              ? t("tentativesRestantes", { nombre: tentatives })
              : t("tentativesEpuisees")}
          </p>
        ) : null}
      </Alerte>
      <div className="flex flex-wrap gap-3">
        {reessai && (tentatives ?? 0) > 0 ? (
          <Bouton asChild>
            <Link href={lienPayer}>
              <RotateCcw aria-hidden="true" className="size-4" />
              {t("reessayer")}
            </Link>
          </Bouton>
        ) : null}
      </div>
    </div>
  );
}
