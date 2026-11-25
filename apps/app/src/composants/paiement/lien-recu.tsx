"use client";

import { Bouton, classesAuDessusDuLien, cn } from "@emine/ui";
import { Eye, ReceiptText } from "@emine/ui/icones";
import { useTranslations } from "next-intl";
import { useVisualiseurDocument } from "../documents/visualiseur";
import { documentDeRecu } from "./recu";

/**
 * Reçu PDF d'un paiement (US-04.6, correctifs J4) : comme toute pièce, il s'ouvre d'abord dans le
 * visualiseur, où l'on choisit de le télécharger ou de l'afficher en plein écran. En bouton sur la
 * page de résultat, en lien compact dans un tableau (au-dessus du lien de ligne).
 */
export function LienRecu({
  reference,
  numero,
  variante = "bouton",
}: {
  reference: string;
  numero: string;
  variante?: "bouton" | "lien";
}) {
  const t = useTranslations("paiement.recu");
  const { ouvrir, visualiseur } = useVisualiseurDocument();
  const consulter = () => ouvrir(documentDeRecu(reference, numero));
  if (variante === "lien") {
    return (
      <>
        <button
          type="button"
          onClick={consulter}
          className={cn(
            classesAuDessusDuLien,
            "inline-flex min-h-11 items-center gap-1 text-royal underline-offset-4 hover:underline",
          )}
        >
          <ReceiptText aria-hidden="true" className="size-4" />
          <span>{numero}</span>
          <span className="sr-only"> · {t("voir")}</span>
        </button>
        {visualiseur}
      </>
    );
  }
  return (
    <>
      <Bouton type="button" onClick={consulter}>
        <Eye aria-hidden="true" className="size-4" />
        {t("voirNumero", { numero })}
      </Bouton>
      {visualiseur}
    </>
  );
}
