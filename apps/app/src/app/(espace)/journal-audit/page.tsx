import {
  type AuteurDto,
  consulterJournal,
  ErreurApi,
  listerAuteursJournal,
  type PageJournalDto,
} from "@emine/api-client";
import { Alerte, EnTetePage, EtatVide, FiltresPastilles } from "@emine/ui";
import { ScrollText } from "@emine/ui/icones";
import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import {
  CATEGORIES,
  lireParametresJournal,
  type ParametresJournal,
} from "@/composants/audit/journal";
import { TableauJournal } from "@/composants/audit/tableau-journal";
import { FiltrePeriode } from "@/composants/liste/filtre-periode";
import { PaginationListe } from "@/composants/liste/pagination-liste";
import { PlusDeFiltres } from "@/composants/liste/plus-de-filtres";
import { RechercheListe } from "@/composants/liste/recherche-liste";
import { libelleProfil } from "@/composants/referentiels";
import { appelPage, chargerMoi, optionsApi } from "@/serveur/donnees";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("audit");
  return { title: t("titre") };
}

type Props = { searchParams: Promise<ParametresJournal> };

/**
 * Journal d'audit (US-11.7, partiel V1) : toutes les modifications enregistrées par la base, de la
 * plus récente à la plus ancienne, et les connexions ; filtres par catégorie (avec leur nombre) et
 * par auteur, recherche pendant la saisie et période appliquée dès qu'une date est choisie. Réservé
 * à l'administrateur (droit « paramétrer »).
 */
export default async function JournalAudit({ searchParams }: Props) {
  const p = lireParametresJournal(await searchParams);
  const moi = await chargerMoi();
  if (!moi.interne || !moi.permissions.includes("PARAMETRER")) notFound();
  const t = await getTranslations("audit");
  const tReferentiels = await getTranslations("referentiels");

  const auteurs: AuteurDto[] = (
    await appelPage("/journal-audit", async () => listerAuteursJournal(await optionsApi()))
  ).data;
  const auteurActif = auteurs.find((a) => String(a.id) === p.auteur);

  let resultat: PageJournalDto | null = null;
  let erreurPeriode = p.periodeInversee;
  if (!p.periodeInversee) {
    try {
      resultat = (
        await appelPage("/journal-audit", async () =>
          consulterJournal(
            {
              categorie: p.categorie,
              auteur: p.auteur ? Number(p.auteur) : undefined,
              q: p.q,
              du: p.du,
              au: p.au,
              page: p.page,
              taille: p.taille,
            },
            await optionsApi(),
          ),
        )
      ).data;
    } catch (e) {
      if (!(e instanceof ErreurApi && e.code === "VAL-001")) throw e;
      erreurPeriode = true;
    }
  }

  /** Adresse du journal avec ces paramètres (les autres sont conservés, retour en page 1). */
  const adresse = (changes: Partial<Record<keyof ParametresJournal, string | undefined>>) => {
    const q = new URLSearchParams();
    const tout = {
      categorie: p.categorie,
      auteur: p.auteur,
      q: p.q,
      du: p.du,
      au: p.au,
      taille: p.taille === 20 ? undefined : String(p.taille),
      ...changes,
    };
    for (const [cle, valeur] of Object.entries(tout)) if (valeur) q.set(cle, valeur);
    const chaine = q.toString();
    return chaine ? `/journal-audit?${chaine}` : "/journal-audit";
  };
  const compteurs = resultat?.compteurs ?? {};
  const total = Object.values(compteurs).reduce((s, n) => s + n, 0);
  const filtre = Boolean(p.categorie || p.auteur || p.q || p.du || p.au);
  const libelleAuteur = (a: AuteurDto) =>
    `${a.nom} · ${libelleProfil(a.profilCode, a.profil, tReferentiels)}`;

  return (
    <div className="flex flex-col gap-6">
      <EnTetePage titre={t("titre")} description={t("description")} />

      <RechercheListe
        libelle={t("recherche.libelle")}
        valeur={p.q}
        placeholder={t("recherche.exemple")}
        boutonLibelle={t("recherche.bouton")}
        conserver={{ categorie: p.categorie, auteur: p.auteur, du: p.du, au: p.au }}
      />

      <FiltrePeriode
        du={p.du}
        au={p.au}
        conserver={{ categorie: p.categorie, auteur: p.auteur, q: p.q }}
      />
      {erreurPeriode && !p.periodeInversee ? (
        <Alerte nature="erreur" titre={t("periode.inverseeTitre")}>
          {t("periode.inversee")}
        </Alerte>
      ) : null}

      {resultat ? (
        <>
          <FiltresPastilles
            libelle={t("filtre")}
            options={[
              {
                valeur: "",
                libelle: t("categories.toutes"),
                href: adresse({ categorie: undefined }),
                actif: !p.categorie,
                nombre: total,
              },
              ...CATEGORIES.map((c) => ({
                valeur: c,
                libelle: t(`categories.${c}`),
                href: adresse({ categorie: c }),
                actif: p.categorie === c,
                nombre: compteurs[c] ?? 0,
              })),
            ]}
            fin={
              auteurs.length > 0 ? (
                <PlusDeFiltres
                  libelle={t("auteurs.libelle")}
                  titre={t("auteurs.libelle")}
                  options={auteurs.map((a) => ({
                    valeur: String(a.id),
                    libelle: libelleAuteur(a),
                    href: adresse({ auteur: String(a.id) }),
                    actif: p.auteur === String(a.id),
                  }))}
                  actif={
                    auteurActif
                      ? {
                          libelle: auteurActif.nom,
                          effacer: adresse({ auteur: undefined }),
                          libelleEffacer: t("auteurs.tous"),
                        }
                      : undefined
                  }
                />
              ) : undefined
            }
          />
          {resultat.total === 0 ? (
            <EtatVide
              icone={ScrollText}
              titre={filtre ? t("vide.filtre") : t("vide.titre")}
              action={
                filtre ? (
                  <Link
                    href="/journal-audit"
                    className="inline-flex min-h-11 items-center text-royal underline-offset-4 hover:underline"
                  >
                    {t("vide.toutVoir")}
                  </Link>
                ) : undefined
              }
            />
          ) : (
            <div className="flex flex-col gap-4">
              <TableauJournal entrees={resultat.elements} legende={t("titre")} />
              <PaginationListe
                chemin="/journal-audit"
                parametres={{
                  categorie: p.categorie,
                  auteur: p.auteur,
                  q: p.q,
                  du: p.du,
                  au: p.au,
                }}
                page={resultat.page}
                taille={resultat.taille}
                total={resultat.total}
                elements={{ un: t("element"), plusieurs: t("elements") }}
              />
            </div>
          )}
        </>
      ) : null}
    </div>
  );
}
