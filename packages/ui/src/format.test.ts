import { formaterGnf } from "./format";

describe("formaterGnf", () => {
  it("sépare les milliers et ajoute la devise", () => {
    expect(formaterGnf(5_000_000).replace(/\s/g, " ")).toBe("5 000 000 GNF");
  });

  it("formate en anglais si demandé", () => {
    expect(formaterGnf(200_000, "en").replace(/\s/g, " ")).toBe("200,000 GNF");
  });
});
