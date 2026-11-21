import { listerDemandes } from "@emine/api-client";
import { Alerte, Bouton, EnTetePage, EtatVide } from "@emine/ui";
import { ArrowRight, Stamp } from "@emine/ui/icones";
import Link from "next/link";
import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import {
  lireParametresDecisions,
  type ParametresDecisions,
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
 * Décisions à prendre (US-05.4) : les demandes recevables dont l'utilisateur est le signataire au
 * catalogue (D4), de la plus ancienne à la plus récente. Réservée aux profils qui signent.
 */
export default async function FileDecisions({ searchParams }: Props) {
  const p = lireParametresDecisions(await searchParams);
  const moi = await chargerMoi();
  if (!moi.interne || !moi.permissions.includes("SIGNER")) notFound();
  const t = await getTranslations("instruction.decisions");
  const [{ documents }, reponse] = await Promise.all([
    chargerReferentiels(),
    appelPage("/decisions", async () =>
      listerDemandes(
        {
          statut: ["recevable"],
          signataireProfil: moi.profil.code,
          q: p.q,
          tri: "date",
          sens: "asc",
          page: p.page,
          taille: p.taille,
        },
        await optionsApi(),
      ),
    ),
  ]);
  const resultat = reponse.data;
  /** Demande suivante à décider (la plus ancienne), proposée juste après une décision. */
  const suivante = resultat.elements[0];
  const libelles = Object.fromEntries(documents.map((d) => [d.code, d.libelleFr]));

  return (
    <div className="flex flex-col gap-6">
      {p.traitee ? (
        <Alerte
          nature="succes"
          titre={t(`traitee.${p.traitee.resultat}`, { numero: p.traitee.numero })}
        >
          <p>{t("traiteeSuite")}</p>
          {suivante ? (
            <div className="mt-3">
              <Bouton asChild>
                <Link href={`/demandes/${suivante.id}?depuis=decisions`}>
                  {t("suivante", { numero: suivante.numero ?? "" })}
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
      <RechercheListe
        libelle={t("recherche.libelle")}
        valeur={p.q}
        placeholder={t("recherche.exemple")}
        boutonLibelle={t("recherche.bouton")}
        conserver={{ taille: String(p.taille) }}
      />
      {resultat.total === 0 ? (
        <EtatVide
          icone={Stamp}
          titre={p.q ? t("vide.recherche") : t("vide.titre")}
          description={p.q ? undefined : t("vide.description")}
        />
      ) : (
        <div className="flex flex-col gap-4">
          <p className="text-texte-doux">{t("nombre", { nombre: resultat.total })}</p>
          <TableauFile
            demandes={resultat.elements}
            libelles={libelles}
            legende={t("titre")}
            maintenant={new Date()}
            avecFrais={false}
            depuis="decisions"
          />
          <PaginationListe
            chemin="/decisions"
            parametres={{ q: p.q }}
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
