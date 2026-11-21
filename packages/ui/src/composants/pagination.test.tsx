import { render, screen, within } from "@testing-library/react";
import { adressePage, Pagination, pagesAffichees } from "./pagination";

const libelles = {
  zone: "Pagination",
  afficher: "Afficher",
  parPage: "par page",
  resume: (d: string, f: string, t: string) => `${d}–${f} sur ${t} demandes`,
  precedente: "Préc.",
  suivante: "Suiv.",
  page: (n: number) => `Page ${n}`,
  pageSur: (n: number, t: number) => `Page ${n} sur ${t}`,
};

describe("pagination commune", () => {
  it("abrège les numéros de page avec « … »", () => {
    expect(pagesAffichees(1, 4)).toEqual([1, 2, 3, 4]);
    expect(pagesAffichees(6, 12)).toEqual([1, "…", 5, 6, 7, "…", 12]);
    expect(pagesAffichees(1, 12)).toEqual([1, 2, 3, 4, "…", 12]);
    expect(pagesAffichees(12, 12)).toEqual([1, "…", 9, 10, 11, 12]);
  });

  it("construit l'adresse d'une page en gardant filtres et tri", () => {
    expect(adressePage("/demandes", { statut: "deposee", q: undefined }, 2, 10)).toBe(
      "/demandes?statut=deposee&page=2&taille=10",
    );
    expect(adressePage("/demandes", {}, 1, 20)).toBe("/demandes?taille=20");
  });

  it("affiche taille, résumé, page courante et précédente désactivée en page 1", () => {
    render(<Pagination chemin="/demandes" page={1} taille={10} total={36} libelles={libelles} />);
    expect(screen.getByLabelText("Afficher")).toHaveValue("10");
    expect(screen.getByRole("status")).toHaveTextContent("1–10 sur 36 demandes");
    const nav = screen.getByRole("navigation", { name: "Pagination" });
    expect(within(nav).getByRole("link", { name: "Page 1" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(within(nav).getByRole("link", { name: "Page 4" })).toHaveAttribute(
      "href",
      "/demandes?page=4&taille=10",
    );
    expect(within(nav).queryByRole("link", { name: /Préc/ })).toBeNull();
    expect(within(nav).getByRole("link", { name: /Suiv/ })).toHaveAttribute(
      "href",
      "/demandes?page=2&taille=10",
    );
  });

  it("n'affiche pas de numéros quand tout tient sur une page", () => {
    render(<Pagination chemin="/demandes" page={1} taille={20} total={3} libelles={libelles} />);
    expect(screen.queryByRole("navigation")).toBeNull();
    expect(screen.getByRole("status")).toHaveTextContent("1–3 sur 3 demandes");
  });
});
