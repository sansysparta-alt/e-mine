import { act, fireEvent, render, screen } from "@testing-library/react";
import { BoutonCopier } from "./bouton-copier";

describe("BoutonCopier (lot 5)", () => {
  it("copie la valeur exacte et l'annonce aux lecteurs d'écran", async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, "clipboard", { value: { writeText }, configurable: true });
    render(<BoutonCopier valeur="GN0000" libelle="Copier : IBAN" libelleCopie="Copié : IBAN" />);
    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: "Copier : IBAN" }));
    });
    expect(writeText).toHaveBeenCalledWith("GN0000");
    expect(screen.getByRole("status")).toHaveTextContent("Copié : IBAN");
  });

  it("ne s'affiche pas sans presse-papiers", () => {
    Object.defineProperty(navigator, "clipboard", { value: undefined, configurable: true });
    render(<BoutonCopier valeur="GN0000" libelle="Copier : IBAN" libelleCopie="Copié : IBAN" />);
    expect(screen.queryByRole("button")).toBeNull();
  });
});
