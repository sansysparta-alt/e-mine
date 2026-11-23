import { entreeActive, NAVIGATION, navigationPour } from "./config";

const cles = (sections: ReturnType<typeof navigationPour>) =>
  sections.flatMap((s) => s.entrees.map((e) => e.cle));

describe("configuration unique de la navigation", () => {
  it("montre la nouvelle demande seulement aux profils qui peuvent déposer", () => {
    expect(cles(navigationPour({ permissions: ["DEPOSER", "PAYER"], interne: false }))).toEqual([
      "tableauDeBord",
      "mesDemandes",
      "notifications",
      "nouvelleDemande",
    ]);
    const fiscal = navigationPour({ permissions: ["ENCAISSER"], interne: true });
    expect(cles(fiscal)).toEqual(["tableauDeBord", "mesDemandes"]);
    expect(fiscal.map((s) => s.cle)).toEqual(["principal"]); // sections vides retirées
  });

  it("montre « À traiter » aux agents internes qui instruisent ou signent (correctifs J5)", () => {
    for (const droit of ["INSTRUIRE", "SIGNER"]) {
      expect(cles(navigationPour({ permissions: [droit], interne: true }))).toEqual([
        "tableauDeBord",
        "mesDemandes",
        "aTraiter",
      ]);
    }
    expect(cles(navigationPour({ permissions: ["INSTRUIRE"], interne: false }))).not.toContain(
      "aTraiter",
    );
    expect(cles(navigationPour({ permissions: ["ENCAISSER"], interne: true }))).not.toContain(
      "aTraiter",
    );
  });

  it("montre le journal d'audit seulement à l'administrateur interne (US-11.7)", () => {
    const admin = navigationPour({ permissions: ["PARAMETRER"], interne: true });
    expect(cles(admin)).toEqual(["tableauDeBord", "mesDemandes", "catalogue", "journalAudit"]);
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

  it("montre les notifications aux seuls usagers externes, qui seuls en reçoivent (US-07.2)", () => {
    expect(cles(navigationPour({ permissions: ["DEPOSER"], interne: false }))).toContain(
      "notifications",
    );
    for (const droit of ["INSTRUIRE", "SIGNER", "PARAMETRER"]) {
      expect(cles(navigationPour({ permissions: [droit], interne: true }))).not.toContain(
        "notifications",
      );
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
    expect(entreeActive("/demandes/nouvelle", tout)?.cle).toBe("nouvelleDemande");
    expect(entreeActive("/tableau-de-bord", tout)?.cle).toBe("tableauDeBord");
    expect(entreeActive("/demandesxyz", tout)).toBeUndefined();
  });
});
