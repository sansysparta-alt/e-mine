import { compterDemandes, compterVues, listerDemandes } from "@emine/api-client";
import { Bouton, EnTetePage, EtatVide, FiltresPastilles } from "@emine/ui";
import type { Metadata } from "next";
import Link from "next/link";
import { getTranslations } from "next-intl/server";
import { cleLibelleVue, statutsVisibles } from "@/composants/demande/filtres";
import { lireParametres, type Parametres, TRIS } from "@/composants/demande/parametres-liste";
import { TableauDemandes } from "@/composants/demande/tableau-demandes";
import { type FiltreActif, FiltresActifs } from "@/composants/liste/filtres-actifs";
import { PaginationListe } from "@/composants/liste/pagination-liste";
import { PlusDeFiltres } from "@/composants/liste/plus-de-filtres";
import { RechercheListe } from "@/composants/liste/recherche-liste";
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
  const moi = await chargerMoi();
  // Vues exclusives du profil, calculées par l'API (règle « À traiter » unique) : la somme des
  // pastilles est exactement « Toutes ».
  const vues = (await appelPage("/demandes", async () => compterVues(await optionsApi()))).data;
  const vue = !p.statut && vues.vues.some((v) => v.cle === p.groupe) ? p.groupe : undefined;
  const statutDetail =
    p.statut && statutsVisibles(moi.interne).includes(p.statut) ? p.statut : undefined;
  const [{ documents }, reponse, compteursReponse] = await Promise.all([
    chargerReferentiels(),
    appelPage("/demandes", async () =>
      listerDemandes(
        {
          statut: statutDetail ? [statutDetail] : undefined,
          vue,
          enRetard: p.enRetard ? true : undefined,
          traiteParMoi: p.traiteParMoi ? true : undefined,
          bloque: p.bloque ? true : undefined,
          periode: p.periode,
          fraisPayes: p.fraisPayes ? p.fraisPayes === "true" : undefined,
          typeDocumentCode: p.document,
          q: p.q,
          tri: p.tri,
          sens: p.sens,
          page: p.page,
          taille: p.taille,
        },
        await optionsApi(),
      ),
    ),
    appelPage("/demandes", async () => compterDemandes(await optionsApi())),
  ]);
  const compteurs = compteursReponse.data;
  const resultat = reponse.data;
  const libelles = Object.fromEntries(documents.map((d) => [d.code, d.libelleFr]));
  const titre = moi.interne ? t("titreInterne") : t("titre");
  const nouvelle = moi.permissions.includes("DEPOSER") ? (
    <Bouton asChild>
      <Link href="/demandes/nouvelle">{t("nouvelle")}</Link>
    </Bouton>
  ) : null;

  // Filtres ouverts depuis une carte du tableau de bord : conservés d'une page, d'un tri ou d'une
  // vue à l'autre, effaçables un à un.
  const indicateurs = {
    enRetard: p.enRetard,
    traiteParMoi: p.traiteParMoi,
    bloque: p.bloque,
    periode: p.periode,
    fraisPayes: p.fraisPayes,
    document: p.document,
  };

  /** Adresse de la liste avec ces paramètres (les autres sont conservés). */
  const adresse = (changes: Partial<Record<keyof Parametres, string | undefined>>) => {
    const q = new URLSearchParams();
    const tout = {
      ...indicateurs,
      groupe: p.groupe,
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
  const conserves = {
    ...indicateurs,
    groupe: p.groupe,
    statut: p.statut,
    q: p.q,
    tri: p.tri,
    sens: p.sens,
  };
  const tFiltres = await getTranslations("mesDemandes.filtres");
  const tActifs = await getTranslations("mesDemandes.filtresTableau");
  const tPeriodes = await getTranslations("tableauDeBord.periodes");
  const filtresActifs: FiltreActif[] = [
    p.enRetard ? { cle: "enRetard", libelle: tActifs("enRetard") } : null,
    p.traiteParMoi ? { cle: "traiteParMoi", libelle: tActifs("traiteParMoi") } : null,
    p.bloque ? { cle: "bloque", libelle: tActifs("bloque") } : null,
    p.periode
      ? { cle: "periode", libelle: tActifs("periode", { periode: tPeriodes(p.periode) }) }
      : null,
    p.fraisPayes
      ? {
          cle: "fraisPayes",
          libelle: tActifs(p.fraisPayes === "true" ? "fraisPayes" : "fraisNonPayes"),
        }
      : null,
    p.document
      ? {
          cle: "document",
          libelle: tActifs("document", { libelle: libelles[p.document] ?? p.document }),
        }
      : null,
  ]
    .filter((f) => f !== null)
    .map((f) => ({ ...f, effacer: adresse({ [f.cle]: undefined }) }));
  const filtre = p.groupe ?? p.statut ?? (filtresActifs.length > 0 ? "indicateur" : undefined);

  return (
    <div className="flex flex-col gap-6">
      <EnTetePage
        titre={titre}
        description={moi.interne ? t("descriptionInterne") : t("description")}
        actions={nouvelle}
      />
      <RechercheListe
        libelle={t("recherche.libelle")}
        valeur={p.q}
        placeholder={t("recherche.exemple")}
        boutonLibelle={t("recherche.bouton")}
        conserver={{
          ...indicateurs,
          groupe: p.groupe,
          statut: p.statut,
          tri: p.tri,
          sens: p.sens,
          taille: String(p.taille),
        }}
      />
      <FiltresPastilles
        libelle={t("filtreStatut")}
        options={[
          {
            valeur: "",
            libelle: t("tous"),
            href: adresse({ groupe: undefined, statut: undefined }),
            actif: !filtre,
            nombre: vues.total,
          },
          ...vues.vues.map((v) => ({
            valeur: v.cle,
            libelle: tFiltres(cleLibelleVue(v.cle, moi.interne)),
            href: adresse({ groupe: v.cle, statut: undefined }),
            actif: vue === v.cle,
            nombre: v.nombre,
          })),
        ]}
        fin={
          <PlusDeFiltres
            libelle={t("plusDeFiltres")}
            titre={t("filtreDetail")}
            options={statutsVisibles(moi.interne).map((s) => ({
              valeur: s,
              libelle: tStatut(s),
              href: adresse({ groupe: undefined, statut: s }),
              actif: p.statut === s,
              nombre: compteurs[s] ?? 0,
            }))}
            actif={
              statutDetail
                ? {
                    libelle: tStatut(statutDetail),
                    effacer: adresse({ groupe: undefined, statut: undefined }),
                    libelleEffacer: t("effacerFiltre"),
                  }
                : undefined
            }
          />
        }
      />
      <FiltresActifs
        titre={tActifs("titre")}
        libelleEffacer={tActifs("effacer")}
        filtres={filtresActifs}
      />
      {resultat.total === 0 ? (
        <EtatVide
          titre={filtre || p.q ? t("aucuneFiltre") : t("aucune")}
          action={filtre || p.q ? undefined : nouvelle}
        />
      ) : (
        <div className="flex flex-col gap-4">
          <TableauDemandes
            interne={moi.interne}
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
