import {
  Bell,
  BookOpen,
  FilePlus2,
  FileText,
  Inbox,
  LayoutDashboard,
  type LucideIcon,
  ScrollText,
  SlidersHorizontal,
  Workflow,
} from "@emine/ui/icones";

/**
 * Configuration unique de la navigation de l'application (gabarit du socle).
 * Ajouter un écran = ajouter une ligne ici (et son libellé dans messages/*.json, clé
 * `navigation.entrees.<cle>`). Chaque entrée est filtrée selon le profil de l'utilisateur.
 */
export type EntreeConfig = {
  /** Identifiant stable, aussi clé du libellé. */
  cle: string;
  /** Module Spring Modulith concerné (documentation et regroupement). */
  module: string;
  href: string;
  icone: LucideIcon;
  /** Droit de la matrice requis (US-01.6) ; absent = tout utilisateur connecté. */
  permission?: string;
  /** Au moins un de ces droits est requis (file de travail commune au guichet et aux signataires). */
  auMoinsUnDroit?: string[];
  /** Réservé aux profils internes (true) ou externes (false) ; absent = tous. */
  interne?: boolean;
  /** Libellé différent pour les profils internes (clé de traduction). */
  cleInterne?: string;
};

export type SectionConfig = { cle: string; entrees: EntreeConfig[] };

export const NAVIGATION: SectionConfig[] = [
  {
    cle: "principal",
    entrees: [
      {
        cle: "tableauDeBord",
        module: "shared",
        href: "/tableau-de-bord",
        icone: LayoutDashboard,
      },
      {
        cle: "mesDemandes",
        cleInterne: "demandes",
        module: "demande",
        href: "/demandes",
        icone: FileText,
      },
      // Centre de notifications du demandeur (US-07.2) : les agents ne reçoivent pas de notification.
      {
        cle: "notifications",
        module: "notification",
        href: "/notifications",
        icone: Bell,
        interne: false,
      },
    ],
  },
  {
    cle: "instruction",
    entrees: [
      {
        cle: "aTraiter",
        module: "instruction",
        href: "/a-traiter",
        icone: Inbox,
        auMoinsUnDroit: ["INSTRUIRE", "SIGNER"],
        interne: true,
      },
    ],
  },
  {
    cle: "demarches",
    entrees: [
      {
        cle: "nouvelleDemande",
        module: "demande",
        href: "/demandes/nouvelle",
        icone: FilePlus2,
        permission: "DEPOSER",
      },
    ],
  },
  {
    cle: "administration",
    entrees: [
      // Catalogue et tarifs (US-02.2) : l'administrateur propose, le Ministre approuve.
      {
        cle: "catalogue",
        module: "catalogue",
        href: "/catalogue",
        icone: BookOpen,
        auMoinsUnDroit: ["PARAMETRER", "VALIDER_CATALOGUE"],
        interne: true,
      },
      // Workflows (US-02.5) : étapes et intervenants affichés, modifiés par l'administrateur.
      {
        cle: "workflows",
        module: "catalogue",
        href: "/workflows",
        icone: Workflow,
        auMoinsUnDroit: ["PARAMETRER", "VALIDER_CATALOGUE"],
        interne: true,
      },
      // Délais et notifications (US-02.6, US-02.7) : règle des délais, canaux par événement.
      {
        cle: "parametres",
        module: "demande",
        href: "/parametres",
        icone: SlidersHorizontal,
        auMoinsUnDroit: ["PARAMETRER", "VALIDER_CATALOGUE"],
        interne: true,
      },
      {
        cle: "journalAudit",
        module: "audit",
        href: "/journal-audit",
        icone: ScrollText,
        permission: "PARAMETRER",
        interne: true,
      },
    ],
  },
];

export type Profil = { permissions: string[]; interne: boolean };

/** Entrée visible pour ce profil ? */
export function visible(e: EntreeConfig, profil: Profil): boolean {
  if (e.permission && !profil.permissions.includes(e.permission)) return false;
  if (e.auMoinsUnDroit && !e.auMoinsUnDroit.some((d) => profil.permissions.includes(d))) {
    return false;
  }
  if (e.interne !== undefined && e.interne !== profil.interne) return false;
  return true;
}

/** Sections filtrées selon le profil (les sections vides disparaissent). */
export function navigationPour(profil: Profil, config = NAVIGATION): SectionConfig[] {
  return config
    .map((s) => ({ ...s, entrees: s.entrees.filter((e) => visible(e, profil)) }))
    .filter((s) => s.entrees.length > 0);
}

/**
 * Entrée correspondant à l'adresse courante : la plus longue adresse de la configuration qui en
 * est le préfixe (« /demandes/12 » → « Mes demandes », « /demandes/nouvelle » → « Nouvelle
 * demande »).
 */
export function entreeActive(chemin: string, sections: SectionConfig[]): EntreeConfig | undefined {
  return sections
    .flatMap((s) => s.entrees)
    .filter((e) => chemin === e.href || chemin.startsWith(`${e.href}/`))
    .sort((a, b) => b.href.length - a.href.length)[0];
}
