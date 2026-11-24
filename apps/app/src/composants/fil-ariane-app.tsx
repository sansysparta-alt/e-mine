import { type ComposantLien, type EtapeFilAriane, FilAriane } from "@emine/ui";
import Link from "next/link";
import { getTranslations } from "next-intl/server";
import { NAVIGATION } from "@/navigation/config";
import { chargerMoi } from "@/serveur/donnees";

const LienNext: ComposantLien = ({ href, children, ...props }) => (
  <Link href={href} {...props}>
    {children}
  </Link>
);

/** Rubrique du menu : sa clé (`tableauDeBord`, `mesDemandes`…), et au besoin une autre adresse. */
export type RubriqueFil = string | { cle: string; href: string };

/**
 * Fil d'Ariane de l'application (socle), au-dessus du titre de la page : rubrique du menu (libellé
 * et adresse tirés de la configuration de navigation, selon le profil), étapes intermédiaires,
 * puis la page courante. « Demandes › DEM-2026-000009 › Paiement ».
 */
export async function FilArianeApp({
  rubrique,
  parents = [],
  courante,
}: {
  rubrique: RubriqueFil;
  parents?: EtapeFilAriane[];
  courante: string;
}) {
  const [t, moi] = await Promise.all([getTranslations("navigation"), chargerMoi()]);
  const cle = typeof rubrique === "string" ? rubrique : rubrique.cle;
  const entree = NAVIGATION.flatMap((s) => s.entrees).find((e) => e.cle === cle);
  const cleLibelle = moi.interne && entree?.cleInterne ? entree.cleInterne : cle;
  const premiere: EtapeFilAriane = {
    libelle: t(`entrees.${cleLibelle}`),
    href: typeof rubrique === "string" ? entree?.href : rubrique.href,
  };
  return (
    <FilAriane
      libelle={t("filAriane")}
      Lien={LienNext}
      etapes={[premiere, ...parents, { libelle: courante }]}
    />
  );
}
