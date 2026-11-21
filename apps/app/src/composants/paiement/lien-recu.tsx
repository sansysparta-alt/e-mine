import { Bouton, classesAuDessusDuLien, cn } from "@emine/ui";
import { Download } from "@emine/ui/icones";
import { useTranslations } from "next-intl";

/** Adresse de téléchargement du reçu (relais BFF : le jeton reste côté serveur). */
export function adresseRecu(reference: string): string {
  return `/api/paiements/${encodeURIComponent(reference)}/recu`;
}

/**
 * Téléchargement du reçu PDF (US-04.6). En bouton sur la page de résultat, en lien compact dans un
 * tableau (placé au-dessus du lien de ligne, règle des lignes cliquables).
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
  if (variante === "lien") {
    return (
      <a
        href={adresseRecu(reference)}
        download
        className={cn(
          classesAuDessusDuLien,
          "inline-flex min-h-11 items-center gap-1 text-royal underline-offset-4 hover:underline",
        )}
      >
        <Download aria-hidden="true" className="size-4" />
        <span>{numero}</span>
        <span className="sr-only"> · {t("telecharger")}</span>
      </a>
    );
  }
  return (
    <Bouton asChild>
      <a href={adresseRecu(reference)} download>
        <Download aria-hidden="true" className="size-4" />
        {t("telechargerNumero", { numero })}
      </a>
    </Bouton>
  );
}
