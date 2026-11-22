import { TAILLES_PAGE, tailleAutorisee, tailleDePagePour } from "./taille-page";

describe("tailles de page de l'API", () => {
  it("demande toujours une taille acceptée par l'API pour une courte liste", () => {
    expect(tailleDePagePour(5)).toBe(10);
    expect(tailleDePagePour(10)).toBe(10);
    expect(tailleDePagePour(11)).toBe(20);
    expect(tailleDePagePour(500)).toBe(100);
    for (const n of [1, 5, 15, 42, 99]) expect(TAILLES_PAGE).toContain(tailleDePagePour(n));
  });

  it("refuse une taille non autorisée lue dans l'adresse", () => {
    expect(tailleAutorisee(50)).toBe(50);
    expect(tailleAutorisee(5)).toBe(20);
    expect(tailleAutorisee(7, 10)).toBe(10);
  });
});
