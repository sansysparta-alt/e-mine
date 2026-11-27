import { formaterDate, formaterDateHeure } from "@emine/ui";

/**
 * Date d'un paiement payé (US-04.14) : un virement n'a que la date du relevé bancaire, affichée
 * seule (« 05/11/2026 », jamais « à 00:00 ») ; un paiement TrésorPay garde son heure de Conakry.
 */
export function formaterDatePaiement(
  p: { moyen: string; datePaiement?: string | null },
  langue = "fr",
): string {
  if (!p.datePaiement) return "";
  return p.moyen === "VIREMENT"
    ? formaterDate(p.datePaiement, langue)
    : formaterDateHeure(p.datePaiement, langue);
}
