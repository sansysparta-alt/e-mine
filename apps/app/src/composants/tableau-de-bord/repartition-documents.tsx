import type { ParDocumentDto } from "@emine/api-client";
import {
  type ColonneTableau,
  type ComposantLien,
  EtatVide,
  formaterGnf,
  formaterNombre,
  TableauDonnees,
} from "@emine/ui";
import Link from "next/link";
import { useTranslations } from "next-intl";

const LienNext: ComposantLien = ({ href, children, ...props }) => (
  <Link href={href} {...props}>
    {children}
  </Link>
);

export type RepartitionDocumentsProps = {
  lignes: ParDocumentDto[];
  /** Libellé de chaque document du catalogue, par code. */
  libelles: Record<string, string>;
  /** Libellé de la période choisie (« Ce mois-ci »…). */
  periode: string;
};

/**
 * Répartition par document du tableau de bord des décideurs (correctifs ergonomie) : en cours et
 * en retard à ce jour ; délivrés, délai moyen et encaissements sur la période. La ligne ouvre la
 * liste des demandes de ce document.
 */
export function RepartitionDocuments({ lignes, libelles, periode }: RepartitionDocumentsProps) {
  const t = useTranslations("tableauDeBord.repartition");
  const nombre = (n: number) => formaterNombre(n);
  const colonnes: ColonneTableau<ParDocumentDto>[] = [
    {
      cle: "document",
      entete: t("colonnes.document"),
      rendu: (d) => <span className="font-semibold">{libelles[d.code] ?? d.code}</span>,
    },
    { cle: "enCours", entete: t("colonnes.enCours"), rendu: (d) => nombre(d.enCours) },
    {
      cle: "enRetard",
      entete: t("colonnes.enRetard"),
      rendu: (d) =>
        d.enRetard > 0 ? (
          <span className="font-semibold text-danger">{nombre(d.enRetard)}</span>
        ) : (
          nombre(0)
        ),
    },
    { cle: "delivrees", entete: t("colonnes.delivrees"), rendu: (d) => nombre(d.delivrees) },
    {
      cle: "delai",
      entete: t("colonnes.delai"),
      rendu: (d) =>
        d.delaiMoyenJours == null
          ? t("sansDelai")
          : t("jours", { jours: formaterNombre(d.delaiMoyenJours, "fr", 1) }),
    },
    {
      cle: "encaissements",
      entete: t("colonnes.encaissements"),
      rendu: (d) => formaterGnf(d.encaissementsGnf),
    },
  ];
  return (
    <section id="repartition" aria-labelledby="repartition-titre" className="flex flex-col gap-3">
      <div className="flex flex-col gap-1">
        <h2 id="repartition-titre" className="text-xl">
          {t("titre")}
        </h2>
        <p className="text-sm text-texte-doux">{t("aide", { periode })}</p>
      </div>
      {lignes.length > 0 ? (
        <TableauDonnees
          legende={t("titre")}
          lignes={lignes}
          cleLigne={(d) => d.code}
          Lien={LienNext}
          lienLigne={(d) => `/demandes?document=${d.code}`}
          colonnes={colonnes}
        />
      ) : (
        <EtatVide titre={t("vide")} description={t("videAide")} />
      )}
    </section>
  );
}
