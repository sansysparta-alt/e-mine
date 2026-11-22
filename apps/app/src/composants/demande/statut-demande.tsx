import { BadgeStatut } from "@emine/ui";
import { useTranslations } from "next-intl";
import { type DemandeAffichee, presentationStatut } from "./statut";

/**
 * Statut d'une demande : **un seul badge**, libellé et ton selon le profil qui consulte (table
 * unique `presentationStatut`), icône et texte (jamais la couleur seule).
 */
export function StatutDemande({
  demande,
  interne,
}: {
  demande: DemandeAffichee;
  interne: boolean;
}) {
  const t = useTranslations("statutsAffiches");
  const p = presentationStatut(demande, interne);
  return <BadgeStatut ton={p.ton}>{t(`${p.profil}.${p.cle}`)}</BadgeStatut>;
}
