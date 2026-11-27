import {
  compterATraiter,
  listerAVerifier,
  listerRemboursements,
  listerSignalementsADecider,
  lireParametresDelai,
  lireRegleRemboursement,
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
 * workflows et les règles (délais, remboursement) à valider. Une rubrique dont le nombre ne peut être lu reste simplement sans compteur.
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
  // Signataire (US-06.8) : signalements de la Brigade sur ses documents, à décider.
  if (moi.interne && droits.has("SIGNER")) {
    lectures.push(
      listerSignalementsADecider({ taille: 10 }, options).then((r) => [
        "signalements",
        r.data.total,
      ]),
    );
  }
  // Responsable fiscal (US-04.9) : éléments « À vérifier » qui attendent sa décision.
  if (moi.interne && droits.has("ENCAISSER")) {
    lectures.push(
      listerAVerifier({ taille: 10 }, options).then((r) => ["aVerifier", r.data.total]),
    );
  }
  // Remboursements (US-04.18) : propositions à valider (Directeur), ordres du Trésor à enregistrer
  // (Responsable fiscal) ; règle « À traiter » de l'API.
  if (moi.interne && (droits.has("ENCAISSER") || droits.has("VALIDER_REMBOURSEMENT"))) {
    lectures.push(
      listerRemboursements({ taille: 10 }, options).then((r) => [
        "remboursements",
        r.data.compteurs.aTraiter,
      ]),
    );
  }
  if (droits.has("VALIDER_CATALOGUE")) {
    lectures.push(
      listerFichesDocuments({ filtre: "A_VALIDER", taille: 10 }, options).then((r) => [
        "catalogue",
        r.data.total,
      ]),
      // Gouvernance (US-02.8, US-02.10) : propositions de workflows, de règle des délais et de
      // règle de remboursement à valider (les deux règles sont dans la rubrique « Paramètres »).
      listerWorkflows({ taille: 10 }, options).then((r) => ["workflows", r.data.aValider]),
      Promise.all([lireParametresDelai(options), lireRegleRemboursement(options)]).then(
        ([delais, remboursement]) => [
          "parametres",
          [...delais.data.versions, ...remboursement.data.versions].filter(
            (v) => v.etat === "a_valider",
          ).length,
        ],
      ),
    );
  }
  const resultats = await Promise.allSettled(lectures);
  return Object.fromEntries(
    resultats.flatMap((r) => (r.status === "fulfilled" && r.value[1] > 0 ? [r.value] : [])),
  );
}
