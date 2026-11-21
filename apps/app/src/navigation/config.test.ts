import { entreeActive, NAVIGATION, navigationPour } from "./config";

const cles = (sections: ReturnType<typeof navigationPour>) =>
  sections.flatMap((s) => s.entrees.map((e) => e.cle));

describe("configuration unique de la navigation", () => {
  it("montre la nouvelle demande seulement aux profils qui peuvent déposer", () => {
    expect(cles(navigationPour({ permissions: ["DEPOSER", "PAYER"], interne: false }))).toEqual([
      "tableauDeBord",
      "mesDemandes",
      "nouvelleDemande",
    ]);
    const fiscal = navigationPour({ permissions: ["ENCAISSER"], interne: true });
    expect(cles(fiscal)).toEqual(["tableauDeBord", "mesDemandes"]);
    expect(fiscal.map((s) => s.cle)).toEqual(["principal"]); // sections vides retirées
  });

  it("montre la file du guichet seulement aux agents internes qui instruisent (US-05.1)", () => {
    const agent = navigationPour({ permissions: ["INSTRUIRE"], interne: true });
    expect(cles(agent)).toEqual(["tableauDeBord", "mesDemandes", "fileGuichet"]);
    expect(cles(navigationPour({ permissions: ["INSTRUIRE"], interne: false }))).not.toContain(
      "fileGuichet",
    );
  });

  it("montre les décisions à prendre seulement aux profils internes qui signent (US-05.4)", () => {
    const ministre = navigationPour({ permissions: ["SIGNER"], interne: true });
    expect(cles(ministre)).toEqual(["tableauDeBord", "mesDemandes", "decisions"]);
    const guichet = navigationPour({ permissions: ["INSTRUIRE"], interne: true });
    expect(cles(guichet)).not.toContain("decisions");
  });

  it("montre le journal d'audit seulement à l'administrateur interne (US-11.7)", () => {
    const admin = navigationPour({ permissions: ["PARAMETRER"], interne: true });
    expect(cles(admin)).toEqual(["tableauDeBord", "mesDemandes", "journalAudit"]);
    const agent = navigationPour({ permissions: ["INSTRUIRE"], interne: true });
    expect(cles(agent)).not.toContain("journalAudit");
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
