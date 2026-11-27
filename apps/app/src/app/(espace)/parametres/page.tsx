import {
  type DetailRegleDelaiDto,
  lireParametresDelai,
  lireRegleRemboursement,
  listerCanauxNotification,
  type VersionRegleDelaiDto,
} from "@emine/api-client";
import { Alerte, Bouton, Carte, EnTetePage, formaterDate } from "@emine/ui";
import { FilePen } from "@emine/ui/icones";
import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { DecisionVersion } from "@/composants/catalogue/decision-version";
import {
  CarteProposition,
  HistoriqueVersions,
  type VersionGouvernee,
} from "@/composants/gouvernance/gouvernance";
import { FormulaireCanaux } from "@/composants/parametres/formulaire-canaux";
import {
  ChangementsRegleRemboursement,
  FormulaireRegleRemboursement,
  ResumeRegleRemboursement,
} from "@/composants/parametres/regle-remboursement";
import { deciderRegle, proposerRegle } from "@/serveur/actions-remboursements";
import {
  ChangementsRegleDelais,
  ResumeRegleDelais,
} from "@/composants/parametres/resume-regle-delais";
import { deciderDelais, enregistrerCanaux } from "@/serveur/actions-parametres";
import { appelPage, chargerMoi, optionsApi } from "@/serveur/donnees";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("parametres");
  return { title: t("titre") };
}

type Props = {
  searchParams: Promise<{ proposee?: string; resultat?: string; regle?: string }>;
};

const RESULTATS = ["approuver", "refuser", "retirer"] as const;
/** Règle concernée par le message de résultat : il la nomme (US-02.10). */
const REGLES = ["delais", "remboursement"] as const;

/** Dans un état donné, la version la plus récente. */
function dans(d: DetailRegleDelaiDto, etat: string): VersionRegleDelaiDto | undefined {
  return d.versions.find((v) => v.etat === etat);
}

/**
 * Délais et notifications (US-02.6, US-02.7, US-02.8) : règle des délais (proposée par
 * l'administrateur, validée par le Ministre, appliquée à sa date d'effet) et canaux de chaque
 * notification (réglés par l'administrateur, aussitôt). Choix provisoires à faire valider par le
 * Ministère.
 */
