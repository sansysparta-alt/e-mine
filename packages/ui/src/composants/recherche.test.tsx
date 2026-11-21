import { act, fireEvent, render, screen } from "@testing-library/react";
import { ChampRecherche } from "./recherche";

describe("recherche pendant la saisie", () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it("lance la recherche après un court délai sans frappe, une seule fois", () => {
    const surRecherche = vi.fn();
    render(
      <ChampRecherche
        libelle="Rechercher"
        boutonLibelle="Rechercher"
        surRecherche={surRecherche}
      />,
    );
    const champ = screen.getByRole("searchbox", { name: "Rechercher" });
    fireEvent.change(champ, { target: { value: "D" } });
    fireEvent.change(champ, { target: { value: "DE" } });
    fireEvent.change(champ, { target: { value: " DEM " } });
    act(() => vi.advanceTimersByTime(299));
    expect(surRecherche).not.toHaveBeenCalled();
    act(() => vi.advanceTimersByTime(1));
    expect(surRecherche).toHaveBeenCalledExactlyOnceWith("DEM");
  });

  it("Entrée lance la recherche tout de suite, sans recharger la page", () => {
    const surRecherche = vi.fn();
    render(
      <ChampRecherche
        libelle="Rechercher"
        boutonLibelle="Rechercher"
        surRecherche={surRecherche}
      />,
    );
    const champ = screen.getByRole("searchbox", { name: "Rechercher" });
    fireEvent.change(champ, { target: { value: "PEX" } });
    fireEvent.submit(champ.closest("form") as HTMLFormElement);
    expect(surRecherche).toHaveBeenCalledWith("PEX");
    act(() => vi.advanceTimersByTime(500));
    expect(surRecherche).toHaveBeenCalledTimes(1);
  });

  it("sans rappel, reste un formulaire GET avec son bouton visible (sans JavaScript)", () => {
    render(
      <ChampRecherche
        libelle="Rechercher"
        boutonLibelle="Rechercher"
        conserver={{ statut: "deposee" }}
      />,
    );
    const bouton = screen.getByRole("button", { name: "Rechercher" });
    expect(bouton.className).not.toMatch(/sr-only/);
    expect(bouton.closest("form")).toHaveAttribute("method", "get");
  });
});
