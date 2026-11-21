import { act, fireEvent, render, screen } from "@testing-library/react";
import { RechercheListe } from "./recherche-liste";

const replace = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace }),
  usePathname: () => "/demandes",
  useSearchParams: () => new URLSearchParams("groupe=aTraiter&page=3&taille=20"),
}));

describe("recherche d'une liste", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    replace.mockReset();
  });
  afterEach(() => vi.useRealTimers());

  it("met l'adresse à jour pendant la saisie : q remplacé, page 1, filtres conservés", () => {
    render(<RechercheListe libelle="Rechercher" boutonLibelle="Rechercher" />);
    fireEvent.change(screen.getByRole("searchbox"), { target: { value: "DEM-2026" } });
    act(() => vi.advanceTimersByTime(300));
    expect(replace).toHaveBeenCalledWith("/demandes?groupe=aTraiter&taille=20&q=DEM-2026", {
      scroll: false,
    });
  });
});
