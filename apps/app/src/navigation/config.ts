import {
  Bell,
  BookOpen,
  BookText,
  CircleHelp,
  CreditCard,
  FileCheck2,
  FileSpreadsheet,
  FileText,
  Inbox,
  LayoutDashboard,
  type LucideIcon,
  Plus,
  ScrollText,
  SearchCheck,
  Undo2,
  SlidersHorizontal,
  Users,
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
        // Moindre privilège : seuls les profils qui déposent, traitent, encaissent ou auditent les
        // dossiers voient cette rubrique (pas la communauté, la tutelle, l'apprenant, le webmaster).
        auMoinsUnDroit: ["DEPOSER", "INSTRUIRE", "SIGNER", "ENCAISSER", "PARAMETRER"],
      },
      // Espace de l'usager (cahier, diapo 29 : « Paiements & reçus », « Documents délivrés »).
      {
        cle: "paiements",
        module: "paiement",
        href: "/paiements",
        icone: CreditCard,
        permission: "PAYER",
        interne: false,
      },
      {
        cle: "documents",
        module: "delivrance",
        href: "/documents",
        icone: FileCheck2,
        permission: "DEPOSER",
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
    // Responsable fiscal (lot 5) : relevés du compte des virements, rapprochement (US-04.8).
    cle: "encaissements",
    entrees: [
      // File du Responsable fiscal : ce que le rapprochement n'a pas confirmé (US-04.9).
      {
        cle: "aVerifier",
        module: "paiement",
        href: "/a-verifier",
        icone: SearchCheck,
        permission: "ENCAISSER",
        interne: true,
      },
      // Remboursements (US-04.10, US-04.18) : validation par le Directeur, puis ordre du Trésor
      // enregistré par le Responsable fiscal.
      {
        cle: "remboursements",
        module: "paiement",
        href: "/remboursements",
        icone: Undo2,
        auMoinsUnDroit: ["ENCAISSER", "VALIDER_REMBOURSEMENT"],
        interne: true,
      },
      {
        cle: "releves",
        module: "paiement",
        href: "/releves",
        icone: FileSpreadsheet,
        permission: "ENCAISSER",
        interne: true,
      },
      // Journal des encaissements (US-04.11) : recettes, frais remboursés, export CSV.
      {
        cle: "journalEncaissements",
        module: "paiement",
        href: "/journal-encaissements",
        icone: BookText,
        permission: "ENCAISSER",
        interne: true,
      },
    ],
  },
  {
    cle: "administration",
    entrees: [
      // Utilisateurs & rôles (espace « Administrateur technique » du cahier) : lecture seule,
      // comptes gérés dans Keycloak (D8).
      {
        cle: "utilisateurs",
        module: "admin",
        href: "/utilisateurs",
        icone: Users,
        permission: "PARAMETRER",
        interne: true,
      },
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

/**
 * Action principale, en tête du menu (bouton doré) : la première action attendue du profil. Seuls
 * les demandeurs en ont une (« Nouvelle demande ») ; un agent part de sa file « À traiter ».
 */
export const ACTIONS_PRINCIPALES: EntreeConfig[] = [
  {
    cle: "nouvelleDemande",
    module: "demande",
    href: "/demandes/nouvelle",
    icone: Plus,
    permission: "DEPOSER",
  },
];

/**
 * Centre de notifications (US-07.2), ouvert par la cloche (« Voir toutes ») : réservé aux usagers
 * externes, seuls destinataires des notifications.
 */
const NOTIFICATIONS: EntreeConfig = {
  cle: "notifications",
  module: "notification",
  href: "/notifications",
  icone: Bell,
  interne: false,
};

/**
 * Tous les écrans soumis à une règle d'accès : rubriques du menu, actions principales, centre de
 * notifications. Contrôle des adresses (`acces.ts`) et recherche globale s'appuient sur cette liste.
 */
export const ECRANS: EntreeConfig[] = [
  ...NAVIGATION.flatMap((s) => s.entrees),
  ...ACTIONS_PRINCIPALES,
  NOTIFICATIONS,
];

/** Lien d'aide, en bas du menu (« Aide & mon compte » des maquettes du cahier des charges). */
export const AIDE: EntreeConfig = {
  cle: "aide",
  module: "shared",
  href: "/aide",
  icone: CircleHelp,
};

/**
 * Pages ouvertes à tout utilisateur connecté mais absentes des rubriques (menu sous l'avatar, cloche,
 * bouton d'action, aide, pied de page) : leur titre dans la barre du haut (`navigation.entrees.<cle>`).
 * L'action principale y figure aussi : son titre reste affiché quand on l'ouvre.
 */
export const PAGES_HORS_MENU: { cle: string; href: string }[] = [
  { cle: "nouvelleDemande", href: "/demandes/nouvelle" },
  { cle: "notifications", href: "/notifications" },
  { cle: "profil", href: "/profil" },
  { cle: "aide", href: "/aide" },
  { cle: "mentionsLegales", href: "/mentions-legales" },
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

/** Action principale du profil, s'il en a une. */
export function actionPrincipalePour(profil: Profil): EntreeConfig | undefined {
  return ACTIONS_PRINCIPALES.find((e) => visible(e, profil));
}

/**
 * Entrée correspondant à l'adresse courante : la plus longue adresse de la configuration qui en
 * est le préfixe (« /demandes/12 » → « Mes demandes »).
 */
export function entreeActive(chemin: string, sections: SectionConfig[]): EntreeConfig | undefined {
  return sections
    .flatMap((s) => s.entrees)
    .filter((e) => chemin === e.href || chemin.startsWith(`${e.href}/`))
    .sort((a, b) => b.href.length - a.href.length)[0];
}
