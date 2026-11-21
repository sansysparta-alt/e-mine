import { lireParametres } from "./parametres-liste";

describe("paramètres de la liste des demandes", () => {
  it("valeurs par défaut : page 1, 20 par page, plus récentes d'abord", () => {
    expect(lireParametres({})).toEqual({
      statut: undefined,
      tri: "date",
      sens: "desc",
      page: 1,
      taille: 20,
      q: undefined,
    });
  });

  it("ignore les valeurs inconnues ou dangereuses", () => {
    const p = lireParametres({ statut: "x';--", tri: "id", taille: "7", page: "-3", q: "  " });
    expect(p).toMatchObject({ statut: undefined, tri: "date", taille: 20, page: 1, q: undefined });
  });

  it("garde des valeurs valides", () => {
    expect(
      lireParametres({
        statut: "deposee",
        tri: "numero",
        sens: "desc",
        page: "3",
        taille: "50",
        q: "PEX",
      }),
    ).toEqual({ statut: "deposee", tri: "numero", sens: "desc", page: 3, taille: 50, q: "PEX" });
  });
});
