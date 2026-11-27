import { listerAVerifier } from "@emine/api-client";
import { Bouton, EnTetePage, EtatVide, tailleAutorisee } from "@emine/ui";
import { CircleCheck, FileSpreadsheet, Hourglass } from "@emine/ui/icones";
import Link from "next/link";
import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { TableauAVerifier } from "@/composants/encaissement/tableau-a-verifier";
import { PaginationListe } from "@/composants/liste/pagination-liste";
import { appelPage, chargerMoi, optionsApi } from "@/serveur/donnees";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("aVerifier");
  return { title: t("titre") };
}

type Props = { searchParams: Promise<{ page?: string; taille?: string }> };

/**
 * « À vérifier » du Responsable fiscal (US-04.9) : ce que le rapprochement automatique n'a pas
 * confirmé (écarts de montant, lignes sans référence, doublons, versements tardifs, retours) et
 * les paiements en ligne au montant divergent ; une décision motivée par élément.
 */
export default async function AVerifier({ searchParams }: Props) {
  const p = await searchParams;
  const moi = await chargerMoi();
  if (!moi.interne || !moi.permissions.includes("ENCAISSER")) notFound();
  const t = await getTranslations("aVerifier");
  const page = Math.max(1, Number.parseInt(p.page ?? "1", 10) || 1);
  const taille = tailleAutorisee(Number.parseInt(p.taille ?? "", 10));
  const resultat = (
    await appelPage("/a-verifier", async () =>
      listerAVerifier({ page, taille }, await optionsApi()),
    )
  ).data;

  return (
    <div className="flex flex-col gap-6">
      <EnTetePage titre={t("titre")} description={t("description", { nombre: resultat.total })} />
      {resultat.total === 0 && resultat.virementsAttendus > 0 ? (
        // R3 : rien à décider, mais des virements déclarés attendent le prochain relevé.
        <EtatVide
          icone={Hourglass}
          titre={t("videVirements.titre")}
          description={t("videVirements.description", { nombre: resultat.virementsAttendus })}
          action={
            <Bouton asChild>
              <Link href="/releves">
                <FileSpreadsheet aria-hidden="true" className="size-4" />
                {t("videVirements.lien")}
              </Link>
            </Bouton>
          }
        />
      ) : resultat.total === 0 ? (
        <EtatVide icone={CircleCheck} titre={t("vide.titre")} description={t("vide.description")} />
      ) : (
        <div className="flex flex-col gap-4">
          {resultat.virementsAttendus > 0 ? (
            <p className="text-texte-doux">
              {t("virementsAttendus", { nombre: resultat.virementsAttendus })}{" "}
              <Link href="/releves" className="font-semibold text-royal underline">
                {t("videVirements.lien")}
              </Link>
            </p>
          ) : null}
          <TableauAVerifier elements={resultat.elements} />
          <PaginationListe
            chemin="/a-verifier"
            parametres={{}}
            page={resultat.page}
            taille={resultat.taille}
            total={resultat.total}
            elements={{ un: t("element"), plusieurs: t("elements") }}
          />
        </div>
      )}
    </div>
  );
}
