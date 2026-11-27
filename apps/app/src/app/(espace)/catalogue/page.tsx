import { listerFichesDocuments } from "@emine/api-client";
import { CarteChiffre, EnTetePage, EtatVide, FiltresPastilles, GrilleChiffres } from "@emine/ui";
import { BookOpen, FilePen } from "@emine/ui/icones";
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
import { PaginationListe } from "@/composants/liste/pagination-liste";
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
  const profil = ministre ? "ministre" : "admin";

  const { data } = await appelPage("/catalogue", async () =>
    listerFichesDocuments(
      {
        filtre: p.filtre === "TOUS" ? undefined : p.filtre,
        q: p.q,
        page: p.page,
        taille: p.taille,
      },
      await optionsApi(),
    ),
  );
  const compteurs = data.compteurs;
  const adresse = (filtre: FiltreCatalogue) => {
    const q = new URLSearchParams();
    if (filtre !== "TOUS") q.set("filtre", filtre);
    if (p.q) q.set("q", p.q);
    const chaine = q.toString();
    return chaine ? `/catalogue?${chaine}` : "/catalogue";
  };

  return (
    <div className="flex flex-col gap-6">
      <EnTetePage titre={t("titre")} description={t(`description.${profil}`)} />

      <GrilleChiffres colonnes={3}>
        <CarteChiffre
          libelle={t("chiffres.documents")}
          periode={t("chiffres.periode")}
          valeur={(compteurs["TOUS"] ?? 0) - (compteurs["INACTIFS"] ?? 0)}
          icone={BookOpen}
          href="/catalogue"
        />
        <CarteChiffre
          libelle={t(`chiffres.propositions.${profil}`)}
          periode={t("chiffres.periode")}
          valeur={compteurs["A_VALIDER"] ?? 0}
          icone={FilePen}
          href={adresse("A_VALIDER")}
        />
        <CarteChiffre
          libelle={t("chiffres.inactifs")}
          periode={t("chiffres.periode")}
          valeur={compteurs["INACTIFS"] ?? 0}
          href={adresse("INACTIFS")}
        />
      </GrilleChiffres>

      <RechercheListe
        libelle={t("recherche.libelle")}
        valeur={p.q}
        placeholder={t("recherche.exemple")}
        boutonLibelle={t("recherche.bouton")}
        conserver={{ filtre: p.filtre === "TOUS" ? undefined : p.filtre }}
      />

      <FiltresPastilles
        libelle={t("filtres.libelle")}
        options={FILTRES_CATALOGUE.map((f) => ({
          valeur: f,
          libelle: t(`filtres.${f === "A_VALIDER" ? `A_VALIDER_${profil}` : f}`),
          href: adresse(f),
          actif: p.filtre === f,
          nombre: compteurs[f] ?? 0,
        }))}
      />

      {data.total === 0 ? (
        <EtatVide
          icone={BookOpen}
          titre={
            p.q
              ? t("vide.recherche")
              : p.filtre === "A_VALIDER"
                ? t(`vide.A_VALIDER_${profil}`)
                : t("vide.INACTIFS")
          }
          action={
            p.q || p.filtre !== "TOUS" ? (
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
            parametres={{ filtre: p.filtre === "TOUS" ? undefined : p.filtre, q: p.q }}
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