export default async function Parametres({ searchParams }: Props) {
  const moi = await chargerMoi();
  const admin = moi.permissions.includes("PARAMETRER");
  if (!moi.interne || (!admin && !moi.permissions.includes("VALIDER_CATALOGUE"))) notFound();
  const t = await getTranslations("parametres");
  const tGouvernance = await getTranslations("gouvernance");
  const tDecision = await getTranslations("catalogue.decision");
  const { proposee, resultat, regle } = await searchParams;
  const regleConnue = REGLES.find((r) => r === regle);
  const nomRegle = regleConnue ? t(`regles.${regleConnue}`) : null;

  const [delais, canaux, remboursement] = await appelPage("/parametres", async () => {
    const options = await optionsApi();
    return Promise.all([
      lireParametresDelai(options).then((r) => r.data),
      listerCanauxNotification(options).then((r) => r.data),
      lireRegleRemboursement(options).then((r) => r.data),
    ]);
  });
  const rembEnVigueur = remboursement.versions.find((v) => v.etat === "en_vigueur");
  const rembAValider = remboursement.versions.find((v) => v.etat === "a_valider");
  const rembProgrammee = remboursement.versions.find((v) => v.etat === "programmee");
  const enVigueur = dans(delais, "en_vigueur");
  const aValider = dans(delais, "a_valider");
  const programmee = dans(delais, "programmee");
  const resultatConnu = RESULTATS.find((r) => r === resultat);

  return (
    <div className="flex flex-col gap-6">
      <EnTetePage
        titre={t("titre")}
        description={t(admin ? "description.admin" : "description.lecture")}
      />
      {proposee && /^\d{1,4}$/.test(proposee) ? (
        <Alerte nature="succes" titre={tGouvernance("resultat.titre")}>
          {nomRegle
            ? tGouvernance("resultatRegle.proposee", { n: Number(proposee), regle: nomRegle })
            : tGouvernance("resultat.proposee", { n: Number(proposee) })}
        </Alerte>
      ) : null}
      {resultatConnu ? (
        <Alerte nature="succes" titre={tGouvernance("resultat.titre")}>
          {nomRegle
            ? tGouvernance(`resultatRegle.${resultatConnu}`, { regle: nomRegle })
            : tGouvernance(`resultat.${resultatConnu}`)}
        </Alerte>
      ) : null}
      <Alerte nature="info" titre={t("provisoire.titre")}>
        {t("provisoire.texte")}
      </Alerte>

      {aValider ? (
        <CarteProposition
          v={aValider satisfies VersionGouvernee}
          decideur={delais.peutDecider}
          objet={t("regles.delais")}
          decision={
            delais.peutDecider || admin ? (
              <DecisionVersion
                code="delais"
                numero={aValider.version}
                action={delais.peutDecider ? "DECIDER" : "ATTENDRE"}
                changeSignataire={false}
                desactive={false}
                decider={deciderDelais}
                adresseApres="/parametres?regle=delais"
                texteApprobation={tGouvernance("approbation")}
                objet={t("regles.objetDelais")}
                texteRefus={tDecision("confirmation.refuser.texteRegle")}
              />
            ) : undefined
          }
        >
          <div className="flex flex-col gap-4">
            <ChangementsRegleDelais avant={delais.enVigueur} apres={aValider.regle} />
            <ResumeRegleDelais regle={aValider.regle} />
          </div>
        </CarteProposition>
      ) : null}

      {programmee ? (
        <Alerte
          nature="info"
          titre={tGouvernance("programmee.titre", {
            n: programmee.version,
            date: formaterDate(programmee.dateEffet),
          })}
        >
          {tGouvernance("programmee.texte", { actuelle: enVigueur?.version ?? "" })}
        </Alerte>
      ) : null}

      <Carte titre={t("delais.enVigueur", { n: enVigueur?.version ?? 1 })}>
        <div className="flex flex-col gap-4">
          <ResumeRegleDelais regle={delais.enVigueur} />
          {delais.peutProposer ? (
            <div>
              <Bouton asChild variante="secondaire">
                <Link href="/parametres/delais/proposer">
                  <FilePen aria-hidden="true" className="size-5" />
                  {t("delais.proposer")}
                </Link>
              </Bouton>
            </div>
          ) : null}
        </div>
      </Carte>

      <HistoriqueVersions
        titre={t("delais.historique")}
        versions={delais.versions}
        decideur={delais.peutDecider}
        contenu={(v) => <ResumeRegleDelais regle={v.regle} />}
      />

      <section aria-labelledby="titre-remboursement" className="flex flex-col gap-6">
        <h2 id="titre-remboursement" className="text-xl font-semibold">
          {t("remboursement.titre")}
        </h2>
        {rembAValider ? (
          <CarteProposition
            v={rembAValider satisfies VersionGouvernee}
            decideur={remboursement.peutDecider}
            objet={t("regles.remboursement")}
            decision={
              remboursement.peutDecider || admin ? (
                <DecisionVersion
                  code="remboursement"
                  numero={rembAValider.version}
                  action={remboursement.peutDecider ? "DECIDER" : "ATTENDRE"}
                  changeSignataire={false}
                  desactive={false}
                  decider={deciderRegle}
                  adresseApres="/parametres?regle=remboursement"
                  texteApprobation={tGouvernance("approbation")}
                  objet={t("regles.objetRemboursement")}
                  texteRefus={tDecision("confirmation.refuser.texteRegle")}
                />
              ) : undefined
            }
          >
            <div className="flex flex-col gap-4">
              <ChangementsRegleRemboursement
                avant={remboursement.rembourserSiRejet}
                apres={rembAValider.rembourserSiRejet}
              />
              <ResumeRegleRemboursement rembourserSiRejet={rembAValider.rembourserSiRejet} />
            </div>
          </CarteProposition>
        ) : null}
        {rembProgrammee ? (
          <Alerte
            nature="info"
            titre={tGouvernance("programmee.titre", {
              n: rembProgrammee.version,
              date: formaterDate(rembProgrammee.dateEffet),
            })}
          >
            {tGouvernance("programmee.texte", { actuelle: rembEnVigueur?.version ?? "" })}
          </Alerte>
        ) : null}
        <Carte titre={t("remboursement.enVigueur", { n: rembEnVigueur?.version ?? 1 })}>
          <div className="flex flex-col gap-4">
            <ResumeRegleRemboursement rembourserSiRejet={remboursement.rembourserSiRejet} />
            {remboursement.peutProposer ? (
              <FormulaireRegleRemboursement
                enVigueur={remboursement.rembourserSiRejet}
                proposer={proposerRegle}
              />
            ) : null}
          </div>
        </Carte>
        <HistoriqueVersions
          titre={t("remboursement.historique")}
          versions={remboursement.versions}
          decideur={remboursement.peutDecider}
          contenu={(v) => <ResumeRegleRemboursement rembourserSiRejet={v.rembourserSiRejet} />}
        />
      </section>

      <Carte titre={t("canaux.titre")}>
        <FormulaireCanaux initial={canaux} modifiable={admin} enregistrer={enregistrerCanaux} />
      </Carte>
    </div>
  );
}
