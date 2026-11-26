import { ErreurApi, lireReleve, listerLignesReleve, type ReleveDto } from "@emine/api-client";
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
import { CircleAlert, CircleCheck, ListChecks } from "@emine/ui/icones";
import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { FilArianeApp } from "@/composants/fil-ariane-app";
import { PaginationListe } from "@/composants/liste/pagination-liste";
import { TableauLignesReleve } from "@/composants/releve/tableaux-releve";
import { appelPage, chargerMoi, optionsApi } from "@/serveur/donnees";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("releves.detail");
  return { title: t("titreOnglet") };
}

const RESULTATS = ["rapprochees", "a-verifier", "traitees"] as const;

type Props = {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ resultat?: string; page?: string; taille?: string; importe?: string }>;
};

/**
 * Un relevé bancaire (US-04.8) : ses chiffres (lignes, virements confirmés, lignes à vérifier),
 * qui ouvrent les lignes filtrées correspondantes, puis les lignes dans l'ordre du fichier. Juste
 * après l'import, une alerte résume ce qui a été confirmé et ce qui attend une décision.
 */
export default async function DetailReleve({ params, searchParams }: Props) {
  const [{ id }, p] = await Promise.all([params, searchParams]);
  if (!/^\d{1,18}$/.test(id)) notFound();
  const moi = await chargerMoi();
  if (!moi.interne || !moi.permissions.includes("ENCAISSER")) notFound();
  const chemin = `/releves/${id}`;
  const resultat = RESULTATS.find((r) => r === p.resultat);
  const page = Math.max(1, Number.parseInt(p.page ?? "1", 10) || 1);
  const taille = tailleAutorisee(Number.parseInt(p.taille ?? "", 10));
  let releve: ReleveDto;
  try {
    releve = (await appelPage(chemin, async () => lireReleve(Number(id), await optionsApi()))).data;
  } catch (e) {
    if (e instanceof ErreurApi && e.code === "PAY-012") notFound();
    throw e;
  }
  const [lignes, t] = await Promise.all([
    appelPage(chemin, async () =>
      listerLignesReleve(Number(id), { resultat, page, taille }, await optionsApi()),
    ).then((r) => r.data),
    getTranslations("releves.detail"),
  ]);
  const aVerifier = releve.ecarts + releve.inconnues;
  const adresse = (r?: string) => (r ? `${chemin}?resultat=${r}` : chemin);

  return (
    <div className="flex flex-col gap-6">
      <EnTetePage
        titre={t("titre", { date: formaterDate(releve.dateReleve) })}
        description={t("description", { banque: releve.banque, fichier: releve.nomFichier })}
        retour={<FilArianeApp rubrique="releves" courante={formaterDate(releve.dateReleve)} />}
      />
      {p.importe === "1" ? (
        <Alerte
          nature={aVerifier > 0 ? "info" : "succes"}
          titre={t("importe", { rapprochees: releve.rapprochees, aVerifier })}
        >
          {t("importeTexte")}
        </Alerte>
      ) : null}
      <GrilleChiffres colonnes={3}>
        <CarteChiffre
          libelle={t("lignes")}
          periode={t("periode")}
          valeur={releve.lignes}
          precision={t("credits", { montant: formaterGnf(releve.totalCreditsGnf) })}
          icone={ListChecks}
          href={adresse()}
        />
        <CarteChiffre
          libelle={t("rapprochees")}
          periode={t("periode")}
          valeur={releve.rapprochees}
          precision={t("rapprocheesPrecision")}
          icone={CircleCheck}
          href={adresse("rapprochees")}
        />
        <CarteChiffre
          libelle={t("aVerifier")}
          periode={t("periode")}
          valeur={aVerifier}
          precision={t("aVerifierPrecision")}
          icone={CircleAlert}
          href={adresse("a-verifier")}
        />
      </GrilleChiffres>
      <FiltresPastilles
        libelle={t("filtres")}
        options={[
          {
            valeur: "",
            libelle: t("toutes"),
            href: adresse(),
            actif: !resultat,
            nombre: releve.lignes,
          },
          {
            valeur: "rapprochees",
            libelle: t("filtreRapprochees"),
            href: adresse("rapprochees"),
            actif: resultat === "rapprochees",
            nombre: releve.rapprochees,
          },
          {
            valeur: "a-verifier",
            libelle: t("filtreAVerifier"),
            href: adresse("a-verifier"),
            actif: resultat === "a-verifier",
            nombre: aVerifier,
          },
          {
            valeur: "traitees",
            libelle: t("filtreTraitees"),
            href: adresse("traitees"),
            actif: resultat === "traitees",
            nombre: releve.traitees,
          },
        ]}
      />
      {lignes.total === 0 ? (
        <EtatVide icone={ListChecks} titre={t("aucune")} />
      ) : (
        <div className="flex flex-col gap-4">
          <TableauLignesReleve lignes={lignes.elements} />
          <PaginationListe
            chemin={chemin}
            parametres={{ resultat }}
            page={lignes.page}
            taille={lignes.taille}
            total={lignes.total}
            elements={t("elements")}
          />
        </div>
      )}
    </div>
  );
}
