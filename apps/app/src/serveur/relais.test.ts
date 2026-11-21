import { identifiant, origineAutorisee } from "./relais";

describe("relais BFF", () => {
  const app = "http://localhost:3001";

  it("accepte une modification venant de l'application", () => {
    const r = new Request(`${app}/api/x`, { method: "POST", headers: { origin: app } });
    expect(origineAutorisee(r, app)).toBe(true);
  });

  it("refuse une modification venant d'un autre site ou sans origine", () => {
    const pirate = new Request(`${app}/api/x`, {
      method: "DELETE",
      headers: { origin: "https://site-pirate.test" },
    });
    expect(origineAutorisee(pirate, app)).toBe(false);
    expect(origineAutorisee(new Request(`${app}/api/x`, { method: "POST" }), app)).toBe(false);
  });

  it("laisse passer les lectures", () => {
    expect(origineAutorisee(new Request(`${app}/api/x`), app)).toBe(true);
  });

  it("ne relaie que des identifiants numériques", () => {
    expect(identifiant("42")).toBe("42");
    expect(identifiant("../admin")).toBeNull();
    expect(identifiant("1;2")).toBeNull();
  });
});
