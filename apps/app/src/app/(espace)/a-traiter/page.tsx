import { listerDemandes } from "@emine/api-client";
import { Alerte, Bouton, EnTetePage, EtatVide, FiltresPastilles } from "@emine/ui";
import { ArrowRight, Inbox, Play } from "@emine/ui/icones";
import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { PaginationListe } from "@/composants/liste/pagination-liste";
import { RechercheListe } from "@/composants/liste/recherche-liste";
import {
  adresseFiche,
  type Groupe,
  groupesPour,
  lireParametresTravail,
  type ParametresTravail,
  parametresListe,
} from "@/composants/travail/file-travail";
import { TableauTravail } from "@/composants/travail/tableau-travail";
import { appelPage, chargerMoi, chargerReferentiels, optionsApi } from "@/serveur/donnees";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("travail");
  return { title: t("titre") };
}

type Props = { searchParams: Promise<ParametresTravail> };

/**
 * « À traiter » (correctifs J5, parcours fluides) : la file de travail de l'agent, en groupes
 * définis selon son profil (guichet : à examiner, frais attendus ; signataire : tout, à décider, à
 * signer), avec leur nombre, l'action attendue sur chaque dossier, les plus anciens d'abord, et le
 * traitement en série (« Commencer le traitement »).
 */
export default async function ATraiter({ searchParams }: Props) {
  const moi = await chargerMoi();
  const groupes = groupesPour(moi);
  if (groupes.length === 0) notFound();
  const p = lireParametresTravail(await searchParams, groupes);
  const t = await getTranslations("travail");

  const liste = async (g: Groupe, page: number, taille: number, q?: string) =>
    listerDemandes({ ...parametresListe(g, moi.profil.code), q, page, taille }, await optionsApi());
  const [{ documents }, courant, ...autres] = await Promise.all([
    chargerReferentiels(),
    appelPage("/a-traiter", () => liste(p.groupe, p.page, p.taille, p.q)),
    ...groupes
      .filter((g) => g.cle !== p.groupe.cle)
      .map((g) => appelPage("/a-traiter", () => liste(g, 1, 10))),
  ]);
  const resultat = courant.data;
  const totaux: Record<string, number> = { [p.groupe.cle]: resultat.total };
  groupes
    .filter((g) => g.cle !== p.groupe.cle)
    .forEach((g, i) => {
      totaux[g.cle] = autres[i]?.data.total ?? 0;
    });
  const libelles = Object.fromEntries(documents.map((d) => [d.code, d.libelleFr]));
  const premier = resultat.elements[0];
  const adresse = (cle: string) =>
    cle === groupes[0]?.cle ? "/a-traiter" : `/a-traiter?groupe=${cle}`;
  const commencer =
    p.groupe.actionnable && premier && !p.q ? (
      <Bouton asChild taille="grande">
        <Link href={adresseFiche(premier.id, p.groupe.cle)}>
          <Play aria-hidden="true" className="size-5" />
          {t("commencer", { nombre: resultat.total })}
        </Link>
      </Bouton>
    ) : null;

  return (
    <div className="flex flex-col gap-6">
      {p.faite ? (
        <Alerte
          nature="succes"
          titre={t(`fait.${p.faite.resultat}`, {
            numero: p.faite.numero,
            document: p.faite.document ?? "",
          })}
        >
          {premier && p.groupe.actionnable ? (
            <div className="mt-2">
              <Bouton asChild>
                <Link href={adresseFiche(premier.id, p.groupe.cle)}>
                  {t("continuer", { nombre: resultat.total })}
                  <ArrowRight aria-hidden="true" className="size-4" />
                </Link>
              </Bouton>
            </div>
          ) : (
            <p>{t("fileVide")}</p>
          )}
        </Alerte>
      ) : null}
      <EnTetePage titre={t("titre")} description={t("description")} actions={commencer} />
      <FiltresPastilles
        libelle={t("filtre")}
        options={groupes.map((g) => ({
          valeur: g.cle,
          libelle: t(`groupes.${g.cle}`),
          href: adresse(g.cle),
          actif: g.cle === p.groupe.cle,
          nombre: totaux[g.cle] ?? 0,
        }))}
      />
      <RechercheListe
        libelle={t("recherche.libelle")}
        valeur={p.q}
        placeholder={t("recherche.exemple")}
        boutonLibelle={t("recherche.bouton")}
        conserver={{
          groupe: p.groupe.cle === groupes[0]?.cle ? undefined : p.groupe.cle,
          taille: String(p.taille),
        }}
      />
      {resultat.total === 0 ? (
        <EtatVide
          icone={Inbox}
          titre={p.q ? t("vide.recherche") : t(`vide.${p.groupe.cle}`)}
          description={p.q ? undefined : t("vide.description")}
        />
      ) : (
        <div className="flex flex-col gap-4">
          <p className="text-texte-doux">{t(`aide.${p.groupe.cle}`)}</p>
          <TableauTravail
            demandes={resultat.elements}
            libelles={libelles}
            legende={t(`groupes.${p.groupe.cle}`)}
            groupe={p.groupe.cle}
            maintenant={new Date()}
          />
          <PaginationListe
            chemin="/a-traiter"
            parametres={{
              groupe: p.groupe.cle === groupes[0]?.cle ? undefined : p.groupe.cle,
              q: p.q,
            }}
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
