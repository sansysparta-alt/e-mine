import { describe, expect, it } from "vitest";
import { joursAttente, lireParametresFile } from "./file-guichet";

describe("file du guichet", () => {
  it("ouvre l'onglet « à examiner » par défaut et ignore les valeurs inconnues", () => {
    expect(lireParametresFile({}).onglet).toBe("a-examiner");
    expect(lireParametresFile({ onglet: "frais-attendus" }).onglet).toBe("frais-attendus");
    expect(lireParametresFile({ onglet: "<script>" }).onglet).toBe("a-examiner");
    expect(lireParametresFile({ taille: "7" }).taille).toBe(20);
    expect(lireParametresFile({ page: "-3" }).page).toBe(1);
  });

  it("n'affiche la confirmation que pour un numéro et un résultat valides", () => {
    expect(
      lireParametresFile({ traitee: "DEM-2026-000001", resultat: "recevable" }).traitee,
    ).toEqual({ numero: "DEM-2026-000001", resultat: "recevable" });
    expect(lireParametresFile({ traitee: "DEM-2026-000001", resultat: "autre" }).traitee).toBe(
      undefined,
    );
    expect(lireParametresFile({ traitee: "<b>x</b>", resultat: "rejetee" }).traitee).toBe(
      undefined,
    );
  });

  it("compte les jours d'attente depuis le dépôt", () => {
    const maintenant = new Date("2026-11-21T10:00:00Z");
    expect(joursAttente("2026-11-21T08:00:00Z", maintenant)).toBe(0);
    expect(joursAttente("2026-11-18T09:00:00Z", maintenant)).toBe(3);
    expect(joursAttente("2026-11-22T09:00:00Z", maintenant)).toBe(0);
  });
});
