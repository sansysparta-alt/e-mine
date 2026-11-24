import { listerFichesDocuments } from "@emine/api-client";
import { EnTetePage, EtatVide, FiltresPastilles } from "@emine/ui";
import { BookOpen } from "@emine/ui/icones";
import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import {
  FILTRES_CATALOGUE,
  type FiltreCatalogue,
  lireParametresCatalogue,
} from "@/composants/catalogue/presentation";
import { TableauFiches } from "@/composants/catalogue/tableau-fiches";
import { libelleWorkflow } from "@/composants/referentiels";
import { PaginationListe } from "@/composants/liste/pagination-liste";
import { PlusDeFiltres } from "@/composants/liste/plus-de-filtres";
import { RechercheListe } from "@/composants/liste/recherche-liste";
import { appelPage, chargerMoi, optionsApi } from "@/serveur/donnees";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("catalogue");
  return { title: t("titre") };
}

type Props = { searchParams: Promise<Record<string, string | string[] | undefined>> };

/**
 * Catalogue et tarifs (US-02.2, cahier des charges diapo 32) : les fiches des 15 documents, leur
 * version en vigueur et les modifications en attente d'approbation. L'administrateur propose, le
 * Ministre approuve ; chacun voit ce qui le concerne.
 */
export default async function Catalogue({ searchParams }: Props) {
  const p = lireParametresCatalogue(await searchParams);
  const moi = await chargerMoi();
  const ministre = moi.permissions.includes("VALIDER_CATALOGUE");
  if (!moi.interne || (!ministre && !moi.permissions.includes("PARAMETRER"))) notFound();
  const t = await getTranslations("catalogue");
  const tReferentiels = await getTranslations("referentiels");
  const profil = ministre ? "ministre" : "admin";

  const { data } = await appelPage("/catalogue", async () =>
    listerFichesDocuments(
      {
        filtre: p.filtre === "TOUS" ? undefined : p.filtre,
        famille: p.famille,
        q: p.q,
        page: p.page,
        taille: p.taille,
      },
      await optionsApi(),
    ),
  );
  const compteurs = data.compteurs;
  const filtreEtat = p.filtre === "TOUS" ? undefined : p.filtre;
  /** Adresse de la liste : chaque groupe de filtres garde l'autre et la recherche. */
  const adresse = ({
    filtre = p.filtre,
    famille = p.famille,
  }: {
    filtre?: FiltreCatalogue;
    famille?: string | null;
  }) => {
    const q = new URLSearchParams();
    if (filtre !== "TOUS") q.set("filtre", filtre);
    if (famille) q.set("famille", famille);
    if (p.q) q.set("q", p.q);
    const chaine = q.toString();
    return chaine ? `/catalogue?${chaine}` : "/catalogue";
  };
  const libelleFamille = (code: string) =>
    libelleWorkflow(
      code,
      data.familles.find((f) => f.code === code)?.libelle ?? code,
      tReferentiels,
    );

  return (
    <div className="flex flex-col gap-6">
      <EnTetePage titre={t("titre")} description={t(`description.${profil}`)} />

      <RechercheListe
        libelle={t("recherche.libelle")}
        valeur={p.q}
        placeholder={t("recherche.exemple")}
        boutonLibelle={t("recherche.bouton")}
        conserver={{ filtre: filtreEtat, famille: p.famille }}
      />

      <FiltresPastilles
        libelle={t("filtres.libelle")}
        options={FILTRES_CATALOGUE.map((f) => ({
          valeur: f,
          libelle: t(`filtres.${f === "A_VALIDER" ? `A_VALIDER_${profil}` : f}`),
          href: adresse({ filtre: f }),
          actif: p.filtre === f,
          nombre: compteurs[f] ?? 0,
        }))}
        fin={
          <PlusDeFiltres
            libelle={t("filtres.famille")}
            titre={t("filtres.famille")}
            options={data.familles.map((f) => ({
              valeur: f.code,
              libelle: libelleFamille(f.code),
              href: adresse({ famille: f.code }),
              actif: p.famille === f.code,
              nombre: f.nombre,
            }))}
            actif={
              p.famille
                ? {
                    libelle: libelleFamille(p.famille),
                    effacer: adresse({ famille: null }),
                    libelleEffacer: t("filtres.toutesFamilles"),
                  }
                : undefined
            }
          />
        }
      />

      {data.total === 0 ? (
        <EtatVide
          icone={BookOpen}
          titre={
            p.q || (p.famille && p.filtre === "TOUS")
              ? t("vide.recherche")
              : p.filtre === "A_VALIDER"
                ? t(`vide.A_VALIDER_${profil}`)
                : t("vide.INACTIFS")
          }
          action={
            p.q || p.filtre !== "TOUS" || p.famille ? (
              <Link
                href="/catalogue"
                className="inline-flex min-h-11 items-center text-royal underline-offset-4 hover:underline"
              >
                {t("vide.toutVoir")}
              </Link>
            ) : undefined
          }
        />
      ) : (
        <div className="flex flex-col gap-4">
          <TableauFiches lignes={data.elements} legende={t("titre")} recherche={p.q} />
          <PaginationListe
            chemin="/catalogue"
            parametres={{ filtre: filtreEtat, famille: p.famille, q: p.q }}
            page={data.page}
            taille={data.taille}
            total={data.total}
            elements={{ un: t("element"), plusieurs: t("elements") }}
          />
        </div>
      )}
    </div>
  );
}
