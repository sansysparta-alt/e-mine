import { listerRemboursements } from "@emine/api-client";
import { EnTetePage, EtatVide, FiltresPastilles, tailleAutorisee } from "@emine/ui";
import { Undo2 } from "@emine/ui/icones";
import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { PaginationListe } from "@/composants/liste/pagination-liste";
import { FILTRES_REMBOURSEMENTS, profilRemboursement } from "@/composants/remboursement/statut";
import { TableauRemboursements } from "@/composants/remboursement/tableau-remboursements";
import { appelPage, chargerMoi, optionsApi } from "@/serveur/donnees";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("remboursements");
  return { title: t("titre") };
}

type Props = { searchParams: Promise<{ statut?: string; page?: string; taille?: string }> };

/**
 * « Remboursements » (US-04.10, US-04.18) : le Directeur valide ou refuse chaque proposition du
 * Responsable fiscal ; le Responsable fiscal enregistre ensuite l'ordre de remboursement du Trésor
 * (l'argent est rendu hors plateforme). Filtres par statut avec leur nombre, calculé par l'API,
 * libellés propres au profil.
 */
export default async function Remboursements({ searchParams }: Props) {
  const p = await searchParams;
  const moi = await chargerMoi();
  const profil = profilRemboursement(moi.permissions);
  if (!moi.interne || profil === "usager") notFound();
  const t = await getTranslations("remboursements");
  const statut = FILTRES_REMBOURSEMENTS.find((s) => s === p.statut);
  const page = Math.max(1, Number.parseInt(p.page ?? "1", 10) || 1);
  const taille = tailleAutorisee(Number.parseInt(p.taille ?? "", 10));
  const resultat = (
    await appelPage("/remboursements", async () =>
      listerRemboursements({ statut, page, taille }, await optionsApi()),
    )
  ).data;
  const c = resultat.compteurs;
  const adresse = (s?: string) => (s ? `/remboursements?statut=${s}` : "/remboursements");

  return (
    <div className="flex flex-col gap-6">
      <EnTetePage titre={t("titre")} description={t(`description.${profil}`)} />
      {c.tous === 0 ? (
        <EtatVide
          icone={Undo2}
          titre={t("vide.titre")}
          description={t(`vide.description.${profil}`)}
        />
      ) : (
        <>
          <FiltresPastilles
            libelle={t("filtres.libelle")}
            options={[
              {
                valeur: "",
                libelle: t("filtres.tous"),
                href: adresse(),
                actif: !statut,
                nombre: c.tous,
              },
              ...FILTRES_REMBOURSEMENTS.map((s) => ({
                valeur: s,
                libelle: t(`filtres.${profil}.${s}`),
                href: adresse(s),
                actif: statut === s,
                nombre: c[s],
              })),
            ]}
          />
          {resultat.total === 0 ? (
            <EtatVide icone={Undo2} titre={t("aucun")} />
          ) : (
            <div className="flex flex-col gap-4">
              <TableauRemboursements remboursements={resultat.elements} profil={profil} />
              <PaginationListe
                chemin="/remboursements"
                parametres={{ statut }}
                page={resultat.page}
                taille={resultat.taille}
                total={resultat.total}
                elements={{ un: t("element"), plusieurs: t("elements") }}
              />
            </div>
          )}
        </>
      )}
    </div>
  );
}
