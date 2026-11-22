"use client";

import type { DocumentDelivreDto } from "@emine/api-client";
import { Alerte, Bouton, Dialogue } from "@emine/ui";
import { FileSignature } from "@emine/ui/icones";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useState, useTransition } from "react";
import type { ResultatAction } from "@/serveur/erreurs-formulaire";
import { adresseApresAction, type ContexteSerie } from "../travail/file-travail";

export type SignerDelivrerProps = {
  demandeId: number;
  numero: string;
  /** Libellé du document du catalogue (« Permis d'exploitation »). */
  document: string;
  /** Signataire du catalogue (D4), rappelé à la personne qui signe. */
  signataire: string;
  signerEtDelivrer: (id: number) => Promise<ResultatAction<DocumentDelivreDto>>;
  /** Traitement en série : après la délivrance, le dossier suivant de la file s'ouvre. */
  serie?: ContexteSerie | null;
};

/**
 * « Signer et délivrer » (US-06.3) : ce qui va se passer, puis confirmation dans une boîte de
 * dialogue (action définitive). Le signataire revient ensuite à son espace, onglet « À signer »,
 * avec le numéro du document délivré et la demande suivante.
 */
export function SignerDelivrer({
  demandeId,
  numero,
  document,
  signataire,
  signerEtDelivrer,
  serie,
}: SignerDelivrerProps) {
  const t = useTranslations("delivrance.signer");
  const router = useRouter();
  const [confirmation, setConfirmation] = useState(false);
  const [erreur, setErreur] = useState<string | null>(null);
  const [enCours, demarrer] = useTransition();

  function envoyer() {
    setErreur(null);
    demarrer(async () => {
      const resultat = await signerEtDelivrer(demandeId);
      if (resultat.ok) {
        router.push(
          adresseApresAction(serie ?? null, "a-signer", {
            numero,
            resultat: "delivree",
            document: resultat.valeur.numero,
          }),
        );
        return;
      }
      setConfirmation(false);
      setErreur(
        resultat.erreur.code === "INDISPONIBLE" ? t("indisponible") : resultat.erreur.message,
      );
      router.refresh();
    });
  }

  return (
    <div className="flex flex-col gap-4">
      <p className="text-texte-doux">{t("signataire", { signataire })}</p>
      <ol className="flex list-decimal flex-col gap-1 pl-5 text-sm">
        <li>{t("etapes.etablir", { document })}</li>
        <li>{t("etapes.signer")}</li>
        <li>{t("etapes.archiver")}</li>
        <li>{t("etapes.remettre")}</li>
      </ol>
      {erreur ? (
        <Alerte nature="erreur" titre={t("echec")}>
          {erreur}
        </Alerte>
      ) : null}
      <div>
        <Bouton
          type="button"
          taille="grande"
          onClick={() => setConfirmation(true)}
          disabled={enCours}
          className="w-full"
        >
          <FileSignature aria-hidden="true" className="size-5" />
          {t("action")}
        </Bouton>
      </div>
      <Dialogue
        ouvert={confirmation}
        titre={t("confirmation.titre", { numero })}
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
            <Bouton type="button" onClick={envoyer} disabled={enCours}>
              {enCours ? t("enCours") : t("confirmation.confirmer")}
            </Bouton>
          </>
        }
      >
        <div className="flex flex-col gap-2">
          <p>{t("confirmation.texte", { document })}</p>
          <p className="font-semibold">{t("confirmation.definitive")}</p>
        </div>
      </Dialogue>
    </div>
  );
}
