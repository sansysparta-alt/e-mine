import { render } from "@testing-library/react";
import { correspond, decouper, normaliser, TexteSurligne } from "./texte";

describe("recherche tolérante du socle", () => {
  it("ignore accents et majuscules", () => {
    expect(normaliser("Société MINIÈRE")).toBe("societe miniere");
    expect(correspond("Journal d'audit", "AUDIT")).toBe(true);
    expect(correspond("Délais et notifications", "delai")).toBe(true);
  });

  it("retrouve chaque mot, dans n'importe quel ordre, et une partie du numéro", () => {
    expect(correspond("Nouvelle demande", "demande nouv")).toBe(true);
    expect(correspond("DEM-2026-000009", "000009")).toBe(true);
    expect(correspond("Nouvelle demande", "demande paiement")).toBe(false);
  });

  it("surligne le texte d'origine, accents compris", () => {
    expect(decouper("Délais et délais", "delai")).toEqual([
      { texte: "Délai", surligne: true },
      { texte: "s et ", surligne: false },
      { texte: "délai", surligne: true },
      { texte: "s", surligne: false },
    ]);
    expect(decouper("Texte", "")).toEqual([{ texte: "Texte", surligne: false }]);
    const { container } = render(<TexteSurligne texte="DEM-2026-000009" recherche="0009" />);
    expect(container.querySelector("mark")?.textContent).toBe("0009");
  });
});
