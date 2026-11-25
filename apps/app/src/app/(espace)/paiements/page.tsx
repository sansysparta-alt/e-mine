import { ErreurApi, listerMesPaiements, type PageMesPaiementsDto } from "@emine/api-client";
import {
  Alerte,
  Bouton,
  EnTetePage,
  EtatVide,
  FiltresPastilles,
  formaterGnf,
  tailleAutorisee,
} from "@emine/ui";
import { CreditCard } from "@emine/ui/icones";
import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { getLocale, getTranslations } from "next-intl/server";
import { FiltrePeriode } from "@/composants/liste/filtre-periode";
import { PaginationListe } from "@/composants/liste/pagination-liste";
import { PlusDeFiltres } from "@/composants/liste/plus-de-filtres";
import { RechercheListe } from "@/composants/liste/recherche-liste";
import { TableauPaiements } from "@/composants/paiement/tableau-paiements";
import { libelleDocument } from "@/composants/referentiels";
import { appelPage, chargerMoi, chargerReferentiels, optionsApi } from "@/serveur/donnees";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("mesPaiements");
  return { title: t("titre") };
}

const STATUTS = ["payes", "en-cours", "echoues", "rembourses"] as const;
type Statut = (typeof STATUTS)[number];
const OBJETS = ["FRAIS_DOSSIER", "DROITS", "ATTESTATION", "FORMATION"] as const;
const DATE = /^\d{4}-\d{2}-\d{2}$/;

type Parametres = {
  page?: string;
  taille?: string;
  q?: string;
  statut?: string;
  objet?: string;
  du?: string;
  au?: string;
};
type Props = { searchParams: Promise<Parametres> };

/** Clé du compteur de l'API pour un filtre de statut. */
const COMPTEUR: Record<Statut, "payes" | "enCours" | "echoues" | "rembourses"> = {
  payes: "payes",
  "en-cours": "enCours",
  echoues: "echoues",
  rembourses: "rembourses",
};

/**
 * « Paiements & reçus » (espace de l'usager, cahier des charges) : tous ses paiements, du plus
 * récent au plus ancien ; recherche pendant la saisie, filtres par statut (avec leur nombre), par
 * objet et par période, total payé pour les critères choisis (calculé par l'API). Le numéro de la
 * demande ouvre le dossier, le reçu se télécharge, la ligne ouvre le détail du paiement.
 */
export default async function MesPaiements({ searchParams }: Props) {
  const p = await searchParams;
  const moi = await chargerMoi();
  if (moi.interne || !moi.permissions.includes("PAYER")) notFound();
  const t = await getTranslations("mesPaiements");
  const tObjet = await getTranslations("paiement.objet");
  const page = Math.max(1, Number.parseInt(p.page ?? "1", 10) || 1);
  const taille = tailleAutorisee(Number.parseInt(p.taille ?? "", 10));
  const q = p.q?.trim() || undefined;
  const statut = STATUTS.find((s) => s === p.statut);
  const objet = OBJETS.find((o) => o === p.objet);
  const du = p.du && DATE.test(p.du) ? p.du : undefined;
  const au = p.au && DATE.test(p.au) ? p.au : undefined;
  const periodeInversee = Boolean(du && au && au < du);

  const [resultat, { documents }, langue] = await Promise.all([
    periodeInversee
      ? Promise.resolve(null)
      : appelPage("/paiements", async () =>
          listerMesPaiements(
            { page, taille, recherche: q, statut, objet, du, au },
            await optionsApi(),
          ),
        )
          .then((r): PageMesPaiementsDto | null => r.data)
          .catch((e: unknown) => {
            if (e instanceof ErreurApi && e.code === "VAL-001") return null;
            throw e;
          }),
    chargerReferentiels(),
    getLocale(),
  ]);

  const conserves = {
    q,
    statut,
    objet,
    du,
    au,
    taille: p.taille ? String(taille) : undefined,
  };
  const adresse = (changes: Partial<typeof conserves>) => {
    const params = new URLSearchParams();
    for (const [cle, valeur] of Object.entries({ ...conserves, ...changes })) {
      if (valeur) params.set(cle, valeur);
    }
    const chaine = params.toString();
    return chaine ? `/paiements?${chaine}` : "/paiements";
  };
  const filtre = Boolean(q || statut || objet || du || au);
  const c = resultat?.compteurs;
  const aucunPaiement = !filtre && resultat?.total === 0;

  if (aucunPaiement) {
    return (
      <div className="flex flex-col gap-6">
        <EnTetePage titre={t("titre")} description={t("description")} />
        <EtatVide icone={CreditCard} titre={t("vide.titre")} description={t("vide.description")} />
      </div>
    );
  }

  const demandes = new Map(
    (resultat?.elements ?? []).map((l) => [
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
      <RechercheListe
        libelle={t("recherche.libelle")}
        valeur={q}
        placeholder={t("recherche.exemple")}
        suggestions={documents.map((d) => libelleDocument(d, langue))}
        boutonLibelle={t("recherche.bouton")}
        conserver={{ statut, objet, du, au, taille: conserves.taille }}
      />
      <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <FiltrePeriode du={du} au={au} conserver={{ q, statut, objet, taille: conserves.taille }} />
        {c ? (
          <p className="flex flex-col lg:items-end">
            <span className="text-lg font-semibold text-navy tabular-nums">
              {t("total", { montant: formaterGnf(c.totalPayeGnf, langue) })}
            </span>
            <span className="text-sm text-texte-doux">{t("totalDetail", { nombre: c.payes })}</span>
          </p>
        ) : null}
      </div>
      {periodeInversee || !resultat || !c ? (
        <Alerte nature="erreur" titre={t("aucunResultat.titre")}>
          {t("aucunResultat.conseil")}
        </Alerte>
      ) : (
        <>
          <FiltresPastilles
            libelle={t("filtres.statut")}
            options={[
              {
                valeur: "",
                libelle: t("filtres.tous"),
                href: adresse({ statut: undefined }),
                actif: !statut,
                nombre: c.tous,
              },
              ...STATUTS.map((s) => ({
                valeur: s,
                libelle: t(`filtres.${COMPTEUR[s]}`),
                href: adresse({ statut: s }),
                actif: statut === s,
                nombre: c[COMPTEUR[s]],
              })),
            ]}
            fin={
              <PlusDeFiltres
                libelle={t("filtres.objet")}
                titre={t("filtres.objet")}
                options={OBJETS.map((o) => ({
                  valeur: o,
                  libelle: tObjet.has(o) ? tObjet(o) : o,
                  href: adresse({ objet: o }),
                  actif: objet === o,
                }))}
                actif={
                  objet
                    ? {
                        libelle: tObjet.has(objet) ? tObjet(objet) : objet,
                        effacer: adresse({ objet: undefined }),
                        libelleEffacer: t("filtres.tousObjets"),
                      }
                    : undefined
                }
              />
            }
          />
          {resultat.total === 0 ? (
            <EtatVide
              icone={CreditCard}
              titre={t("aucunResultat.titre")}
              description={t("aucunResultat.conseil")}
              action={
                <Bouton asChild variante="secondaire">
                  <Link href="/paiements">{t("aucunResultat.effacer")}</Link>
                </Bouton>
              }
            />
          ) : (
            <div className="flex flex-col gap-4">
              <TableauPaiements
                legende={t("titre")}
                paiements={resultat.elements.map((l) => l.paiement)}
                demande={(paiement) => demandes.get(paiement.reference) ?? null}
                recherche={q}
              />
              <PaginationListe
                chemin="/paiements"
                parametres={{ q, statut, objet, du, au }}
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
