import {
  ErreurApi,
  listerJournalEncaissements,
  type PageJournalEncaissementsDto,
} from "@emine/api-client";
import {
  Alerte,
  CarteChiffre,
  EnTetePage,
  EtatVide,
  FiltresPastilles,
  formaterDate,
  formaterGnf,
  GrilleChiffres,
  tailleAutorisee,
} from "@emine/ui";
import { Banknote, Download, Landmark, Undo2 } from "@emine/ui/icones";
import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getLocale, getTranslations } from "next-intl/server";
import { TableauJournalEncaissements } from "@/composants/encaissement/tableau-journal-encaissements";
import { FiltrePeriode } from "@/composants/liste/filtre-periode";
import { PaginationListe } from "@/composants/liste/pagination-liste";
import { appelPage, chargerMoi, optionsApi } from "@/serveur/donnees";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("journalEncaissements");
  return { title: t("titre") };
}

const DATE = /^\d{4}-\d{2}-\d{2}$/;
const MOYENS = ["TRESORPAY", "VIREMENT"] as const;

type Props = {
  searchParams: Promise<{
    du?: string;
    au?: string;
    moyen?: string;
    page?: string;
    taille?: string;
  }>;
};

/** Premier et dernier jour du mois en cours, à l'heure de Conakry (UTC+0). */
function moisEnCours(maintenant = new Date()): { du: string; au: string } {
  const annee = maintenant.getUTCFullYear();
  const mois = maintenant.getUTCMonth();
  const dernier = new Date(Date.UTC(annee, mois + 1, 0)).getUTCDate();
  const mm = String(mois + 1).padStart(2, "0");
  return { du: `${annee}-${mm}-01`, au: `${annee}-${mm}-${String(dernier).padStart(2, "0")}` };
}

/**
 * Journal des encaissements du Responsable fiscal (US-04.11) : recettes et frais remboursés de la
 * période (le mois en cours par défaut), totaux calculés par l'API, filtre par moyen de paiement,
 * export CSV des mêmes lignes.
 */
export default async function JournalEncaissements({ searchParams }: Props) {
  const p = await searchParams;
  const moi = await chargerMoi();
  if (!moi.interne || !moi.permissions.includes("ENCAISSER")) notFound();
  const [t, langue] = await Promise.all([getTranslations("journalEncaissements"), getLocale()]);
  const defaut = moisEnCours();
  const periodeChoisie = p.du !== undefined || p.au !== undefined;
  const du = p.du && DATE.test(p.du) ? p.du : periodeChoisie ? undefined : defaut.du;
  const au = p.au && DATE.test(p.au) ? p.au : periodeChoisie ? undefined : defaut.au;
  const moyen = MOYENS.find((m) => m === p.moyen);
  const page = Math.max(1, Number.parseInt(p.page ?? "1", 10) || 1);
  const taille = tailleAutorisee(Number.parseInt(p.taille ?? "", 10));
  const periodeInversee = Boolean(du && au && au < du);

  const resultat: PageJournalEncaissementsDto | null = periodeInversee
    ? null
    : await appelPage("/journal-encaissements", async () =>
        listerJournalEncaissements({ du, au, moyen, page, taille }, await optionsApi()),
      )
        .then((r) => r.data)
        .catch((e: unknown) => {
          if (e instanceof ErreurApi && e.code === "VAL-001") return null;
          throw e;
        });

  const parametres = new URLSearchParams();
  if (du) parametres.set("du", du);
  if (au) parametres.set("au", au);
  if (moyen) parametres.set("moyen", moyen);
  const export_ = `/api/encaissements/journal.csv?${parametres.toString()}`;
  const adresse = (m?: string) => {
    const q = new URLSearchParams();
    if (du) q.set("du", du);
    if (au) q.set("au", au);
    if (m) q.set("moyen", m);
    return `/journal-encaissements?${q.toString()}`;
  };
  const periode =
    du && au ? t("periode", { du: formaterDate(du), au: formaterDate(au) }) : t("toutePeriode");
  const totaux = resultat?.totaux;

  return (
    <div className="flex flex-col gap-6">
      <EnTetePage
        titre={t("titre")}
        description={t("description")}
        actions={
          resultat && resultat.total > 0 ? (
            <a
              href={export_}
              download
              className="inline-flex min-h-11 items-center gap-2 rounded-lg border-2 border-royal bg-white px-4 font-semibold text-royal hover:bg-ice"
            >
              <Download aria-hidden="true" className="size-5" />
              {t("exporter")}
            </a>
          ) : undefined
        }
      />
      <FiltrePeriode du={du} au={au} conserver={{ moyen }} />
      {periodeInversee || !resultat || !totaux ? (
        <Alerte nature="erreur" titre={t("periodeInvalide.titre")}>
          {t("periodeInvalide.texte")}
        </Alerte>
      ) : (
        <>
          <GrilleChiffres colonnes={3}>
            <CarteChiffre
              libelle={t("chiffres.encaisse")}
              periode={periode}
              valeur={formaterGnf(totaux.encaisseGnf, langue)}
              precision={t("chiffres.encaissePrecision", {
                tresorpay: formaterGnf(totaux.tresorPayGnf, langue),
                virement: formaterGnf(totaux.virementGnf, langue),
              })}
              icone={Banknote}
            />
            <CarteChiffre
              libelle={t("chiffres.rembourse")}
              periode={periode}
              valeur={formaterGnf(totaux.rembourseGnf, langue)}
              precision={t("chiffres.remboursePrecision")}
              icone={Undo2}
            />
            <CarteChiffre
              libelle={t("chiffres.solde")}
              periode={periode}
              valeur={formaterGnf(totaux.soldeGnf, langue)}
              precision={t("chiffres.soldePrecision", { nombre: totaux.nombre })}
              icone={Landmark}
            />
          </GrilleChiffres>
          <FiltresPastilles
            libelle={t("filtres.libelle")}
            options={[
              { valeur: "", libelle: t("filtres.tous"), href: adresse(), actif: !moyen },
              ...MOYENS.map((m) => ({
                valeur: m,
                libelle: t(`moyens.${m}`),
                href: adresse(m),
                actif: moyen === m,
              })),
            ]}
          />
          {resultat.total === 0 ? (
            <EtatVide
              icone={Banknote}
              titre={t("vide.titre")}
              description={t("vide.description")}
            />
          ) : (
            <div className="flex flex-col gap-4">
              <TableauJournalEncaissements lignes={resultat.elements} />
              <PaginationListe
                chemin="/journal-encaissements"
                parametres={{ du, au, moyen }}
                page={resultat.page}
                taille={resultat.taille}
                total={resultat.total}
                elements={t("elements")}
              />
            </div>
          )}
          <p className="text-sm text-texte-doux">{t("note")}</p>
        </>
      )}
    </div>
  );
}
