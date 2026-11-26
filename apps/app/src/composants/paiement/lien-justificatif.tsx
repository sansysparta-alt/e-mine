"use client";

import { Bouton } from "@emine/ui";
import { Paperclip } from "@emine/ui/icones";
import { useTranslations } from "next-intl";
import { useVisualiseurDocument } from "../documents/visualiseur";

/** Adresse du justificatif d'un virement (relais BFF : le jeton reste côté serveur). */
export function adresseJustificatif(reference: string): string {
  return `/api/paiements/${encodeURIComponent(reference)}/justificatif`;
}

/**
 * Justificatif d'un virement déclaré (US-04.7) : comme toute pièce, il s'ouvre d'abord dans le
 * visualiseur du socle.
 */
export function LienJustificatif({
  reference,
  fichier,
}: {
  reference: string;
  fichier: { nomFichier: string; typeMime: string };
}) {
  const t = useTranslations("detailPaiement");
  const { ouvrir, visualiseur } = useVisualiseurDocument();
  const adresse = adresseJustificatif(reference);
  return (
    <>
      <Bouton
        type="button"
        variante="secondaire"
        onClick={() =>
          ouvrir({
            nomFichier: fichier.nomFichier,
            typeMime: fichier.typeMime,
            urlAffichage: adresse,
            urlTelechargement: adresse,
          })
        }
      >
        <Paperclip aria-hidden="true" className="size-4" />
        {t("voirJustificatif")}
        <span className="sr-only"> · {fichier.nomFichier}</span>
      </Bouton>
      {visualiseur}
    </>
  );
}
