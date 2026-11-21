import { compterDemandes, listerDemandes } from "@emine/api-client";
import {
  Alerte,
  Bouton,
  CarteChiffre,
  EnTetePage,
  EtatVide,
  FiltresPastilles,
  GrilleChiffres,
} from "@emine/ui";
import { ArrowRight, CircleCheck, CircleX, Hourglass, Inbox } from "@emine/ui/icones";
import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import {
  FRAIS_PAYES_PAR_ONGLET,
  lireParametresFile,
  ONGLETS_FILE,
  type OngletFile,
  type ParametresFile,
} from "@/composants/instruction/file-guichet";
import { TableauFile } from "@/composants/instruction/tableau-file";
import { PaginationListe } from "@/composants/liste/pagination-liste";
import { RechercheListe } from "@/composants/liste/recherche-liste";
import { appelPage, chargerMoi, chargerReferentiels, optionsApi } from "@/serveur/donnees";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("instruction.file");
  return { title: t("titre") };
}

type Props = { searchParams: Promise<ParametresFile> };

/**
 * File du guichet unique (US-05.1) : où en sont les dossiers (chiffres clés), puis les demandes
 * déposées à examiner, de la plus ancienne à la plus récente (premier arrivé, premier servi).
 * Réservée aux profils qui instruisent.
 */
export default async function FileGuichet({ searchParams }: Props) {
  const p = lireParametresFile(await searchParams);
  const moi = await chargerMoi();
  if (!moi.interne || !moi.permissions.includes("INSTRUIRE")) notFound();
  const t = await getTranslations("instruction.file");

  const liste = async (onglet: OngletFile, page: number, taille: number) =>
    listerDemandes(
      {
        statut: ["deposee"],
        fraisPayes: FRAIS_PAYES_PAR_ONGLET[onglet],
        q: p.q,
        tri: "date",
        sens: "asc",
        page,
        taille,
      },
      await optionsApi(),
    );
  const autre: OngletFile = p.onglet === "a-examiner" ? "frais-attendus" : "a-examiner";
  const [{ documents }, courant, compteAutre, compteurs] = await Promise.all([
    chargerReferentiels(),
    appelPage("/guichet", () => liste(p.onglet, p.page, p.taille)),
    appelPage("/guichet", () => liste(autre, 1, 10)),
    appelPage("/guichet", async () => compterDemandes(await optionsApi())),
  ]);
  const resultat = courant.data;
  const totaux: Record<OngletFile, number> = {
    [p.onglet]: resultat.total,
    [autre]: compteAutre.data.total,
  } as Record<OngletFile, number>;
  const libelles = Object.fromEntries(documents.map((d) => [d.code, d.libelleFr]));

  const adresse = (onglet: OngletFile) => {
    const q = new URLSearchParams();
    if (onglet !== "a-examiner") q.set("onglet", onglet);
    if (p.q) q.set("q", p.q);
    const chaine = q.toString();
    return chaine ? `/guichet?${chaine}` : "/guichet";
  };
  const legende = t(`onglets.${p.onglet}`);
  /** Demande suivante à examiner (la plus ancienne), proposée juste après une déclaration. */
  const suivante = p.onglet === "a-examiner" ? resultat.elements[0] : undefined;

  return (
    <div className="flex flex-col gap-6">
      {p.traitee ? (
        <Alerte
          nature="succes"
          titre={t(`traitee.${p.traitee.resultat}`, { numero: p.traitee.numero })}
        >
          <p>{t(`traitee.${p.traitee.resultat}Suite`)}</p>
          {suivante ? (
            <div className="mt-3">
              <Bouton asChild>
                <Link href={`/demandes/${suivante.id}?depuis=guichet`}>
                  {t("suivante", { numero: suivante.numero ?? "" })}
                  <ArrowRight aria-hidden="true" className="size-4" />
                </Link>
              </Bouton>
            </div>
          ) : null}
        </Alerte>
      ) : null}
      <EnTetePage titre={t("titre")} description={t("description")} />
      <section aria-labelledby="chiffres-guichet">
        <h2 id="chiffres-guichet" className="sr-only">
          {t("chiffres")}
        </h2>
        <GrilleChiffres>
          <CarteChiffre
            libelle={t("cartes.aExaminer")}
            valeur={totaux["a-examiner"]}
            precision={t("cartes.aExaminerPrecision")}
            icone={Inbox}
            href={adresse("a-examiner")}
          />
          <CarteChiffre
            libelle={t("cartes.fraisAttendus")}
            valeur={totaux["frais-attendus"]}
            precision={t("cartes.fraisAttendusPrecision")}
            icone={Hourglass}
            href={adresse("frais-attendus")}
          />
          <CarteChiffre
            libelle={t("cartes.recevables")}
            valeur={compteurs.data["recevable"] ?? 0}
            precision={t("cartes.recevablesPrecision")}
            icone={CircleCheck}
            href="/demandes?statut=recevable"
          />
          <CarteChiffre
            libelle={t("cartes.nonRecevables")}
            valeur={compteurs.data["rejetee"] ?? 0}
            precision={t("cartes.nonRecevablesPrecision")}
            icone={CircleX}
            href="/demandes?statut=rejetee"
          />
        </GrilleChiffres>
      </section>

      <section aria-labelledby="file-demandes" className="flex flex-col gap-4">
        <h2 id="file-demandes" className="text-xl">
          {t("demandesDeposees")}
        </h2>
        <RechercheListe
          libelle={t("recherche.libelle")}
          valeur={p.q}
          placeholder={t("recherche.exemple")}
          boutonLibelle={t("recherche.bouton")}
          conserver={{
            onglet: p.onglet === "a-examiner" ? undefined : p.onglet,
            taille: String(p.taille),
          }}
        />
        <FiltresPastilles
          libelle={t("filtre")}
          options={ONGLETS_FILE.map((o) => ({
            valeur: o,
            libelle: t(`onglets.${o}`),
            href: adresse(o),
            actif: p.onglet === o,
            nombre: totaux[o],
          }))}
        />
        {resultat.total === 0 ? (
          <EtatVide
            titre={p.q ? t("vide.recherche") : t(`vide.${p.onglet}`)}
            description={p.q ? undefined : t(`vide.${p.onglet}Description`)}
            action={
              p.q ? undefined : (
                <Link
                  href="/demandes"
                  className="inline-flex min-h-11 items-center text-royal underline-offset-4 hover:underline"
                >
                  {t("vide.toutesLesDemandes")}
                </Link>
              )
            }
          />
        ) : (
          <div className="flex flex-col gap-4">
            <p className="text-texte-doux">{t(`aide.${p.onglet}`)}</p>
            <TableauFile
              demandes={resultat.elements}
              libelles={libelles}
              legende={legende}
              maintenant={new Date()}
              depuis="guichet"
            />
            <PaginationListe
              chemin="/guichet"
              parametres={{ onglet: p.onglet === "a-examiner" ? undefined : p.onglet, q: p.q }}
              page={resultat.page}
              taille={resultat.taille}
              total={resultat.total}
              elements={t("elements")}
            />
          </div>
        )}
      </section>
    </div>
  );
}
