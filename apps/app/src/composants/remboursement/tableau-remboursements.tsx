"use client";

import type { RemboursementDto } from "@emine/api-client";
import {
  classesReference,
  BadgeStatut,
  type ComposantLien,
  formaterDate,
  formaterGnf,
  TableauDonnees,
} from "@emine/ui";
import Link from "next/link";
import { useTranslations } from "next-intl";
import { deciderRemboursement } from "@/serveur/actions-remboursements";
import { ActionsRemboursement, actionsRemboursement } from "./actions-remboursement";
import { presentationStatutRemboursement } from "./statut";

const LienNext: ComposantLien = ({ href, children, ...props }) => (
  <Link href={href} {...props}>
    {children}
  </Link>
);

/**
 * Remboursements du Responsable fiscal (US-04.10) : à valider et à exécuter d'abord. La ligne ouvre
 * la demande concernée ; les actions passent au-dessus du lien de ligne.
 */
export function TableauRemboursements({ remboursements }: { remboursements: RemboursementDto[] }) {
  const t = useTranslations("remboursements");
  const tCommun = useTranslations("commun");
  return (
    <TableauDonnees
      legende={t("titre")}
      lignes={remboursements}
      cleLigne={(r) => r.id}
      Lien={LienNext}
      lienLigne={(r) => (r.demandeId != null ? `/demandes/${r.demandeId}` : "/remboursements")}
      colonnes={[
        {
          cle: "numero",
          entete: t("colonnes.numero"),
          rendu: (r) => (
            <span className="flex flex-col">
              <span className="font-semibold whitespace-nowrap text-navy tabular-nums">
                {r.numero}
              </span>
              <span className="text-sm text-texte-doux">
                {t("demandeLe", { date: formaterDate(r.demandeLe) })}
              </span>
            </span>
          ),
        },
        {
          cle: "beneficiaire",
          entete: t("colonnes.beneficiaire"),
          rendu: (r) => (
            <span className="flex flex-col">
              <span>{r.beneficiaire ?? "—"}</span>
              <span className="flex flex-wrap gap-x-1 text-sm text-texte-doux">
                {r.numeroDemande ? (
                  <span className={classesReference}>{r.numeroDemande} ·</span>
                ) : null}
                <span className={classesReference}>{r.referencePaiement}</span>
              </span>
            </span>
          ),
        },
        {
          cle: "motif",
          entete: t("colonnes.motif"),
          rendu: (r) => (
            <span className="flex flex-col">
              <span>{t(`motifs.${r.motif}`)}</span>
              {r.explication ? (
                <span className="text-sm text-texte-doux">{r.explication}</span>
              ) : null}
            </span>
          ),
        },
        {
          cle: "montant",
          entete: t("colonnes.montant"),
          alignement: "droite",
          rendu: (r) => (
            <span className="font-semibold whitespace-nowrap tabular-nums">
              {formaterGnf(r.montantGnf)}
            </span>
          ),
        },
        {
          cle: "statut",
          entete: t("colonnes.statut"),
          rendu: (r) => {
            const p = presentationStatutRemboursement(r.statut, true);
            return (
              <span className="flex flex-col items-start gap-1">
                <BadgeStatut ton={p.ton}>{t(`statuts.${p.cle}`)}</BadgeStatut>
                {r.referenceOrdre ? (
                  <span className="text-sm text-texte-doux">
                    {t("ordreDu", { ordre: r.referenceOrdre })}
                  </span>
                ) : null}
                {r.motifRefus ? (
                  <span className="text-sm text-texte-doux">
                    {t("motifRefus", { motif: r.motifRefus })}
                  </span>
                ) : null}
              </span>
            );
          },
        },
        {
          cle: "actions",
          entete: t("colonnes.actions"),
          interactive: true,
          voirSiVide: tCommun("voirDetail"),
          rendu: (r) =>
            actionsRemboursement(r.statut).length > 0 ? (
              <ActionsRemboursement remboursement={r} decider={deciderRemboursement} />
            ) : null,
        },
      ]}
    />
  );
}
