import en from "../messages/en.json";
import fr from "../messages/fr.json";

function cles(objet: object, prefixe = ""): string[] {
  return Object.entries(objet).flatMap(([cle, valeur]) =>
    typeof valeur === "object" && valeur !== null
      ? cles(valeur, `${prefixe}${cle}.`)
      : [`${prefixe}${cle}`],
  );
}

describe("traductions de l'application", () => {
  it("chaque texte existe en français et en anglais", () => {
    expect(cles(en).sort()).toEqual(cles(fr).sort());
  });

  it("aucun texte vide", () => {
    const valeurs = (o: object): unknown[] =>
      Object.values(o).flatMap((v) => (typeof v === "object" && v ? valeurs(v) : [v]));
    expect(valeurs(fr).every((v) => typeof v === "string" && v.trim() !== "")).toBe(true);
    expect(valeurs(en).every((v) => typeof v === "string" && v.trim() !== "")).toBe(true);
  });

  it("les exemples des champs de recherche tiennent sur un mobile (30 caractères au plus)", () => {
    // Un exemple plus long est coupé dans le champ à 375 px de large (correction 8 du lot 4).
    const trop = (messages: object) =>
      cles(messages)
        .filter((c) => /recherche\.exemple$|\.placeholder$/.test(c))
        .map((c) => [c, c.split(".").reduce<unknown>((o, k) => (o as never)[k], messages)])
        .filter(([, v]) => String(v).length > 30);
    expect(trop(fr)).toEqual([]);
    expect(trop(en)).toEqual([]);
  });
});
