import type { PaiementDto } from "@emine/api-client";
import {
  BadgeStatut,
  type ComposantLien,
  formaterDateHeure,
  formaterGnf,
  TableauDonnees,
} from "@emine/ui";
import Link from "next/link";
import { useTranslations } from "next-intl";
import { LienRecu } from "./lien-recu";
import { tonStatutPaiement } from "./statut";

const LienNext: ComposantLien = ({ href, children, ...props }) => (
  <Link href={href} {...props}>
    {children}
  </Link>
);

/** Adresse du détail d'un paiement (page de résultat, qui relit le statut en base). */
export function lienPaiement(p: Pick<PaiementDto, "demandeId" | "reference">): string {
  return `/demandes/${p.demandeId}/paiement/retour?reference=${encodeURIComponent(p.reference)}`;
}

/**
 * Historique des paiements d'une demande (US-04.4) : référence, objet, montant, statut au code
 * couleur commun et date ; la ligne entière ouvre le détail du paiement.
 */
export function TableauPaiements({ paiements }: { paiements: PaiementDto[] }) {
  const t = useTranslations("detail.paiements");
  const tObjet = useTranslations("paiement.objet");
  const tStatut = useTranslations("statutsPaiement");
  return (
    <TableauDonnees
      legende={t("legende")}
      lignes={paiements}
      cleLigne={(p) => p.reference}
      Lien={LienNext}
      lienLigne={lienPaiement}
      colonnes={[
        { cle: "reference", entete: t("colonnes.reference"), rendu: (p) => p.reference },
        {
          cle: "objet",
          entete: t("colonnes.objet"),
          rendu: (p) => (tObjet.has(p.objet) ? tObjet(p.objet) : p.objet),
        },
        {
          cle: "montant",
          entete: t("colonnes.montant"),
          alignement: "droite",
          rendu: (p) => <span className="tabular-nums">{formaterGnf(p.montantGnf)}</span>,
        },
        {
          cle: "statut",
          entete: t("colonnes.statut"),
          rendu: (p) => (
            <BadgeStatut ton={tonStatutPaiement(p.statut)}>
              {tStatut.has(p.statut) ? tStatut(p.statut) : p.statut}
            </BadgeStatut>
          ),
        },
        {
          cle: "date",
          entete: t("colonnes.date"),
          rendu: (p) => formaterDateHeure(p.datePaiement ?? p.creeLe),
        },
        {
          cle: "recu",
          entete: t("colonnes.recu"),
          interactive: true,
          rendu: (p) =>
            p.recuNumero ? (
              <LienRecu reference={p.reference} numero={p.recuNumero} variante="lien" />
            ) : (
              <span className="text-texte-doux">—</span>
            ),
        },
      ]}
    />
  );
}
