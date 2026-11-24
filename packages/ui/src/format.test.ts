import {
  formaterDate,
  formaterDateHeure,
  formaterGnf,
  formaterNombre,
  formaterSuperficie,
  formaterTaille,
} from "./format";

/** Normalise les espaces insécables (fines ou non) pour comparer. */
const ESPACES = new RegExp(`[${String.fromCharCode(0xa0)}${String.fromCharCode(0x202f)}]`, "g");
const n = (s: string) => s.replace(ESPACES, " ");

describe("formats français du socle", () => {
  it("nombres avec virgule décimale", () => {
    expect(formaterNombre(125.5)).toBe("125,5");
    expect(n(formaterNombre(1234567.891))).toBe("1 234 567,89");
  });

  it("superficie toujours à deux décimales", () => {
    expect(n(formaterSuperficie(125.5))).toBe("125,50 ha");
  });

  it("montants en GNF entiers avec espaces entre milliers", () => {
    expect(n(formaterGnf(5_000_000))).toBe("5 000 000 GNF");
    expect(n(formaterGnf(200_000, "en"))).toBe("200,000 GNF");
  });

  it("dates JJ/MM/AAAA à l'heure de Conakry (UTC+0), y compris en fin de journée UTC", () => {
    expect(formaterDate("2026-11-20T23:30:00Z")).toBe("20/11/2026");
    expect(formaterDate("2026-01-05T08:00:00Z")).toBe("05/01/2026");
    expect(formaterDateHeure("2026-11-20T10:30:00Z")).toBe("20/11/2026 à 10:30");
  });

  it("tailles de fichier", () => {
    expect(n(formaterTaille(850))).toBe("850 o");
    expect(n(formaterTaille(2048))).toBe("2 Ko");
    expect(n(formaterTaille(2.4 * 1024 * 1024))).toBe("2,4 Mo");
  });
});

describe("jours calendaires (heure de Conakry)", () => {
  it("compte la veille comme 1 jour, même moins de 24 heures plus tard", async () => {
    const { joursCalendairesDepuis } = await import("./format");
    const maintenant = new Date("2026-11-22T08:00:00Z");
    expect(joursCalendairesDepuis("2026-11-21T22:00:00Z", maintenant)).toBe(1);
    expect(joursCalendairesDepuis("2026-11-22T00:30:00Z", maintenant)).toBe(0);
    expect(joursCalendairesDepuis("2026-11-15T12:00:00Z", maintenant)).toBe(7);
    expect(joursCalendairesDepuis("2026-11-23T12:00:00Z", maintenant)).toBe(0);
  });

  it("donne l'échéance dans N jours calendaires, même tard le soir", async () => {
    const { dateDansJours } = await import("./format");
    expect(dateDansJours(12, new Date("2026-11-24T23:30:00Z"))).toBe("2026-12-06");
    expect(dateDansJours(0, new Date("2026-11-24T00:10:00Z"))).toBe("2026-11-24");
  });
});
