"use client";

import { type ComposantLien, GabaritApplication } from "@emine/ui";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useTranslations } from "next-intl";
import { type ReactNode, useEffect, useState } from "react";
import { ClocheNotifications } from "@/composants/notification/cloche-notifications";
import { type PageRecherche, RechercheGlobale } from "@/composants/recherche/recherche-globale";
import { MenuCompte } from "@/composants/session/menu-compte";
import { SurveillanceSession } from "@/composants/session/surveillance-session";
import {
  actionPrincipalePour,
  AIDE,
  ECRANS,
  entreeActive,
  navigationPour,
  PAGES_HORS_MENU,
  type Profil,
  visible,
} from "./config";

const LienNext: ComposantLien = ({ href, children, ...props }) => (
  <Link href={href} {...props}>
    {children}
  </Link>
);

export type NavigationApplicationProps = {
  utilisateur: { nomComplet: string; profil: string };
  profil: Profil;
  /** Notifications non lues au chargement (la cloche se met ensuite à jour seule). */
  nonLues?: number;
  /** Compteurs du menu au chargement, par rubrique (relus ensuite à chaque changement de page). */
  compteurs?: Record<string, number>;
  /** Pied de page (composant serveur fourni par la disposition de l'espace). */
  piedDePage?: ReactNode;
  children: ReactNode;
};

/**
 * Gabarit de l'espace connecté : navigation filtrée selon le profil (action principale, rubriques
 * avec leurs compteurs, aide), titre de la page déduit de la configuration, recherche globale,
 * cloche, utilisateur, pied de page et surveillance de la session.
 */
export function NavigationApplication({
  utilisateur,
  profil,
  nonLues = 0,
  compteurs: compteursInitiaux = {},
  piedDePage,
  children,
}: NavigationApplicationProps) {
  const t = useTranslations("navigation");
  const chemin = usePathname();
  const sections = navigationPour(profil);
  const active = entreeActive(chemin, sections);
  const action = actionPrincipalePour(profil);
  // Pages hors des rubriques (« Mon profil », « Nouvelle demande »…) : leur titre dans la barre du
  // haut quand elles sont plus précises que la rubrique (« /demandes/nouvelle » sous « Demandes »).
  const horsMenu = PAGES_HORS_MENU.filter(
    (p) => chemin === p.href || chemin.startsWith(`${p.href}/`),
  ).sort((a, b) => b.href.length - a.href.length)[0];
  const libelle = (cle: string, cleInterne?: string) =>
    t(`entrees.${profil.interne && cleInterne ? cleInterne : cle}`);
  const titre =
    horsMenu && (!active || horsMenu.href.length > active.href.length)
      ? t(`entrees.${horsMenu.cle}`)
      : active
        ? libelle(active.cle, active.cleInterne)
        : t("marque");

  // Compteurs du menu : relus à chaque changement de page (un dossier traité sort du compte).
  const [compteurs, setCompteurs] = useState(compteursInitiaux);
  useEffect(() => {
    let actif = true;
    fetch("/api/navigation/compteurs", { cache: "no-store" })
      .then(async (r) => {
        if (r.ok && actif) setCompteurs((await r.json()) as Record<string, number>);
      })
      .catch(() => {
        // Hors ligne : les derniers compteurs connus restent affichés.
      });
    return () => {
      actif = false;
    };
  }, [chemin]);

  // Recherche globale : seulement les écrans que ce profil peut ouvrir.
  const pagesRecherche: PageRecherche[] = [
    ...sections.flatMap((s) => s.entrees),
    ...ECRANS.filter((e) => !sections.some((s) => s.entrees.includes(e)) && visible(e, profil)),
    AIDE,
  ]
    .filter((e, i, liste) => liste.findIndex((x) => x.cle === e.cle) === i)
    .map((e) => ({
      cle: e.cle,
      href: e.href,
      icone: e.icone,
      libelle: libelle(e.cle, e.cleInterne),
    }));

  return (
    <GabaritApplication
      marque={t("marque")}
      sousTitre={t("sousTitre")}
      titre={titre}
      utilisateur={utilisateur}
      actionPrincipale={
        action
          ? { libelle: libelle(action.cle), href: action.href, icone: action.icone }
          : undefined
      }
      aide={{ libelle: libelle(AIDE.cle), href: AIDE.href, icone: AIDE.icone }}
      recherche={<RechercheGlobale pages={pagesRecherche} lienDemandes="/demandes" />}
      piedDePage={piedDePage}
      actionsEntete={profil.interne ? null : <ClocheNotifications initial={nonLues} />}
      menuUtilisateur={<MenuCompte utilisateur={utilisateur} Lien={LienNext} />}
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
          compteur: compteurs[e.cle],
          libelleCompteur: t("compteur", { nombre: compteurs[e.cle] ?? 0 }),
        })),
      }))}
    >
      <SurveillanceSession />
      {children}
    </GabaritApplication>
  );
}
