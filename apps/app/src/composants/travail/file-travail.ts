import { tailleAutorisee } from "@emine/ui";
/**
 * File de travail de l'agent (correctifs J5, parcours fluides) : une seule page « À traiter », des
 * groupes définis selon le profil (règle du socle : filtres métier par profil), l'action attendue
 * pour chaque dossier, et le traitement en série (dossier suivant ouvert après chaque action).
 */
export const CLES_GROUPE = [
  "tout",
  "a-examiner",
  "sans-reponse",
  "frais-attendus",
  "a-decider",
  "a-signer",
] as const;
export type CleGroupe = (typeof CLES_GROUPE)[number];

/** Action attendue de l'agent sur un dossier ; null = rien à faire (information). */
export type ActionAttendue = "examiner" | "decider" | "signer";

/** Paramètres de la liste des demandes (API) qui définissent un groupe. */
export type ParametresGroupe = {
  aTraiter?: boolean;
  groupe?: string;
  statut?: string[];
  fraisPayes?: boolean;
};

export type Groupe = {
  cle: CleGroupe;
  /** Filtre de l'API : la règle « À traiter » est calculée par l'API (une seule définition). */
  params: ParametresGroupe;
  /** Le groupe se traite en série (bouton « Commencer le traitement »). */
  actionnable: boolean;
  /** Nombre de dossiers, donné par l'API (compteur « à traiter »), s'il est connu. */
  nombre?: number;
};

export type ProfilTravail = {
  interne: boolean;
  permissions: string[];
  profil: { code: string };
};

/** Réponse de l'API « à traiter » (même règle que la liste filtrée). */
export type CompteurATraiter = { total: number; groupes: { cle: string; nombre: number }[] };

const CLES_AGENT: readonly string[] = ["a-examiner", "sans-reponse", "a-decider", "a-signer"];

/**
 * Groupes de la file de travail de l'agent, construits à partir du compteur « à traiter » de l'API
 * (la règle n'existe qu'une fois, côté API) : « Tout » s'il a plusieurs groupes, ses groupes, et
 * pour le guichet le suivi des frais attendus (information, pas une action).
 */
export function groupesDepuis(aTraiter: CompteurATraiter, p: ProfilTravail): Groupe[] {
  if (!p.interne) return [];
  const api = aTraiter.groupes.filter((g) => CLES_AGENT.includes(g.cle));
  const groupes: Groupe[] = [];
  if (api.length > 1) {
    groupes.push({
      cle: "tout",
      params: { aTraiter: true },
      actionnable: true,
      nombre: aTraiter.total,
    });
  }
  for (const g of api) {
    groupes.push({
      cle: g.cle as CleGroupe,
      params: { groupe: g.cle },
      actionnable: true,
      nombre: g.nombre,
    });
  }
  if (p.permissions.includes("INSTRUIRE")) {
    groupes.push({
      cle: "frais-attendus",
      params: { statut: ["deposee"], fraisPayes: false },
      actionnable: false,
    });
  }
  return groupes;
}

/** Paramètres de la liste des demandes (API) pour un groupe : du plus ancien au plus récent. */
export function parametresListe(groupe: Groupe) {
  return { ...groupe.params, tri: "date" as const, sens: "asc" as const };
}

/** Action attendue de l'agent selon le statut (et les frais pour une demande déposée). */
export function actionPour(statut: string, fraisPayes: boolean): ActionAttendue | null {
  if (statut === "deposee") return fraisPayes ? "examiner" : null;
  if (statut === "recevable") return "decider";
  if (statut === "droits_payes") return "signer";
  return null;
}

/** Groupe où revenir après chaque type d'action, hors traitement en série. */
export const GROUPE_PAR_ACTION: Record<ActionAttendue, CleGroupe> = {
  examiner: "a-examiner",
  decider: "a-decider",
  signer: "a-signer",
};

