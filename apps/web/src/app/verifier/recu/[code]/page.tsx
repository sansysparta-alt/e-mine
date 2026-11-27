import { ErreurApi, verifierRecu, type VerificationRecuDto } from "@emine/api-client";
import { Alerte, Bouton, Carte, formaterDate, formaterDateHeure, formaterGnf } from "@emine/ui";
import { ShieldAlert, ShieldCheck } from "@emine/ui/icones";
import type { Metadata } from "next";
import Link from "next/link";
import { getLocale, getTranslations } from "next-intl/server";
import { extraireCode } from "@/composants/verification";

// Toujours lu au moment de la requête : un remboursement doit se voir aussitôt.
export const dynamic = "force-dynamic";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("verificationRecu");
  return { title: t("titre"), robots: { index: false, follow: false } };
}

type Props = { params: Promise<{ code: string }> };

type Issue =
  { cas: "trouve"; recu: VerificationRecuDto } | { cas: "inconnu" } | { cas: "indisponible" };

async function verifier(code: string | null): Promise<Issue> {
  if (!code) return { cas: "inconnu" };
  try {
    return { cas: "trouve", recu: (await verifierRecu(code)).data };
  } catch (e) {
    if (e instanceof ErreurApi && e.code === "PAY-017") return { cas: "inconnu" };
    console.error("Vérification d'un reçu : API injoignable", e);
    return { cas: "indisponible" };
  }
}

/**
 * Vérification publique d'un reçu (US-04.14) : ouverte par le QR du reçu, sans connexion, sans
 * donnée personnelle ; « Reçu authentique », ou authentique mais remboursé depuis.
 */
export default async function PageVerificationRecu({ params }: Props) {
  const { code } = await params;
  const t = await getTranslations("verificationRecu");
  const issue = await verifier(extraireCode(code));
  return (
    <main id="contenu" className="mx-auto flex max-w-3xl flex-col gap-6 px-4 py-10 sm:px-6">
      <h1 className="text-3xl sm:text-4xl">{t("titre")}</h1>
      {issue.cas === "trouve" ? (
        <Resultat recu={issue.recu} />
      ) : (
        <Alerte nature="erreur" titre={t(`${issue.cas}.titre`)}>
          {t(`${issue.cas}.texte`)}
        </Alerte>
      )}
      <div>
        <Bouton asChild variante="secondaire">
          <Link href="/verifier">{t("autre")}</Link>
        </Bouton>
      </div>
      <p className="text-sm text-texte-doux">{t("confidentialite")}</p>
    </main>
  );
}

async function Resultat({ recu: r }: { recu: VerificationRecuDto }) {
  const t = await getTranslations("verificationRecu");
  const langue = await getLocale();
  const cle = r.rembourse ? "rembourse" : "authentique";
  const Icone = r.rembourse ? ShieldAlert : ShieldCheck;
  return (
    <>
      <section
        aria-labelledby="statut-recu"
        className={
          r.rembourse
            ? "flex items-start gap-4 rounded-carte border-2 border-avertissement bg-avertissement-fond p-5"
            : "flex items-start gap-4 rounded-carte border-2 border-succes bg-succes-fond p-5"
        }
      >
        <Icone
          aria-hidden="true"
          className={
            r.rembourse ? "size-10 shrink-0 text-avertissement" : "size-10 shrink-0 text-succes"
          }
        />
        <div>
          <h2 id="statut-recu" className="text-2xl">
            {t(`${cle}.titre`)}
          </h2>
          <p className="mt-1">{t(`${cle}.texte`)}</p>
        </div>
      </section>
      <Carte titre={t("details.titre")}>
        <dl className="grid grid-cols-1 gap-x-6 gap-y-2 sm:grid-cols-[auto_1fr]">
          <dt className="font-semibold">{t("details.numero")}</dt>
          <dd className="font-semibold whitespace-nowrap tabular-nums">{r.numero}</dd>
          <dt className="font-semibold">{t("details.objet")}</dt>
          <dd>{t(`objets.${r.objet}`)}</dd>
          <dt className="font-semibold">{t("details.montant")}</dt>
          <dd className="font-semibold whitespace-nowrap tabular-nums">
            {formaterGnf(r.montantGnf, langue)}
          </dd>
          <dt className="font-semibold">{t("details.payeLe")}</dt>
          <dd>
            {r.heureConnue ? formaterDateHeure(r.payeLe, langue) : formaterDate(r.payeLe, langue)}
          </dd>
          <dt className="font-semibold">{t("details.moyen")}</dt>
          <dd>{t(`moyens.${r.moyen}`)}</dd>
          <dt className="font-semibold">{t("details.demande")}</dt>
          <dd className="whitespace-nowrap tabular-nums">{r.demandeNumero}</dd>
        </dl>
      </Carte>
    </>
  );
}
