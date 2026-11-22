import { listerDemandes } from "@emine/api-client";
import { Alerte, Bouton, EnTetePage, EtatVide, FiltresPastilles } from "@emine/ui";
import { ArrowRight, FileSignature, Stamp } from "@emine/ui/icones";
import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import {
  lireParametresDecisions,
  ONGLETS_SIGNATAIRE,
  type OngletSignataire,
  type ParametresDecisions,
  STATUT_PAR_ONGLET,
} from "@/composants/instruction/file-decisions";
import { TableauFile } from "@/composants/instruction/tableau-file";
import { PaginationListe } from "@/composants/liste/pagination-liste";
import { RechercheListe } from "@/composants/liste/recherche-liste";
import { appelPage, chargerMoi, chargerReferentiels, optionsApi } from "@/serveur/donnees";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("instruction.decisions");
  return { title: t("titre") };
}

type Props = { searchParams: Promise<ParametresDecisions> };

/**
 * Espace du signataire (US-05.4, US-06.3) : demandes recevables à décider et demandes dont les
 * droits sont payés, à signer et délivrer ; seulement les documents dont il est le signataire au
 * catalogue (D4), de la plus ancienne à la plus récente. Réservé aux profils qui signent.
 */
export default async function FileDecisions({ searchParams }: Props) {
  const p = lireParametresDecisions(await searchParams);
  const moi = await chargerMoi();
  if (!moi.interne || !moi.permissions.includes("SIGNER")) notFound();
  const t = await getTranslations("instruction.decisions");
  const liste = async (onglet: OngletSignataire, page: number, taille: number) =>
    listerDemandes(
      {
        statut: [STATUT_PAR_ONGLET[onglet]],
        signataireProfil: moi.profil.code,
        q: p.q,
        tri: "date",
        sens: "asc",
        page,
        taille,
      },
      await optionsApi(),
    );
  const autre: OngletSignataire = p.onglet === "a-decider" ? "a-signer" : "a-decider";
  const [{ documents }, courant, compteAutre] = await Promise.all([
    chargerReferentiels(),
    appelPage("/decisions", () => liste(p.onglet, p.page, p.taille)),
    appelPage("/decisions", () => liste(autre, 1, 10)),
  ]);
  const resultat = courant.data;
  const totaux = {
    [p.onglet]: resultat.total,
    [autre]: compteAutre.data.total,
  } as Record<OngletSignataire, number>;
  /** Demande suivante de l'onglet (la plus ancienne), proposée juste après une action. */
  const suivante = resultat.elements[0];
  const libelles = Object.fromEntries(documents.map((d) => [d.code, d.libelleFr]));
  const adresse = (onglet: OngletSignataire) => {
    const q = new URLSearchParams();
    if (onglet !== "a-decider") q.set("onglet", onglet);
    if (p.q) q.set("q", p.q);
    const chaine = q.toString();
    return chaine ? `/decisions?${chaine}` : "/decisions";
  };

  return (
    <div className="flex flex-col gap-6">
      {p.traitee ? (
        <Alerte
          nature="succes"
          titre={t(`traitee.${p.traitee.resultat}`, {
            numero: p.traitee.numero,
            document: p.traitee.document ?? "",
          })}
        >
          <p>{t(p.traitee.resultat === "delivree" ? "traiteeSuiteDelivree" : "traiteeSuite")}</p>
          {suivante ? (
            <div className="mt-3">
              <Bouton asChild>
                <Link href={`/demandes/${suivante.id}?depuis=decisions`}>
                  {t(`suivante.${p.onglet}`, { numero: suivante.numero ?? "" })}
                  <ArrowRight aria-hidden="true" className="size-4" />
                </Link>
              </Bouton>
            </div>
          ) : null}
        </Alerte>
      ) : null}
      <EnTetePage
        titre={t("titre")}
        description={t("description", { profil: moi.profil.libelle })}
      />
      <FiltresPastilles
        libelle={t("filtre")}
        options={ONGLETS_SIGNATAIRE.map((o) => ({
          valeur: o,
          libelle: t(`onglets.${o}`),
          href: adresse(o),
          actif: p.onglet === o,
          nombre: totaux[o],
        }))}
      />
      <RechercheListe
        libelle={t("recherche.libelle")}
        valeur={p.q}
        placeholder={t("recherche.exemple")}
        boutonLibelle={t("recherche.bouton")}
        conserver={{
          onglet: p.onglet === "a-decider" ? undefined : p.onglet,
          taille: String(p.taille),
        }}
      />
      {resultat.total === 0 ? (
        <EtatVide
          icone={p.onglet === "a-signer" ? FileSignature : Stamp}
          titre={p.q ? t("vide.recherche") : t(`vide.${p.onglet}`)}
          description={p.q ? undefined : t(`vide.${p.onglet}Description`)}
        />
      ) : (
        <div className="flex flex-col gap-4">
          <p className="text-texte-doux">{t(`nombre.${p.onglet}`, { nombre: resultat.total })}</p>
          <TableauFile
            demandes={resultat.elements}
            libelles={libelles}
            legende={t(`onglets.${p.onglet}`)}
            maintenant={new Date()}
            avecFrais={false}
            depuis="decisions"
          />
          <PaginationListe
            chemin="/decisions"
            parametres={{ onglet: p.onglet === "a-decider" ? undefined : p.onglet, q: p.q }}
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
