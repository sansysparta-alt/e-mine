import { BadgeStatut } from "@emine/ui";
import { useTranslations } from "next-intl";
import { type PaiementAffiche, presentationStatutPaiement, type ProfilPaiement } from "./statut";

/**
 * Badge du statut d'un paiement, libellé selon le profil qui consulte (lot 5) : un seul badge,
 * icône et libellé, code couleur commun du socle.
 */
export function BadgeStatutPaiement({
  paiement,
  profil,
}: {
  paiement: PaiementAffiche;
  profil: ProfilPaiement;
}) {
  const t = useTranslations("statutsPaiementAffiches");
  const p = presentationStatutPaiement(paiement, profil);
  return <BadgeStatut ton={p.ton}>{t(p.cle)}</BadgeStatut>;
}
