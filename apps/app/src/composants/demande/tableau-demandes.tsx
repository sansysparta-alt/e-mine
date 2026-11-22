import type { DemandeDto } from "@emine/api-client";
import { type ComposantLien, formaterDate, type SensTri, TableauDonnees } from "@emine/ui";
import Link from "next/link";
import { useTranslations } from "next-intl";
import { StatutDemande } from "./statut-demande";

export type TableauDemandesProps = {
  demandes: DemandeDto[];
  /** Libellé de chaque document du catalogue, par code. */
  libelles: Record<string, string>;
  legende: string;
  tri?: { colonne: string; sens: SensTri };
  lienTri?: (colonne: string) => string;
  /** Colonnes triables (selon ce que l'API sait trier). */
  triables?: string[];
  /** Profil interne : libellés de statut de l'agent (sinon ceux du demandeur). */
  interne?: boolean;
};

const LienNext: ComposantLien = ({ href, children, ...props }) => (
  <Link href={href} {...props}>
    {children}
  </Link>
);

/** Adresse d'une demande : le formulaire pour un brouillon, la fiche sinon. */
export function lienDemande(d: Pick<DemandeDto, "id" | "statut">): string {
  return d.statut === "brouillon" ? `/demandes/${d.id}/modifier` : `/demandes/${d.id}`;
}

/**
 * Tableau des demandes (US-03.5) : numéro, document, statut et date, sur le socle ; la ligne
 * entière ouvre la demande (formulaire pour un brouillon, fiche sinon).
 */
export function TableauDemandes({
  demandes,
  libelles,
  legende,
  tri,
  lienTri,
  triables = [],
  interne = false,
}: TableauDemandesProps) {
  const t = useTranslations("mesDemandes");
  return (
    <TableauDonnees
      legende={legende}
      lignes={demandes}
      cleLigne={(d) => d.id}
      tri={tri}
      lienTri={lienTri}
      Lien={LienNext}
      lienLigne={lienDemande}
      libellesTri={{
        trier: t("tri.trier"),
        croissant: t("tri.croissant"),
        decroissant: t("tri.decroissant"),
      }}
      colonnes={[
        {
          cle: "numero",
          entete: t("colonnes.numero"),
          triable: triables.includes("numero"),
          rendu: (d) => d.numero ?? t("brouillonSansNumero"),
        },
        {
          cle: "document",
          entete: t("colonnes.document"),
          rendu: (d) => libelles[d.typeDocumentCode] ?? d.typeDocumentCode,
        },
        {
          cle: "statut",
          entete: t("colonnes.statut"),
          triable: triables.includes("statut"),
          rendu: (d) => <StatutDemande demande={d} interne={interne} />,
        },
        {
          cle: "date",
          entete: t("colonnes.date"),
          triable: triables.includes("date"),
          rendu: (d) =>
            d.deposeLe
              ? t("deposeeLe", { date: formaterDate(d.deposeLe) })
              : t("creeeLe", { date: formaterDate(d.creeLe) }),
        },
      ]}
    />
  );
}
