import {
  compterATraiter,
  listerAVerifier,
  listerRemboursements,
  lireParametresDelai,
  listerFichesDocuments,
  listerWorkflows,
  type MoiDto,
} from "@emine/api-client";
import { optionsApi } from "./donnees";

/** Nombre affiché à côté d'une rubrique du menu, par clé de rubrique (`navigation/config.ts`). */
export type CompteursNavigation = Record<string, number>;

/**
 * Compteurs du menu : ce que **le profil connecté** doit faire maintenant (règle « À traiter » du
 * socle, calculée par l'API, jamais recomptée ici). Demandeur : ses demandes qui attendent une
 * action de sa part ; agent : sa file « À traiter » ; Ministre : les fiches du catalogue, les
 * workflows et la règle des délais à valider. Une rubrique dont le nombre ne peut être lu reste simplement sans compteur.
 */
export async function compteursNavigation(moi: MoiDto): Promise<CompteursNavigation> {
  const options = await optionsApi();
  const droits = new Set(moi.permissions);
  const lectures: Promise<[string, number]>[] = [];
  const aTraiter = () => compterATraiter(options).then((r) => r.data.total);
  if (!moi.interne && droits.has("DEPOSER")) {
    lectures.push(aTraiter().then((n) => ["mesDemandes", n]));
  }
  if (moi.interne && (droits.has("INSTRUIRE") || droits.has("SIGNER"))) {
    lectures.push(aTraiter().then((n) => ["aTraiter", n]));
  }
  // Responsable fiscal (US-04.9) : éléments « À vérifier » qui attendent sa décision.
  if (moi.interne && droits.has("ENCAISSER")) {
    lectures.push(
      listerAVerifier({ taille: 10 }, options).then((r) => ["aVerifier", r.data.total]),
      // Remboursements à valider, puis ordres du Trésor à enregistrer (US-04.10).
      listerRemboursements({ taille: 10 }, options).then((r) => [
        "remboursements",
        r.data.compteurs.demande + r.data.compteurs.valide,
      ]),
    );
  }
  if (droits.has("VALIDER_CATALOGUE")) {
    lectures.push(
      listerFichesDocuments({ filtre: "A_VALIDER", taille: 10 }, options).then((r) => [
        "catalogue",
        r.data.total,
      ]),
      // Gouvernance (US-02.8) : propositions de workflows et de règle des délais à valider.
      listerWorkflows({ taille: 10 }, options).then((r) => ["workflows", r.data.aValider]),
      lireParametresDelai(options).then((r) => [
        "parametres",
        r.data.versions.filter((v) => v.etat === "a_valider").length,
      ]),
    );
  }
  const resultats = await Promise.allSettled(lectures);
  return Object.fromEntries(
    resultats.flatMap((r) => (r.status === "fulfilled" && r.value[1] > 0 ? [r.value] : [])),
  );
}
