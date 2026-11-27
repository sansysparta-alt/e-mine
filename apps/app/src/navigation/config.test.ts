import { actionPrincipalePour, entreeActive, NAVIGATION, navigationPour } from "./config";

const cles = (sections: ReturnType<typeof navigationPour>) =>
  sections.flatMap((s) => s.entrees.map((e) => e.cle));

describe("configuration unique de la navigation", () => {
  it("filtre les rubriques selon le profil et retire les sections vides", () => {
    // Espace de l'investisseur (cahier, diapo 29) : demandes, paiements et reçus, documents.
    expect(cles(navigationPour({ permissions: ["DEPOSER", "PAYER"], interne: false }))).toEqual([
      "tableauDeBord",
      "mesDemandes",
      "paiements",
      "documents",
    ]);
    const fiscal = navigationPour({ permissions: ["ENCAISSER"], interne: true });
    expect(cles(fiscal)).toEqual([
      "tableauDeBord",
      "mesDemandes",
      "aVerifier",
      "remboursements",
      "releves",
      "journalEncaissements",
    ]);
    // Sections vides retirées ; « Encaissements » pour le Responsable fiscal (lot 5).
    expect(fiscal.map((s) => s.cle)).toEqual(["principal", "encaissements"]);
    const guichet = navigationPour({ permissions: ["INSTRUIRE"], interne: true });
    expect(cles(guichet)).not.toContain("releves");
    // Le Directeur valide les remboursements (US-04.18) : la rubrique, sans le reste des encaissements.
    const directeur = cles(
      navigationPour({
        permissions: ["SIGNER", "VERIFIER", "VALIDER_REMBOURSEMENT"],
        interne: true,
      }),
    );
    expect(directeur).toContain("remboursements");
    expect(directeur).not.toContain("releves");
    expect(directeur).not.toContain("aVerifier");
  });

  it("montre « À traiter » aux agents internes qui instruisent ou signent (correctifs J5)", () => {
    expect(cles(navigationPour({ permissions: ["INSTRUIRE"], interne: true }))).toEqual([
      "tableauDeBord",
      "mesDemandes",
      "aTraiter",
    ]);
    // Le signataire a aussi ses signalements à décider (US-06.8).
    expect(cles(navigationPour({ permissions: ["SIGNER"], interne: true }))).toEqual([
      "tableauDeBord",
      "mesDemandes",
      "aTraiter",
      "signalements",
    ]);
    expect(cles(navigationPour({ permissions: ["INSTRUIRE"], interne: false }))).not.toContain(
      "aTraiter",
    );
    expect(cles(navigationPour({ permissions: ["ENCAISSER"], interne: true }))).not.toContain(
      "aTraiter",
    );
  });

  it("montre le journal d'audit seulement à l'administrateur interne (US-11.7)", () => {
    const admin = navigationPour({ permissions: ["PARAMETRER"], interne: true });
    expect(cles(admin)).toEqual([
      "tableauDeBord",
      "mesDemandes",
      "utilisateurs",
      "catalogue",
      "workflows",
      "parametres",
      "journalAudit",
    ]);
    const agent = navigationPour({ permissions: ["INSTRUIRE"], interne: true });
    expect(cles(agent)).not.toContain("journalAudit");
  });

  it("montre le catalogue à l'administrateur et au Ministre seulement (US-02.2)", () => {
    const ministre = navigationPour({
      permissions: ["SIGNER", "VALIDER_CATALOGUE"],
      interne: true,
    });
    expect(cles(ministre)).toContain("catalogue");
    expect(cles(ministre)).not.toContain("journalAudit");
    for (const droit of ["INSTRUIRE", "SIGNER", "DEPOSER"]) {
      expect(
        cles(navigationPour({ permissions: [droit], interne: droit !== "DEPOSER" })),
      ).not.toContain("catalogue");
    }
  });

  it("les notifications passent par la cloche, l'action principale par le bouton : hors des rubriques", () => {
    const usager = cles(navigationPour({ permissions: ["DEPOSER", "PAYER"], interne: false }));
    expect(usager).not.toContain("notifications");
    expect(usager).not.toContain("nouvelleDemande");
  });

  it("donne l'action « Nouvelle demande » aux seuls profils qui déposent", () => {
    expect(actionPrincipalePour({ permissions: ["DEPOSER"], interne: false })?.href).toBe(
      "/demandes/nouvelle",
    );
    for (const droit of ["INSTRUIRE", "SIGNER", "PARAMETRER"]) {
      expect(actionPrincipalePour({ permissions: [droit], interne: true })).toBeUndefined();
    }
  });

  it("ne montre « Demandes » qu'aux profils qui en ont l'usage (moindre privilège)", () => {
    // Communauté locale, organismes sous tutelle, apprenant, webmaster (matrice des droits).
    for (const profil of [
      { permissions: ["CADASTRE_LECTURE", "VERIFIER"], interne: false },
      { permissions: ["CADASTRE_LECTURE"], interne: true },
      { permissions: ["PAYER"], interne: true },
      { permissions: ["PUBLIER"], interne: true },
    ]) {
      expect(cles(navigationPour(profil))).toEqual(["tableauDeBord"]);
    }
  });

  it("chaque entrée a une adresse, une icône et un module", () => {
    for (const e of NAVIGATION.flatMap((s) => s.entrees)) {
      expect(e.href).toMatch(/^\//);
      expect(e.icone).toBeTruthy();
      expect(e.module).toBeTruthy();
    }
  });

  it("retrouve l'entrée active par le plus long préfixe", () => {
    const tout = navigationPour({ permissions: ["DEPOSER"], interne: false });
    expect(entreeActive("/demandes/12", tout)?.cle).toBe("mesDemandes");
    expect(entreeActive("/demandes/nouvelle", tout)?.cle).toBe("mesDemandes");
    expect(entreeActive("/tableau-de-bord", tout)?.cle).toBe("tableauDeBord");
    expect(entreeActive("/demandesxyz", tout)).toBeUndefined();
  });
});
