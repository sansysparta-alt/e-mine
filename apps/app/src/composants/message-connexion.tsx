import { Alerte } from "@emine/ui";
import { useTranslations } from "next-intl";

type Motif = "connexion" | "indisponible" | "expiree" | "deconnecte";

/** Choisit le message à afficher sur la page de connexion d'après les paramètres d'URL. */
export function motifDepuisParametres(
  p: Record<string, string | string[] | undefined>,
): Motif | null {
  if (p.erreur === "indisponible") return "indisponible";
  if (p.erreur !== undefined) return "connexion";
  if (p.expiree !== undefined) return "expiree";
  if (p.deconnecte !== undefined) return "deconnecte";
  return null;
}

/** Message clair : ce qui s'est passé (titre) et quoi faire (conseil). */
export function MessageConnexion({ motif }: { motif: Motif | null }) {
  const t = useTranslations("connexion.messages");
  if (!motif) return null;
  const nature = motif === "deconnecte" ? "succes" : motif === "expiree" ? "info" : "erreur";
  return (
    <Alerte nature={nature} titre={t(`${motif}.titre`)}>
      {t(`${motif}.conseil`)}
    </Alerte>
  );
}
