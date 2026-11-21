"use client";

import { type ComposantLien, GabaritApplication } from "@emine/ui";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useTranslations } from "next-intl";
import type { ReactNode } from "react";
import { FormulaireDeconnexion } from "@/composants/session/formulaire-deconnexion";
import { SurveillanceSession } from "@/composants/session/surveillance-session";
import { entreeActive, navigationPour, type Profil } from "./config";

const LienNext: ComposantLien = ({ href, children, ...props }) => (
  <Link href={href} {...props}>
    {children}
  </Link>
);

export type NavigationApplicationProps = {
  utilisateur: { nomComplet: string; profil: string };
  profil: Profil;
  children: ReactNode;
};

/**
 * Gabarit de l'espace connecté : navigation filtrée selon le profil, titre de la page déduit de
 * la configuration, utilisateur, déconnexion et surveillance de la session.
 */
export function NavigationApplication({
  utilisateur,
  profil,
  children,
}: NavigationApplicationProps) {
  const t = useTranslations("navigation");
  const chemin = usePathname();
  const sections = navigationPour(profil);
  const active = entreeActive(chemin, sections);
  const libelle = (cle: string, cleInterne?: string) =>
    t(`entrees.${profil.interne && cleInterne ? cleInterne : cle}`);

  return (
    <GabaritApplication
      marque={t("marque")}
      sousTitre={t("sousTitre")}
      titre={active ? libelle(active.cle, active.cleInterne) : t("marque")}
      utilisateur={utilisateur}
      actionUtilisateur={<FormulaireDeconnexion libelle={t("deconnexion")} />}
      libelles={{
        navigation: t("titre"),
        ouvrirMenu: t("ouvrirMenu"),
        fermerMenu: t("fermerMenu"),
        reduireMenu: t("reduireMenu"),
        deplierMenu: t("deplierMenu"),
      }}
      Lien={LienNext}
      sections={sections.map((s) => ({
        cle: s.cle,
        titre: t(`sections.${s.cle}`),
        entrees: s.entrees.map((e) => ({
          cle: e.cle,
          href: e.href,
          libelle: libelle(e.cle, e.cleInterne),
          icone: e.icone,
          actif: active?.cle === e.cle,
        })),
      }))}
    >
      <SurveillanceSession />
      {children}
    </GabaritApplication>
  );
}
