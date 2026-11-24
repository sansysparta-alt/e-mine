import { act, fireEvent, render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import fr from "../../../messages/fr.json";
import { lireRecentes } from "../recherche/recentes";
import { RechercheListe } from "./recherche-liste";

const enFrancais = (enfant: ReactNode) =>
  render(
    <NextIntlClientProvider locale="fr" messages={fr}>
      {enfant}
    </NextIntlClientProvider>,
  );

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
    localStorage.clear();
    enFrancais(<RechercheListe libelle="Rechercher" boutonLibelle="Rechercher" />);
    fireEvent.change(screen.getByRole("searchbox"), { target: { value: "DEM-2026" } });
    act(() => vi.advanceTimersByTime(300));
    expect(replace).toHaveBeenCalledWith("/demandes?groupe=aTraiter&taille=20&q=DEM-2026", {
      scroll: false,
    });
    // La recherche est gardée parmi les récentes de cette liste (proposées ensuite en suggestion).
    expect(lireRecentes("emine.recherches-recentes:/demandes")).toEqual(["DEM-2026"]);
    expect(screen.getByRole("button", { name: "Effacer la recherche" })).toBeVisible();
  });
});
