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
    const agent = navigationPour({ permissions: ["INSTRUIRE"], interne: true });
    expect(cles(agent)).toEqual(["tableauDeBord", "mesDemandes"]);
    expect(agent.map((s) => s.cle)).toEqual(["principal"]); // section vide retirée
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
