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

  it("la croix vide le champ et relance la recherche aussitôt", () => {
    const surRecherche = vi.fn();
    render(
      <ChampRecherche
        libelle="Rechercher"
        boutonLibelle="Rechercher"
        valeur="DEM"
        surRecherche={surRecherche}
      />,
    );
    const champ = screen.getByRole("searchbox", { name: "Rechercher" });
    fireEvent.click(screen.getByRole("button", { name: "Effacer la recherche" }));
    expect(champ).toHaveValue("");
    expect(champ).toHaveFocus();
    expect(surRecherche).toHaveBeenCalledWith("");
    expect(screen.queryByRole("button", { name: "Effacer la recherche" })).toBeNull();
  });

  it("propose des suggestions sans doublon, et suit une recherche changée hors du champ", () => {
    const { container, rerender } = render(
      <ChampRecherche
        libelle="Rechercher"
        boutonLibelle="Rechercher"
        valeur="permis"
        suggestions={["Permis de recherche", "Permis de recherche", " Carte d'orpailleur "]}
        surRecherche={vi.fn()}
      />,
    );
    const options = Array.from(container.querySelectorAll("datalist option"));
    expect(options.map((o) => o.getAttribute("value"))).toEqual([
      "Permis de recherche",
      "Carte d'orpailleur",
    ]);
    rerender(
      <ChampRecherche
        libelle="Rechercher"
        boutonLibelle="Rechercher"
        suggestions={["Permis de recherche"]}
        surRecherche={vi.fn()}
      />,
    );
    expect(screen.getByRole("combobox", { name: "Rechercher" })).toHaveValue("");
  });
});