const RESULTATS = [
  "recevable",
  "rejetee",
  "decision",
  "droits_payes",
  "delivree",
  "complement_requis",
  "relance",
] as const;
export type ResultatAction = (typeof RESULTATS)[number];
const NUMERO = /^DEM-\d{4}-\d{6}$/;
const NUMERO_DOCUMENT = /^[A-Z]{3}-\d{4}-\d{4,}$/;

/** Action qui vient d'être menée (message de confirmation), lue dans l'adresse et validée. */
export type ActionFaite = { numero: string; resultat: ResultatAction; document?: string };

export function lireActionFaite(p: {
  numero?: string;
  resultat?: string;
  document?: string;
}): ActionFaite | undefined {
  const resultat = RESULTATS.find((r) => r === p.resultat);
  if (!p.numero || !NUMERO.test(p.numero) || !resultat) return undefined;
  return {
    numero: p.numero,
    resultat,
    ...(p.document && NUMERO_DOCUMENT.test(p.document) ? { document: p.document } : {}),
  };
}

export type ParametresTravail = {
  groupe?: string;
  q?: string;
  page?: string;
  taille?: string;
  traitee?: string;
  resultat?: string;
  document?: string;
};

/** Paramètres de la page « À traiter », validés contre les groupes du profil. */
export function lireParametresTravail(p: ParametresTravail, groupes: Groupe[]) {
  const groupe = groupes.find((g) => g.cle === p.groupe) ?? groupes[0];
  if (!groupe) throw new Error("Aucune file de travail pour ce profil");
  const page = Math.max(1, Number.parseInt(p.page ?? "1", 10) || 1);
  const tailleDemandee = Number.parseInt(p.taille ?? "", 10);
  const taille = tailleAutorisee(tailleDemandee);
  const q = p.q?.trim().slice(0, 100) || undefined;
  const faite = lireActionFaite({ numero: p.traitee, resultat: p.resultat, document: p.document });
  return { groupe, page, taille, q, faite };
}

/** Adresse d'un dossier ouvert depuis la file (traitement en série). */
export function adresseFiche(id: number, groupe: CleGroupe): string {
  return `/demandes/${id}?file=${groupe}`;
}

/** Position du dossier dans la file et dossiers voisins (identifiants dans l'ordre de la file). */
export function positionDansFile(ids: number[], courant: number) {
  const index = ids.indexOf(courant);
  if (index < 0) return { dansLaFile: false as const, total: ids.length, premier: ids[0] ?? null };
  return {
    dansLaFile: true as const,
    total: ids.length,
    position: index + 1,
    precedent: index > 0 ? (ids[index - 1] ?? null) : null,
    suivant: index + 1 < ids.length ? (ids[index + 1] ?? null) : null,
  };
}

/**
 * Contexte du traitement en série transmis aux actions (recevabilité, décision, signature) :
 * groupe de la file et dossier à ouvrir ensuite (null = c'était le dernier).
 */
export type ContexteSerie = { groupe: CleGroupe; suivant: number | null };

/**
 * Adresse où aller après une action réussie : le dossier suivant de la file (avec la confirmation
 * de ce qui vient d'être fait), sinon la page « À traiter » avec cette confirmation.
 */
export function adresseApresAction(
  serie: ContexteSerie | null,
  groupeParDefaut: CleGroupe,
  faite: ActionFaite,
): string {
  const q = new URLSearchParams();
  if (serie?.suivant != null) {
    q.set("file", serie.groupe);
    q.set("fait", faite.numero);
    q.set("resultat", faite.resultat);
    if (faite.document) q.set("document", faite.document);
    return `/demandes/${serie.suivant}?${q.toString()}`;
  }
  q.set("groupe", serie?.groupe ?? groupeParDefaut);
  q.set("traitee", faite.numero);
  q.set("resultat", faite.resultat);
  if (faite.document) q.set("document", faite.document);
  return `/a-traiter?${q.toString()}`;
}
