import type { PaiementDto } from "@emine/api-client";
import { Alerte, Bouton } from "@emine/ui";
import { CreditCard } from "@emine/ui/icones";
import Link from "next/link";
import { useTranslations } from "next-intl";
import { etatRemboursementDuRejet } from "./statut";

/**
 * Dernier paiement rejeté d'une demande qui n'a pas été remplacé depuis par un paiement accepté
 * du même objet (US-07.5) : c'est lui que l'usager doit comprendre. Les paiements arrivent du plus
 * récent au plus ancien (API).
 */
export function paiementRejeteAExpliquer(paiements: PaiementDto[]): PaiementDto | null {
  for (const p of paiements) {
    if (p.statut === "rejete") {
      const remplace = paiements.some(
        (q) => q.objet === p.objet && q.statut === "paye" && q.creeLe > p.creeLe,
      );
      return remplace ? null : p;
    }
  }
  return null;
}

/**
 * Paiement rejeté expliqué à l'usager (US-07.5) : le motif, ce qu'il est advenu de la somme reçue
 * (remboursée, en cours de remboursement) et ce qu'il lui reste à faire, ou rien.
 */
export function ExplicationPaiementRejete({
  paiement: p,
  resteAPayer,
  demandeId,
}: {
  paiement: PaiementDto;
  /** La somme est toujours due (frais ou droits à payer de nouveau). */
  resteAPayer: boolean;
  demandeId: number;
}) {
  const t = useTranslations("paiementRejete");
  const remb = etatRemboursementDuRejet(p);
  const r = (p.remboursements ?? []).find((x) =>
    remb === "effectue" ? x.statut === "execute" : x.statut === "demande" || x.statut === "valide",
  );
  const refus = remb ? undefined : (p.remboursements ?? []).find((x) => x.statut === "refuse");
  const objet = t(`objets.${p.objet}`);
  return (
    <Alerte
      nature={resteAPayer ? "avertissement" : "info"}
      titre={t("titre", { reference: p.reference })}
    >
      <div className="flex flex-col gap-2">
        <p>{p.motifRejet ? t("motif", { motif: p.motifRejet }) : t("sansMotif")}</p>
        <p>
          {remb === "effectue"
            ? t("effectue", { ordre: r?.referenceOrdre ?? "" })
            : remb === "enCours"
              ? t("enCours", { numero: r?.numero ?? "" })
              : refus
                ? t("refuse", { numero: refus.numero, motif: refus.motifRefus ?? "" })
                : t("aucun")}
        </p>
        <p className="font-semibold">
          {resteAPayer ? t("resteAPayer", { objet }) : t("rienAFaire")}
        </p>
        {resteAPayer ? (
          <div>
            <Bouton asChild>
              <Link href={`/demandes/${demandeId}/paiement`}>
                <CreditCard aria-hidden="true" className="size-4" />
                {t("payer", { objet })}
              </Link>
            </Bouton>
          </div>
        ) : null}
      </div>
    </Alerte>
  );
}
