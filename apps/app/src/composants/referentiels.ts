/**
 * Libellés des référentiels venant de la base (profils, documents, signataires, workflows), dans la
 * langue de l'interface. Cahier des charges : « chaque page et chaque e-Service existent dans les
 * deux langues ». Un seul endroit pour les traduire : aucun écran n'affiche un libellé brut.
 */

/** Traduction next-intl réduite à l'essentiel (serveur ou client). */
export type TraduireReferentiel = {
  (cle: string): string;
  has(cle: string): boolean;
};

/** Document du catalogue : libellé anglais s'il existe, sinon le libellé français. */
export function libelleDocument(
  d: { libelleFr: string; libelleEn?: string | null } | undefined,
  langue: string,
  repli = "",
): string {
  if (!d) return repli;
  return langue === "en" && d.libelleEn ? d.libelleEn : d.libelleFr;
}

/** Profil, par son code (16 profils, D2) ; repli : le libellé de la base. */
export function libelleProfil(
  code: string | null | undefined,
  repli: string | null | undefined,
  t: TraduireReferentiel,
): string {
  return code && t.has(`profils.${code}`) ? t(`profils.${code}`) : (repli ?? "");
}

/**
 * Profil désigné par son nom en base (réponses qui ne donnent pas le code : auteur d'un complément
 * ou d'une décision). Les noms en base sont ceux de `referentiels.profils` en français (V22).
 */
export function libelleProfilParNom(
  nom: string | null | undefined,
  t: TraduireReferentiel,
): string {
  return libelleProfil(nom ? CODES_PROFILS[nom] : undefined, nom, t);
}

/** Nom du profil en base → code (vérifié contre messages/fr.json par les tests). */
export const CODES_PROFILS: Record<string, string> = {
  "Administrateur technique": "ADMIN_TECH",
  "Agent du guichet unique (CPDM)": "GUICHET",
  "Agent DNM": "DNM",
  "Inspecteur des mines": "INSPECTEUR",
  "Géologue (DNG)": "GEOLOGUE",
  "Responsable fiscal": "FISCAL",
  "Directeur général / national": "DIRECTEUR",
  "Ministre / Secrétaire général": "MINISTRE",
  "Investisseur extérieur": "INVESTISSEUR",
  "Société minière": "SOCIETE_MINIERE",
  "Communauté locale": "COMMUNAUTE",
  "PME guinéenne": "PME",
  "Organismes sous tutelle": "TUTELLE",
  "Brigade anti-fraude": "BRIGADE",
  "Apprenant (cadre du Ministère)": "APPRENANT",
  Webmaster: "WEBMASTER",
};

/** Titre du signataire (D4) ; repli : le titre de la base. */
export function libelleSignataire(titre: string, t: TraduireReferentiel): string {
  const cle = SIGNATAIRES[titre];
  return cle && t.has(`signataires.${cle}`) ? t(`signataires.${cle}`) : titre;
}

/** Workflow (famille de documents), par son code ; repli : le libellé de la base. */
export function libelleWorkflow(code: string, repli: string, t: TraduireReferentiel): string {
  return t.has(`workflows.${code}`) ? t(`workflows.${code}`) : repli;
}

const SIGNATAIRES: Record<string, string> = {
  Ministre: "ministre",
  "Directeur général": "directeurGeneral",
  "Directeur national": "directeurNational",
  "Chef du BNE": "chefBne",
};

/** Famille désignée par son nom (réponses sans code) ; repli : le nom de la base. */
export function libelleWorkflowParNom(nom: string, t: TraduireReferentiel): string {
  const code = CODES_WORKFLOWS[nom];
  return code ? libelleWorkflow(code, nom, t) : nom;
}

/** Nom de la famille en base → code (vérifié contre messages/fr.json par les tests). */
export const CODES_WORKFLOWS: Record<string, string> = {
  "Titres miniers": "TITRES",
  "Artisanal et comptoirs": "ARTISANAL",
  "Export et expertise": "EXPORT",
  "Attestations et copies": "ATTESTATIONS",
  "Contenu local": "CONTENU_LOCAL",
};

/**
 * Libellés affichés des pièces requises d'un document (US-02.9) : la traduction anglaise de même
 * rang si l'interface est en anglais et que la fiche en a une ; sinon le libellé français, qui reste
 * l'identifiant de la pièce déposée (jamais traduit en base).
 */
export function libellesPieces(
  d: { piecesRequises: string[]; piecesRequisesEn?: string[] | null } | undefined,
  langue: string,
): Record<string, string> {
  if (!d) return {};
  const en = langue === "en" && d.piecesRequisesEn?.length === d.piecesRequises.length;
  return Object.fromEntries(
    d.piecesRequises.map((p, i) => [p, en ? (d.piecesRequisesEn?.[i] ?? p) : p]),
  );
}

/** Service instructeur dans la langue de l'interface (repli sur le français). */
export function libelleInstruitPar(
  d: { instruitPar: string; instruitParEn?: string | null },
  langue: string,
): string {
  return langue === "en" && d.instruitParEn ? d.instruitParEn : d.instruitPar;
}
