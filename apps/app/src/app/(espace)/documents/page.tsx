import { type LigneDocumentDto, listerMesDocuments } from "@emine/api-client";
import {
  BadgeStatut,
  type ColonneTableau,
  type ComposantLien,
  EnTetePage,
  EtatVide,
  formaterDate,
  TableauDonnees,
  tailleAutorisee,
} from "@emine/ui";
import { Download, ExternalLink, FileCheck2 } from "@emine/ui/icones";
import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { getLocale, getTranslations } from "next-intl/server";
import { PaginationListe } from "@/composants/liste/pagination-liste";
import { libelleDocument } from "@/composants/referentiels";
import { appelPage, chargerMoi, chargerReferentiels, optionsApi } from "@/serveur/donnees";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("mesDocuments");
  return { title: t("titre") };
}

const LienNext: ComposantLien = ({ href, children, ...props }) => (
  <Link href={href} {...props}>
    {children}
  </Link>
);

const classesAction =
  "inline-flex min-h-11 items-center gap-1 rounded-lg px-2 text-sm font-semibold text-royal hover:bg-ice";

type Props = { searchParams: Promise<{ page?: string; taille?: string }> };

/**
 * « Documents délivrés » (espace de l'usager, cahier des charges) : ses documents signés, leur
 * validité (un document révoqué est signalé), le PDF à télécharger et la page publique de
 * vérification ; la ligne ouvre la demande.
 */
export default async function MesDocuments({ searchParams }: Props) {
  const p = await searchParams;
  const moi = await chargerMoi();
  if (moi.interne || !moi.permissions.includes("DEPOSER")) notFound();
  const t = await getTranslations("mesDocuments");
  const page = Math.max(1, Number.parseInt(p.page ?? "1", 10) || 1);
  const taille = tailleAutorisee(Number.parseInt(p.taille ?? "", 10));
  const [resultat, { documents }, langue] = await Promise.all([
    appelPage("/documents", async () =>
      listerMesDocuments({ page, taille }, await optionsApi()),
    ).then((r) => r.data),
    chargerReferentiels(),
    getLocale(),
  ]);
  const colonnes: ColonneTableau<LigneDocumentDto>[] = [
    {
      cle: "document",
      entete: t("colonnes.document"),
      rendu: (d) => (
        <span className="flex flex-col">
          <span className="font-semibold text-navy tabular-nums">{d.numero}</span>
          <span className="text-sm text-texte-doux">
            {libelleDocument(
              documents.find((x) => x.code === d.codeType),
              langue,
              d.codeType,
            )}
          </span>
        </span>
      ),
    },
    {
      cle: "validite",
      entete: t("colonnes.validite"),
      rendu: (d) => (
        <BadgeStatut ton={d.valide ? "succes" : "danger"}>
          {d.valide ? t("valide") : t("revoque")}
        </BadgeStatut>
      ),
    },
    {
      cle: "demande",
      entete: t("colonnes.demande"),
      rendu: (d) => <span className="tabular-nums">{d.numeroDemande}</span>,
    },
    { cle: "date", entete: t("colonnes.date"), rendu: (d) => formaterDate(d.delivreLe) },
    {
      cle: "actions",
      entete: t("colonnes.actions"),
      interactive: true,
      rendu: (d) => (
        <span className="flex flex-wrap items-center gap-1">
          <a
            href={`/api/demandes/${d.demandeId}/document/contenu`}
            download
            className={classesAction}
          >
            <Download aria-hidden="true" className="size-4" />
            {t("telecharger")}
          </a>
          <a href={d.urlVerification} target="_blank" rel="noopener" className={classesAction}>
            <ExternalLink aria-hidden="true" className="size-4" />
            {t("verifier")}
            <span className="sr-only">{t("nouvelOnglet")}</span>
          </a>
        </span>
      ),
    },
  ];

  return (
    <div className="flex flex-col gap-6">
      <EnTetePage titre={t("titre")} description={t("description")} />
      {resultat.total === 0 ? (
        <EtatVide icone={FileCheck2} titre={t("vide.titre")} description={t("vide.description")} />
      ) : (
        <div className="flex flex-col gap-4">
          <TableauDonnees
            legende={t("titre")}
            lignes={resultat.elements}
            cleLigne={(d) => d.numero}
            Lien={LienNext}
            lienLigne={(d) => `/demandes/${d.demandeId}`}
            colonnes={colonnes}
          />
          <PaginationListe
            chemin="/documents"
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
