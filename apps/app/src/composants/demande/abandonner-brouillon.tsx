"use client";

import { Alerte, Bouton, Dialogue } from "@emine/ui";
import { Trash2 } from "@emine/ui/icones";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useState, useTransition } from "react";
import { supprimerBrouillon } from "@/serveur/actions-demande";

/**
 * « Abandonner ce brouillon » depuis le formulaire (correctifs ergonomie) : confirmation, puis
 * suppression logique côté API et retour à « Mes demandes ».
 */
export function AbandonnerBrouillon({ demandeId }: { demandeId: number }) {
  const t = useTranslations("demande.abandon");
  const router = useRouter();
  const [ouvert, setOuvert] = useState(false);
  const [erreur, setErreur] = useState<string | null>(null);
  const [enCours, demarrer] = useTransition();

  function confirmer() {
    setErreur(null);
    demarrer(async () => {
      const resultat = await supprimerBrouillon(demandeId);
      if (resultat.ok) {
        router.push("/demandes?groupe=brouillons");
        return;
      }
      setErreur(resultat.erreur.message || t("echec"));
    });
  }

  return (
    <>
      <button
        type="button"
        onClick={() => setOuvert(true)}
        className="inline-flex min-h-11 items-center gap-1 rounded-lg px-3 font-semibold text-danger hover:bg-danger-fond"
      >
        <Trash2 aria-hidden="true" className="size-4" />
        {t("action")}
      </button>
      <Dialogue
        ouvert={ouvert}
        titre={t("titre")}
        surFermeture={enCours ? undefined : () => setOuvert(false)}
        actions={
          <>
            <Bouton
              type="button"
              variante="secondaire"
              onClick={() => setOuvert(false)}
              disabled={enCours}
            >
              {t("annuler")}
            </Bouton>
            <Bouton type="button" onClick={confirmer} disabled={enCours}>
              {enCours ? t("enCours") : t("confirmer")}
            </Bouton>
          </>
        }
      >
        <p>{t("texte")}</p>
        {erreur ? (
          <div className="mt-3">
            <Alerte nature="erreur" titre={t("echec")}>
              {erreur}
            </Alerte>
          </div>
        ) : null}
      </Dialogue>
    </>
  );
}
