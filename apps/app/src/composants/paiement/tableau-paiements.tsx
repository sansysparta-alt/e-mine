import type { PaiementDto } from "@emine/api-client";
import { BoutonProposerRemboursement, type ProposerRemboursement } from "./proposer-remboursement";
import {
  classesAuDessusDuLien,
  cn,
  type ComposantLien,
  formaterDateHeure,
  formaterGnf,
  TableauDonnees,
  TexteSurligne,
} from "@emine/ui";
import { Download, Eye } from "@emine/ui/icones";
import Link from "next/link";
import { useTranslations } from "next-intl";
import { formaterDatePaiement } from "./date-paiement";
import { LienRecu } from "./lien-recu";
import { adresseRecu } from "./recu";
import { BadgeStatutPaiement } from "./badge-statut-paiement";
import { plafondRemboursement, type ProfilPaiement } from "./statut";

const LienNext: ComposantLien = ({ href, children, ...props }) => (
  <Link href={href} {...props}>
    {children}
  </Link>
);

/** Adresse du détail d'un paiement (page de résultat, qui relit le statut en base). */
export function lienPaiement(p: Pick<PaiementDto, "demandeId" | "reference">): string {
  return `/demandes/${p.demandeId}/paiement/retour?reference=${encodeURIComponent(p.reference)}`;
}

/** Adresse du « Détail du paiement » de « Paiements & reçus » (historique de l'usager). */
export function lienDetailPaiement(p: Pick<PaiementDto, "reference">): string {
  return `/paiements/${encodeURIComponent(p.reference)}`;
}

const classesAction =
  "inline-flex min-h-11 items-center gap-1 rounded-lg px-2 text-sm font-semibold text-royal hover:bg-ice";

/**
 * Historique des paiements (US-04.4) : référence, objet, montant, statut au code couleur commun et
 * date ; la ligne entière ouvre le détail du paiement. Dans « Paiements & reçus » (`demande`
 * fourni) : le numéro de la demande ouvre le dossier, et la colonne Actions propose « Télécharger
 * le reçu » (ou « Voir le détail » sans reçu) : elle n'est jamais vide.
 */
export function TableauPaiements({
  paiements,
  legende,
  demande,
  recherche,
  profil = "usager",
  proposerRemboursement,
}: {
  paiements: PaiementDto[];
  /** Responsable fiscal (R7) : « Proposer un remboursement » sur un paiement payé. */
  proposerRemboursement?: ProposerRemboursement;
  /** Profil qui consulte : libellé du statut et page ouverte par la ligne. */
  profil?: ProfilPaiement;
  legende?: string;
  /** Liste de tous les paiements (« Paiements & reçus ») : demande et document de chaque ligne. */
  demande?: (p: PaiementDto) => { numero: string; document: string } | null;
  /** Texte recherché, surligné dans les lignes. */
  recherche?: string;
}) {
  const t = useTranslations("detail.paiements");
  const tActions = useTranslations("mesPaiements.actions");
  const historique = demande !== undefined;
  const surligner = (texte: string) =>
    recherche ? <TexteSurligne texte={texte} recherche={recherche} /> : texte;
  const tObjet = useTranslations("paiement.objet");
  return (
    <TableauDonnees
      legende={legende ?? t("legende")}
      lignes={paiements}
      cleLigne={(p) => p.reference}
      Lien={LienNext}
      lienLigne={historique || profil === "usager" ? lienDetailPaiement : lienPaiement}
      colonnes={[
        {
          cle: "reference",
          entete: t("colonnes.reference"),
          rendu: (p) => (
            <span className="whitespace-nowrap tabular-nums">{surligner(p.reference)}</span>
          ),
        },
        ...(demande
          ? [
              {
                cle: "demande",
                entete: t("colonnes.demande"),
                interactive: true,
                rendu: (p: PaiementDto) => {
                  const d = demande(p);
                  return d ? (
                    <span className="flex flex-col">
                      <Link
                        href={`/demandes/${p.demandeId}`}
                        className={cn(
                          classesAuDessusDuLien,
                          "font-semibold whitespace-nowrap text-royal tabular-nums underline-offset-4 hover:underline",
                        )}
                      >
                        {surligner(d.numero)}
                      </Link>
                      <span className="text-sm text-texte-doux">{surligner(d.document)}</span>
                    </span>
                  ) : null;
                },
              },
            ]
          : []),
        {
          cle: "objet",
          entete: t("colonnes.objet"),
          rendu: (p) => (tObjet.has(p.objet) ? tObjet(p.objet) : p.objet),
        },
        {
          cle: "montant",
          entete: t("colonnes.montant"),
          alignement: "droite",
          rendu: (p) => (
            <span className="whitespace-nowrap tabular-nums">{formaterGnf(p.montantGnf)}</span>
          ),
        },
        {
          cle: "statut",
          entete: t("colonnes.statut"),
          rendu: (p) => <BadgeStatutPaiement paiement={p} profil={profil} />,
        },
        {
          cle: "date",
          entete: t("colonnes.date"),
          rendu: (p) => (p.datePaiement ? formaterDatePaiement(p) : formaterDateHeure(p.creeLe)),
        },
        historique
          ? {
              cle: "actions",
              entete: t("colonnes.actions"),
              interactive: true,
              rendu: (p: PaiementDto) =>
                p.recuNumero ? (
                  <a
                    href={adresseRecu(p.reference)}
                    download={`recu-${p.recuNumero}.pdf`}
                    className={cn(classesAuDessusDuLien, classesAction)}
                  >
                    <Download aria-hidden="true" className="size-4" />
                    {tActions("telechargerRecu")}
                    <span className="sr-only"> {p.recuNumero}</span>
                  </a>
                ) : (
                  <Link
                    href={lienDetailPaiement(p)}
                    className={cn(classesAuDessusDuLien, classesAction)}
                  >
                    <Eye aria-hidden="true" className="size-4" />
                    {tActions("voir")}
                    <span className="sr-only"> {p.reference}</span>
                  </Link>
                ),
            }
          : {
              cle: "recu",
              entete: t("colonnes.recu"),
              interactive: true,
              rendu: (p: PaiementDto) =>
                p.recuNumero ? (
                  <LienRecu reference={p.reference} numero={p.recuNumero} variante="lien" />
                ) : (
                  <span className="text-texte-doux">—</span>
                ),
            },
        ...(proposerRemboursement
          ? [
              {
                cle: "actions",
                entete: t("colonnes.actions"),
                interactive: true,
                voirSiVide: tActions("voir"),
                rendu: (p: PaiementDto) =>
                  plafondRemboursement(p) > 0 ? (
                    <BoutonProposerRemboursement paiement={p} proposer={proposerRemboursement} />
                  ) : null,
              },
            ]
          : []),
      ]}
    />
  );
}
