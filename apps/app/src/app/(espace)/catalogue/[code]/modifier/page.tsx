import { ErreurApi, lireFicheDocument, referencesFichesDocuments } from "@emine/api-client";
import { dateDuJour, EnTetePage } from "@emine/ui";
import { ArrowLeft } from "@emine/ui/icones";
import type { Metadata } from "next";
import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { FormulaireFiche } from "@/composants/catalogue/formulaire-fiche";
import { proposerFiche } from "@/serveur/actions-catalogue";
import { appelPage, chargerMoi, optionsApi } from "@/serveur/donnees";

type Props = { params: Promise<{ code: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { code } = await params;
  const t = await getTranslations("catalogue.formulaire");
  return { title: `${code} · ${t("titre")}` };
}

/**
 * Proposer une modification de la fiche d'un document (US-02.2) : réservé à l'administrateur ;
 * une seule proposition à la fois (sinon retour à la fiche, qui montre celle en attente).
 */
export default async function ModifierFiche({ params }: Props) {
  const { code } = await params;
  if (!/^[A-Z]{3}$/.test(code)) notFound();
  const moi = await chargerMoi();
  if (!moi.interne || !moi.permissions.includes("PARAMETRER")) notFound();
  const t = await getTranslations("catalogue.formulaire");

  const adresse = `/catalogue/${code}/modifier`;
  let fiche;
  try {
    fiche = (await appelPage(adresse, async () => lireFicheDocument(code, await optionsApi())))
      .data;
  } catch (e) {
    if (e instanceof ErreurApi && e.code === "SYS-002") notFound();
    throw e;
  }
  if (!fiche.peutProposer) redirect(`/catalogue/${code}`);
  const references = (
    await appelPage(adresse, async () => referencesFichesDocuments(await optionsApi()))
  ).data;

  return (
    <div className="flex flex-col gap-6">
      <EnTetePage
        retour={
          <Link
            href={`/catalogue/${code}`}
            className="inline-flex min-h-11 items-center gap-2 text-royal underline-offset-4 hover:underline"
          >
            <ArrowLeft aria-hidden="true" className="size-4" />
            {t("retour", { code })}
          </Link>
        }
        titre={t("titre")}
        description={`${fiche.code} · ${fiche.enVigueur.libelleFr} — ${t("versionActuelle", {
          numero: fiche.enVigueur.numero,
        })}`}
      />
      <FormulaireFiche
        code={fiche.code}
        enVigueur={fiche.enVigueur}
        tarifsAVenir={fiche.tarifsAVenir}
        aujourdhui={dateDuJour()}
        signataires={references.signataires}
        profilsAvis={references.profilsAvis}
        proposer={proposerFiche}
      />
    </div>
  );
}
