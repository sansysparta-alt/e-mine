import { listerDemandes } from "@emine/api-client";
import { Bouton, ChampRecherche, EnTetePage, EtatVide, FiltresPastilles } from "@emine/ui";
import type { Metadata } from "next";
import Link from "next/link";
import { getTranslations } from "next-intl/server";
import {
  lireParametres,
  type Parametres,
  STATUTS,
  TRIS,
} from "@/composants/demande/parametres-liste";
import { TableauDemandes } from "@/composants/demande/tableau-demandes";
import { PaginationListe } from "@/composants/liste/pagination-liste";
import { appelPage, chargerMoi, chargerReferentiels, optionsApi } from "@/serveur/donnees";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("mesDemandes");
  return { title: t("titre") };
}

type Props = { searchParams: Promise<Parametres> };

/**
 * « Mes demandes » (US-03.5) sur le gabarit : recherche, filtres en pastilles, tableau trié,
 * pagination côté API (sans limite fixe), état vide.
 */
export default async function MesDemandes({ searchParams }: Props) {
  const p = lireParametres(await searchParams);
  const t = await getTranslations("mesDemandes");
  const tStatut = await getTranslations("statuts");
  const [moi, { documents }, reponse] = await Promise.all([
    chargerMoi(),
    chargerReferentiels(),
    appelPage("/demandes", async () =>
      listerDemandes(
        {
          statut: p.statut,
          q: p.q,
          tri: p.tri,
          sens: p.sens,
          page: p.page,
          taille: p.taille,
        },
        await optionsApi(),
      ),
    ),
  ]);
  const resultat = reponse.data;
  const libelles = Object.fromEntries(documents.map((d) => [d.code, d.libelleFr]));
  const titre = moi.interne ? t("titreInterne") : t("titre");
  const nouvelle = moi.permissions.includes("DEPOSER") ? (
    <Bouton asChild>
      <Link href="/demandes/nouvelle">{t("nouvelle")}</Link>
    </Bouton>
  ) : null;

  /** Adresse de la liste avec ces paramètres (les autres sont conservés). */
  const adresse = (changes: Partial<Record<keyof Parametres, string | undefined>>) => {
    const q = new URLSearchParams();
    const tout = {
      statut: p.statut,
      q: p.q,
      tri: p.tri === "date" ? undefined : p.tri,
      sens: p.sens,
      taille: String(p.taille),
      ...changes,
    };
    for (const [cle, valeur] of Object.entries(tout)) if (valeur) q.set(cle, valeur);
    const chaine = q.toString();
    return chaine ? `/demandes?${chaine}` : "/demandes";
  };
  const conserves = { statut: p.statut, q: p.q, tri: p.tri, sens: p.sens };

  return (
    <div className="flex flex-col gap-6">
      <EnTetePage titre={titre} description={t("description")} actions={nouvelle} />
      <ChampRecherche
        libelle={t("recherche.libelle")}
        valeur={p.q}
        placeholder={t("recherche.exemple")}
        boutonLibelle={t("recherche.bouton")}
        conserver={{ statut: p.statut, tri: p.tri, sens: p.sens, taille: String(p.taille) }}
      />
      <FiltresPastilles
        libelle={t("filtreStatut")}
        options={[
          {
            valeur: "",
            libelle: t("tous"),
            href: adresse({ statut: undefined }),
            actif: !p.statut,
          },
          ...STATUTS.map((s) => ({
            valeur: s,
            libelle: tStatut(s),
            href: adresse({ statut: s }),
            actif: p.statut === s,
          })),
        ]}
      />
      {resultat.total === 0 ? (
        <EtatVide
          titre={p.statut || p.q ? t("aucuneFiltre") : t("aucune")}
          action={p.statut || p.q ? undefined : nouvelle}
        />
      ) : (
        <div className="flex flex-col gap-4">
          <TableauDemandes
            demandes={resultat.elements}
            libelles={libelles}
            legende={titre}
            triables={[...TRIS]}
            tri={{ colonne: p.tri, sens: p.sens }}
            lienTri={(colonne) =>
              adresse({
                tri: colonne,
                sens:
                  p.tri === colonne
                    ? p.sens === "asc"
                      ? "desc"
                      : "asc"
                    : colonne === "date"
                      ? "desc"
                      : "asc",
              })
            }
          />
          <PaginationListe
            chemin="/demandes"
            parametres={conserves}
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
