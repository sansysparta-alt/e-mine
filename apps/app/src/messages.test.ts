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
});
