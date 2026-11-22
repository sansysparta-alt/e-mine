"use client";

import type { DemandeDto } from "@emine/api-client";
import { Alerte, Bouton, Dialogue } from "@emine/ui";
import { Download, PenLine, Trash2 } from "@emine/ui/icones";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useState, useTransition } from "react";
import { supprimerBrouillon } from "@/serveur/actions-demande";

const classesAction =
  "inline-flex min-h-11 items-center gap-1 rounded-lg px-2 text-sm font-semibold hover:bg-ice";

/**
 * Actions directes d'une demande dans la liste du demandeur (correctifs ergonomie) : reprendre ou
 * supprimer un brouillon (confirmation, suppression logique), télécharger le PDF signé d'une
 * demande délivrée. Rien pour les autres statuts : la ligne ouvre la fiche.
 */
export function ActionsDemande({ demande }: { demande: Pick<DemandeDto, "id" | "statut"> }) {
  const t = useTranslations("mesDemandes.actions");
  const router = useRouter();
  const [confirmation, setConfirmation] = useState(false);
  const [erreur, setErreur] = useState<string | null>(null);
  const [enCours, demarrer] = useTransition();

  if (demande.statut === "delivree") {
    return (
      <a
        href={`/api/demandes/${demande.id}/document/contenu`}
        download
        className={`${classesAction} text-royal`}
      >
        <Download aria-hidden="true" className="size-4" />
        {t("telecharger")}
      </a>
    );
  }
  if (demande.statut !== "brouillon") return null;

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
        {t("reprendre")}
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
