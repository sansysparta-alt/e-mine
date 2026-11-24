import { listerMesPaiements } from "@emine/api-client";
import { EnTetePage, EtatVide, tailleAutorisee } from "@emine/ui";
import { CreditCard } from "@emine/ui/icones";
import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getLocale, getTranslations } from "next-intl/server";
import { PaginationListe } from "@/composants/liste/pagination-liste";
import { TableauPaiements } from "@/composants/paiement/tableau-paiements";
import { libelleDocument } from "@/composants/referentiels";
import { appelPage, chargerMoi, chargerReferentiels, optionsApi } from "@/serveur/donnees";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("mesPaiements");
  return { title: t("titre") };
}

type Props = { searchParams: Promise<{ page?: string; taille?: string }> };

/**
 * « Paiements & reçus » (espace de l'usager, cahier des charges) : tous ses paiements, du plus
 * récent au plus ancien, avec la demande concernée et le reçu à télécharger ; la ligne ouvre le
 * détail du paiement.
 */
export default async function MesPaiements({ searchParams }: Props) {
  const p = await searchParams;
  const moi = await chargerMoi();
  if (moi.interne || !moi.permissions.includes("PAYER")) notFound();
  const t = await getTranslations("mesPaiements");
  const page = Math.max(1, Number.parseInt(p.page ?? "1", 10) || 1);
  const taille = tailleAutorisee(Number.parseInt(p.taille ?? "", 10));
  const [resultat, { documents }, langue] = await Promise.all([
    appelPage("/paiements", async () =>
      listerMesPaiements({ page, taille }, await optionsApi()),
    ).then((r) => r.data),
    chargerReferentiels(),
    getLocale(),
  ]);
  const demandes = new Map(
    resultat.elements.map((l) => [
      l.paiement.reference,
      {
        numero: l.numeroDemande ?? "",
        document: libelleDocument(
          documents.find((d) => d.code === l.codeDocument),
          langue,
          l.codeDocument,
        ),
      },
    ]),
  );

  return (
    <div className="flex flex-col gap-6">
      <EnTetePage titre={t("titre")} description={t("description")} />
      {resultat.total === 0 ? (
        <EtatVide icone={CreditCard} titre={t("vide.titre")} description={t("vide.description")} />
      ) : (
        <div className="flex flex-col gap-4">
          <TableauPaiements
            legende={t("titre")}
            paiements={resultat.elements.map((l) => l.paiement)}
            demande={(paiement) => demandes.get(paiement.reference) ?? null}
          />
          <PaginationListe
            chemin="/paiements"
            parametres={{}}
            page={resultat.page}
            taille={resultat.taille}
            total={resultat.total}
            elements={t("elements")}
          />
        </div>
      )}
    </div>
  );
}
