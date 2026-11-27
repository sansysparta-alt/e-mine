import type { PaiementDto } from "@emine/api-client";
import { Alerte, Bouton, formaterDate, formaterGnf } from "@emine/ui";
import { CreditCard } from "@emine/ui/icones";
import Link from "next/link";
import { useTranslations } from "next-intl";

/**
 * Paiement d'une demande en attente de complément (US-04.12) : le demandeur doit payer le reste.
 * Le statut et le reste à payer viennent de l'API.
 */
export function complementAPayer(paiements: PaiementDto[]): PaiementDto | null {
  return paiements.find((p) => p.statut === "complement_attendu" && p.resteAPayerGnf) ?? null;
}

/**
 * Complément expliqué à l'usager (US-04.12) : son paiement n'est pas rejeté ; somme reçue, reste à
 * payer, motif, échéance indicative, et le bouton pour payer le reste par le circuit habituel.
 */
export function ExplicationComplement({
  paiement: p,
  demandeId,
}: {
  paiement: PaiementDto;
  demandeId: number;
}) {
  const t = useTranslations("complementPaiement");
  return (
    <Alerte
      nature="avertissement"
      titre={t("titre", {
        reference: p.reference,
        reste: formaterGnf(p.resteAPayerGnf ?? 0),
      })}
    >
      <div className="flex flex-col gap-2">
        <p>
          {t("texte", {
            recu: formaterGnf(p.montantRecuGnf ?? 0),
            du: formaterGnf(p.montantGnf),
          })}
        </p>
        {p.motifComplement ? <p>{t("motif", { motif: p.motifComplement })}</p> : null}
        {p.complementEcheance ? (
          <p>{t("echeance", { date: formaterDate(p.complementEcheance) })}</p>
        ) : null}
        <div className="mt-1">
          <Bouton asChild>
            <Link href={`/demandes/${demandeId}/paiement`}>
              <CreditCard aria-hidden="true" className="size-4" />
              {t("payer")}
            </Link>
          </Bouton>
        </div>
      </div>
    </Alerte>
  );
}
