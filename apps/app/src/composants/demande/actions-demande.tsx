"use client";

import type { DemandeDto } from "@emine/api-client";
import { Alerte, Bouton, Dialogue } from "@emine/ui";
import { CreditCard, Eye, FilePlus2, PenLine, Route, Trash2 } from "@emine/ui/icones";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useState, useTransition } from "react";
import { supprimerBrouillon } from "@/serveur/actions-demande";
import { TelechargerDocument } from "../delivrance/telecharger-document";

const classesAction =
  "inline-flex min-h-11 items-center gap-1 rounded-lg px-2 text-sm font-semibold hover:bg-ice";

/**
 * Action attendue du demandeur sur une ligne, d'après le statut (même règle que le libellé du
 * statut : `presentationStatut`) : payer les frais ou les droits, compléter le dossier. Une
 * demande dont rien n'est attendu du demandeur n'a pas d'action : la ligne ouvre la fiche.
 */
export function actionAttendue(
  d: Pick<DemandeDto, "id" | "statut" | "fraisPayes">,
): { cle: "payerFrais" | "payerDroits" | "completer"; href: string } | null {
  if (d.statut === "deposee" && !d.fraisPayes) {
    return { cle: "payerFrais", href: `/demandes/${d.id}/paiement` };
  }
  if (d.statut === "decision") return { cle: "payerDroits", href: `/demandes/${d.id}/paiement` };
  if (d.statut === "complement_requis") return { cle: "completer", href: `/demandes/${d.id}` };
  return null;
}

/**
 * Actions directes d'une demande dans la liste du demandeur (correctifs ergonomie) : continuer ou
 * supprimer un brouillon (confirmation, suppression logique), payer les frais ou les droits,
 * compléter, télécharger le PDF signé d'une demande délivrée (avec avertissement s'il est
 * révoqué), sinon « Suivre » : la colonne Actions n'est jamais vide.
 */
export function ActionsDemande({
  demande,
}: {
  demande: Pick<DemandeDto, "id" | "statut" | "fraisPayes" | "documentRevoque">;
}) {
  const t = useTranslations("mesDemandes.actions");
  const router = useRouter();
  const [confirmation, setConfirmation] = useState(false);
  const [erreur, setErreur] = useState<string | null>(null);
  const [enCours, demarrer] = useTransition();

  if (demande.statut === "delivree") {
    // Document révoqué : on ne propose pas de le télécharger ; « Voir » ouvre la fiche, qui
    // explique la révocation (date, autorité, motif) avant toute consultation.
    if (demande.documentRevoque) {
      return (
        <Link href={`/demandes/${demande.id}`} className={`${classesAction} text-royal`}>
          <Eye aria-hidden="true" className="size-4" />
          {t("voir")}
        </Link>
      );
    }
    return <TelechargerDocument demandeId={demande.id} />;
  }
  const attendue = actionAttendue(demande);
  if (attendue) {
    const Icone = attendue.cle === "completer" ? FilePlus2 : CreditCard;
    return (
      <Link href={attendue.href} className={`${classesAction} text-royal`}>
        <Icone aria-hidden="true" className="size-4" />
        {t(attendue.cle)}
      </Link>
    );
  }
  if (demande.statut !== "brouillon") {
    return (
      <Link href={`/demandes/${demande.id}`} className={`${classesAction} text-royal`}>
        <Route aria-hidden="true" className="size-4" />
        {t("suivre")}
      </Link>
    );
  }

  function supprimer() {
    setErreur(null);
    demarrer(async () => {
      const resultat = await supprimerBrouillon(demande.id);
      if (resultat.ok) {
        setConfirmation(false);
        router.refresh();
        return;
      }
      setErreur(resultat.erreur.message || t("echec"));
    });
  }

  return (
    <span className="flex flex-wrap items-center gap-1">
      <Link href={`/demandes/${demande.id}/modifier`} className={`${classesAction} text-royal`}>
        <PenLine aria-hidden="true" className="size-4" />
        {t("continuer")}
      </Link>
      <button
        type="button"
        onClick={() => setConfirmation(true)}
        className={`${classesAction} text-danger hover:bg-danger-fond`}
      >
        <Trash2 aria-hidden="true" className="size-4" />
        {t("supprimer")}
      </button>
      <Dialogue
        ouvert={confirmation}
        titre={t("confirmation.titre")}
        surFermeture={enCours ? undefined : () => setConfirmation(false)}
        actions={
          <>
            <Bouton
              type="button"
              variante="secondaire"
              onClick={() => setConfirmation(false)}
              disabled={enCours}
            >
              {t("confirmation.annuler")}
            </Bouton>
            <Bouton type="button" onClick={supprimer} disabled={enCours}>
              {enCours ? t("confirmation.enCours") : t("confirmation.confirmer")}
            </Bouton>
          </>
        }
      >
        <p>{t("confirmation.texte")}</p>
        {erreur ? (
          <div className="mt-3">
            <Alerte nature="erreur" titre={t("echec")}>
              {erreur}
            </Alerte>
          </div>
        ) : null}
      </Dialogue>
    </span>
  );
}
