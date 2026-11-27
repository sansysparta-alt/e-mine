import { ErreurApi, verifierDocument, verifierRecu, type VerificationDto } from "@emine/api-client";
import { Alerte, Bouton, Carte, formaterDateHeure } from "@emine/ui";
import { ShieldCheck, ShieldX } from "@emine/ui/icones";
import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { extraireCode } from "@/composants/verification";
import { VerifierFichier } from "@/composants/verifier-fichier";

// Toujours lu au moment de la requête : une révocation doit se voir aussitôt.
export const dynamic = "force-dynamic";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("verification");
  return { title: t("titre"), robots: { index: false, follow: false } };
}

type Props = { params: Promise<{ code: string }> };

type Issue =
  { cas: "trouve"; verification: VerificationDto } | { cas: "inconnu" } | { cas: "indisponible" };

async function verifier(code: string | null): Promise<Issue> {
  if (!code) return { cas: "inconnu" };
  try {
    return { cas: "trouve", verification: (await verifierDocument(code)).data };
  } catch (e) {
    if (e instanceof ErreurApi && e.code === "DOC-007") return { cas: "inconnu" };
    console.error("Vérification : API injoignable", e);
    return { cas: "indisponible" };
  }
}

/** Le code est-il celui d'un reçu ? Une erreur laisse le message « aucun document ». */
async function estUnRecu(code: string): Promise<boolean> {
  try {
    await verifierRecu(code);
    return true;
  } catch {
    return false;
  }
}

/**
 * Vérification publique d'un document délivré (US-06.5) : ouverte par le QR, sans connexion,
 * sans donnée personnelle ; « Document authentique » ou « NON VALIDE » (révoqué), puis
 * comparaison facultative du fichier présenté.
 */
export default async function PageVerification({ params }: Props) {
  const { code } = await params;
  const t = await getTranslations("verification");
  const lu = extraireCode(code);
  const issue = await verifier(lu);
  // Code saisi ou scanné ici mais qui est celui d'un reçu (US-04.14) : page de vérification du reçu.
  if (issue.cas === "inconnu" && lu && (await estUnRecu(lu))) redirect(`/verifier/recu/${lu}`);
  const autre = (
    <Bouton asChild variante="secondaire">
      <Link href="/verifier">{t("autre")}</Link>
    </Bouton>
  );

  return (
    <main id="contenu" className="mx-auto flex max-w-3xl flex-col gap-6 px-4 py-10 sm:px-6">
      <h1 className="text-3xl sm:text-4xl">{t("titre")}</h1>
      {issue.cas === "trouve" ? (
        <Resultat verification={issue.verification} />
      ) : issue.cas === "inconnu" ? (
        <Alerte nature="erreur" titre={t("inconnu.titre")}>
          {t("inconnu.texte")}
        </Alerte>
      ) : (
        <Alerte nature="erreur" titre={t("indisponible.titre")}>
          {t("indisponible.texte")}
        </Alerte>
      )}
      <div>{autre}</div>
      <p className="text-sm text-texte-doux">{t("confidentialite")}</p>
    </main>
  );
}

async function Resultat({ verification: v }: { verification: VerificationDto }) {
  const t = await getTranslations("verification");
  const authentique = v.statut === "AUTHENTIQUE";
  const Icone = authentique ? ShieldCheck : ShieldX;
  return (
    <>
      <section
        aria-labelledby="statut-document"
        className={
          authentique
            ? "flex items-start gap-4 rounded-carte border-2 border-succes bg-succes-fond p-5"
            : "flex items-start gap-4 rounded-carte border-2 border-danger bg-danger-fond p-5"
        }
      >
        <Icone
          aria-hidden="true"
          className={authentique ? "size-10 shrink-0 text-succes" : "size-10 shrink-0 text-danger"}
        />
        <div>
          <h2 id="statut-document" className="text-2xl">
            {authentique ? t("authentique.titre") : t("nonValide.titre")}
          </h2>
          <p className="mt-1">{authentique ? t("authentique.texte") : t("nonValide.texte")}</p>
        </div>
      </section>
      <Carte titre={t("details.titre")}>
        <dl className="grid grid-cols-1 gap-x-6 gap-y-2 sm:grid-cols-[auto_1fr]">
          <dt className="font-semibold">{t("details.document")}</dt>
          <dd>
            {v.document} ({v.codeType})
          </dd>
          <dt className="font-semibold">{t("details.numero")}</dt>
          <dd className="font-semibold">{v.numero}</dd>
          <dt className="font-semibold">{t("details.delivreLe")}</dt>
          <dd>{formaterDateHeure(v.delivreLe)}</dd>
          {v.signataire ? (
            <>
              <dt className="font-semibold">{t("details.signataire")}</dt>
              <dd>{v.signataire}</dd>
            </>
          ) : null}
          <dt className="font-semibold">{t("details.empreinte")}</dt>
          <dd className="text-sm break-all text-texte-doux">{v.empreinteSha256}</dd>
        </dl>
      </Carte>
      {authentique ? (
        <Carte titre={t("fichier.titre")}>
          <VerifierFichier empreinteAttendue={v.empreinteSha256} />
        </Carte>
      ) : null}
    </>
  );
}
