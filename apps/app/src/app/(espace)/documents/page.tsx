import { type LigneDocumentDto, listerMesDocuments } from "@emine/api-client";
import {
  BadgeStatut,
  Bouton,
  type ColonneTableau,
  type ComposantLien,
  EnTetePage,
  EtatVide,
  FiltresPastilles,
  formaterDate,
  TableauDonnees,
  TexteSurligne,
  tailleAutorisee,
} from "@emine/ui";
import { ExternalLink, FileCheck2 } from "@emine/ui/icones";
import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { getLocale, getTranslations } from "next-intl/server";
import { TelechargerDocument } from "@/composants/delivrance/telecharger-document";
import { PaginationListe } from "@/composants/liste/pagination-liste";
import { RechercheListe } from "@/composants/liste/recherche-liste";
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

const VALIDITES = ["valides", "revoques"] as const;
type Validite = (typeof VALIDITES)[number];

type Props = {
  searchParams: Promise<{ page?: string; taille?: string; q?: string; validite?: string }>;
};

/**
 * « Documents délivrés » (espace de l'usager, cahier des charges) : ses documents signés, leur
 * validité (un document révoqué porte sa date et son motif, et le téléchargement avertit), le PDF
 * et la page publique de vérification ; recherche, filtre par validité avec ses compteurs (donnés
 * par l'API, ils suivent la recherche) ; la ligne ouvre la demande.
 */
export default async function MesDocuments({ searchParams }: Props) {
  const p = await searchParams;
  const moi = await chargerMoi();
  if (moi.interne || !moi.permissions.includes("DEPOSER")) notFound();
  const t = await getTranslations("mesDocuments");
  const page = Math.max(1, Number.parseInt(p.page ?? "1", 10) || 1);
  const taille = tailleAutorisee(Number.parseInt(p.taille ?? "", 10));
  const validite = VALIDITES.find((v) => v === p.validite);
  const q = p.q?.trim() || undefined;
  const [resultat, { documents }, langue] = await Promise.all([
    appelPage("/documents", async () =>
      listerMesDocuments({ page, taille, recherche: q, validite }, await optionsApi()),
    ).then((r) => r.data),
    chargerReferentiels(),
    getLocale(),
  ]);
  /** Autorité de la révocation, dans la langue de l'écran. */
  const autorite = (d: LigneDocumentDto) =>
    (langue === "en" ? d.revoqueAutoriteEn : null) ?? d.revoqueAutorite ?? undefined;
  const colonnes: ColonneTableau<LigneDocumentDto>[] = [
    {
      cle: "document",
      entete: t("colonnes.document"),
      rendu: (d) => (
        <span className="flex flex-col">
          <span className="font-semibold text-navy tabular-nums">
            <TexteSurligne texte={d.numero} recherche={q ?? ""} />
          </span>
          <span className="text-sm text-texte-doux">
            <TexteSurligne
              texte={libelleDocument(
                documents.find((x) => x.code === d.codeType),
                langue,
                d.codeType,
              )}
              recherche={q ?? ""}
            />
          </span>
        </span>
      ),
    },
    {
      cle: "validite",
      entete: t("colonnes.validite"),
      rendu: (d) =>
        d.valide ? (
          <BadgeStatut ton="succes">{t("valide")}</BadgeStatut>
        ) : (
          <span className="flex max-w-72 flex-col items-start gap-1">
            <BadgeStatut ton="danger">{t("revoque")}</BadgeStatut>
            {d.revoqueLe ? (
              <span className="text-sm text-texte-doux">
                {t("revoqueLe", { date: formaterDate(d.revoqueLe) })}
              </span>
            ) : null}
            {autorite(d) ? (
              <span className="text-sm">{t("revoquePar", { autorite: autorite(d) ?? "" })}</span>
            ) : null}
            {d.motifRevocation ? (
              <span className="line-clamp-3 text-sm">
                {t("motif", { motif: d.motifRevocation })}
              </span>
            ) : null}
          </span>
        ),
    },
    {
      cle: "demande",
      entete: t("colonnes.demande"),
      rendu: (d) => (
        <span className="tabular-nums">
          <TexteSurligne texte={d.numeroDemande ?? ""} recherche={q ?? ""} />
        </span>
      ),
    },
    { cle: "date", entete: t("colonnes.date"), rendu: (d) => formaterDate(d.delivreLe) },
    {
      cle: "actions",
      entete: t("colonnes.actions"),
      interactive: true,
      rendu: (d) => (
        <span className="flex flex-wrap items-center gap-1">
          <TelechargerDocument
            demandeId={d.demandeId}
            numero={d.numero}
            revocation={
              d.valide ? null : { le: d.revoqueLe, autorite: autorite(d), motif: d.motifRevocation }
            }
          />
          <a href={d.urlVerification} target="_blank" rel="noopener" className={classesAction}>
            <ExternalLink aria-hidden="true" className="size-4" />
            {t("verifier")}
            <span className="sr-only">{t("nouvelOnglet")}</span>
          </a>
        </span>
      ),
    },
  ];

  const adresse = (changes: { validite?: Validite }) => {
    const params = new URLSearchParams();
    if (q) params.set("q", q);
    if (changes.validite) params.set("validite", changes.validite);
    if (p.taille) params.set("taille", String(taille));
    const chaine = params.toString();
    return chaine ? `/documents?${chaine}` : "/documents";
  };
  const c = resultat.compteurs;
  const aucunDocument = c.tous === 0 && !q;

  return (
    <div className="flex flex-col gap-6">
      <EnTetePage titre={t("titre")} description={t("description")} />
      {aucunDocument ? (
        <EtatVide icone={FileCheck2} titre={t("vide.titre")} description={t("vide.description")} />
      ) : (
        <>
          <RechercheListe
            libelle={t("recherche.libelle")}
            valeur={q}
            placeholder={t("recherche.exemple")}
            suggestions={documents.map((d) => libelleDocument(d, langue))}
            boutonLibelle={t("recherche.bouton")}
            conserver={{ validite, taille: p.taille ? String(taille) : undefined }}
          />
          <FiltresPastilles
            libelle={t("filtres.libelle")}
            options={[
              {
                valeur: "",
                libelle: t("filtres.tous"),
                href: adresse({}),
                actif: !validite,
                nombre: c.tous,
              },
              ...VALIDITES.map((v) => ({
                valeur: v,
                libelle: t(`filtres.${v}`),
                href: adresse({ validite: v }),
                actif: validite === v,
                nombre: v === "valides" ? c.valides : c.revoques,
              })),
            ]}
          />
          {resultat.total === 0 ? (
            <EtatVide
              titre={q ? t("aucunResultat.titre", { texte: q }) : t("aucunResultat.filtre")}
              description={q ? t("aucunResultat.conseil") : undefined}
              action={
                <Bouton asChild variante="secondaire">
                  <Link href="/documents">{t("aucunResultat.effacer")}</Link>
                </Bouton>
              }
            />
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
                parametres={{ q, validite }}
                page={resultat.page}
                taille={resultat.taille}
                total={resultat.total}
                elements={t("elements")}
              />
            </div>
          )}
        </>
      )}
    </div>
  );
}
