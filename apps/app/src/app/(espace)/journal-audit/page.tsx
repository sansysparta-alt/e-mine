import { consulterJournal, ErreurApi, type PageJournalDto } from "@emine/api-client";
import { Alerte, Bouton, classesChamp, EnTetePage, EtatVide, FiltresPastilles } from "@emine/ui";
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
import { PaginationListe } from "@/composants/liste/pagination-liste";
import { RechercheListe } from "@/composants/liste/recherche-liste";
import { appelPage, chargerMoi, optionsApi } from "@/serveur/donnees";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("audit");
  return { title: t("titre") };
}

type Props = { searchParams: Promise<ParametresJournal> };

/**
 * Journal d'audit (US-11.7, partiel V1) : toutes les modifications enregistrées par la base, de la
 * plus récente à la plus ancienne ; filtres par catégorie (avec leur nombre), recherche pendant la
 * saisie et période. Réservé à l'administrateur (droit « paramétrer »).
 */
export default async function JournalAudit({ searchParams }: Props) {
  const p = lireParametresJournal(await searchParams);
  const moi = await chargerMoi();
  if (!moi.interne || !moi.permissions.includes("PARAMETRER")) notFound();
  const t = await getTranslations("audit");

  let resultat: PageJournalDto | null = null;
  let erreurPeriode = p.periodeInversee;
  if (!p.periodeInversee) {
    try {
      resultat = (
        await appelPage("/journal-audit", async () =>
          consulterJournal(
            {
              categorie: p.categorie,
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
  const filtre = Boolean(p.categorie || p.q || p.du || p.au);

  return (
    <div className="flex flex-col gap-6">
      <EnTetePage titre={t("titre")} description={t("description")} />

      <RechercheListe
        libelle={t("recherche.libelle")}
        valeur={p.q}
        placeholder={t("recherche.exemple")}
        boutonLibelle={t("recherche.bouton")}
        conserver={{ categorie: p.categorie, du: p.du, au: p.au }}
      />

      <form
        method="get"
        action="/journal-audit"
        className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-end"
        aria-describedby={erreurPeriode ? "periode-erreur" : undefined}
      >
        {p.categorie ? <input type="hidden" name="categorie" value={p.categorie} /> : null}
        {p.q ? <input type="hidden" name="q" value={p.q} /> : null}
        <div className="flex flex-col gap-1">
          <label htmlFor="periode-du" className="font-semibold">
            {t("periode.du")}
          </label>
          <input
            id="periode-du"
            type="date"
            name="du"
            defaultValue={p.du}
            aria-invalid={erreurPeriode ? true : undefined}
            className={classesChamp}
          />
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor="periode-au" className="font-semibold">
            {t("periode.au")}
          </label>
          <input
            id="periode-au"
            type="date"
            name="au"
            defaultValue={p.au}
            aria-invalid={erreurPeriode ? true : undefined}
            className={classesChamp}
          />
        </div>
        <Bouton type="submit" variante="secondaire">
          {t("periode.appliquer")}
        </Bouton>
        {p.du || p.au ? (
          <Link
            href={adresse({ du: undefined, au: undefined })}
            className="inline-flex min-h-11 items-center text-royal underline-offset-4 hover:underline"
          >
            {t("periode.effacer")}
          </Link>
        ) : null}
      </form>
      {erreurPeriode ? (
        <div id="periode-erreur">
          <Alerte nature="erreur" titre={t("periode.inverseeTitre")}>
            {t("periode.inversee")}
          </Alerte>
        </div>
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
                parametres={{ categorie: p.categorie, q: p.q, du: p.du, au: p.au }}
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
