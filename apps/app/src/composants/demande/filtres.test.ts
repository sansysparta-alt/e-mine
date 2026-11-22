import en from "../../../messages/en.json";
import fr from "../../../messages/fr.json";
import { cleLibelleVue, statutsVisibles } from "./filtres";

const VUES = ["a-traiter", "attente-demandeur", "en-cours", "brouillons", "terminees", "rejetees"];

describe("filtres de la liste des demandes (vues de l'API, correctifs J5)", () => {
  it("un agent ne voit jamais les brouillons", () => {
    expect(statutsVisibles(true)).not.toContain("brouillon");
    expect(statutsVisibles(false)).toContain("brouillon");
  });

  it("dit « À faire » au demandeur et « À traiter » à l'agent", () => {
    expect(cleLibelleVue("a-traiter", false)).toBe("a-faire");
    expect(cleLibelleVue("a-traiter", true)).toBe("a-traiter");
    expect(cleLibelleVue("en-cours", false)).toBe("en-cours");
  });

  it("chaque vue a son libellé en français et en anglais", () => {
    for (const cle of [...VUES, "a-faire"]) {
      expect(fr.mesDemandes.filtres).toHaveProperty([cle]);
      expect(en.mesDemandes.filtres).toHaveProperty([cle]);
    }
  });
});
