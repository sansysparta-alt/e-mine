import type { DemandeDto } from "@emine/api-client";
import { formaterDate } from "@emine/ui";
import Link from "next/link";
import { useTranslations } from "next-intl";

export type ListeDemandesProps = {
  demandes: DemandeDto[];
  /** Libellé de chaque document du catalogue, par code. */
  libelles: Record<string, string>;
};

/** Adresse d'une demande : le formulaire pour un brouillon, la fiche sinon. */
export function lienDemande(d: Pick<DemandeDto, "id" | "statut">): string {
  return d.statut === "brouillon" ? `/demandes/${d.id}/modifier` : `/demandes/${d.id}`;
}

/** Liste des demandes avec numéro, document, statut et date (US-03.5). */
export function ListeDemandes({ demandes, libelles }: ListeDemandesProps) {
  const t = useTranslations("mesDemandes");
  const tStatut = useTranslations("statuts");
  return (
    <ul className="flex flex-col gap-3">
      {demandes.map((d) => (
        <li key={d.id}>
          <Link
            href={lienDemande(d)}
            className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-bordure bg-white p-4 hover:border-royal"
          >
            <span className="flex flex-col">
              <span className="font-semibold text-royal">
                {d.numero ?? t("brouillonSansNumero")}
              </span>
              <span>{libelles[d.typeDocumentCode] ?? d.typeDocumentCode}</span>
            </span>
            <span className="flex flex-col text-left sm:text-right">
              <span className="font-semibold">
                {tStatut.has(d.statut) ? tStatut(d.statut) : d.statut}
              </span>
              <span className="text-sm text-texte-doux">
                {d.deposeLe
                  ? t("deposeeLe", { date: formaterDate(d.deposeLe) })
                  : t("creeeLe", { date: formaterDate(d.creeLe) })}
              </span>
            </span>
          </Link>
        </li>
      ))}
    </ul>
  );
}
