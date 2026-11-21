import {
  compter,
  GROUPES_AGENT,
  GROUPES_DEMANDEUR,
  statutsFiltres,
  statutsVisibles,
} from "./filtres";
import { STATUTS } from "./parametres-liste";

describe("filtres de statut par profil", () => {
  it("proposent au plus 5 filtres métier, couvrant chaque statut une seule fois", () => {
    for (const [groupes, interne] of [
      [GROUPES_DEMANDEUR, false],
      [GROUPES_AGENT, true],
    ] as const) {
      expect(groupes.length).toBeLessThanOrEqual(5);
      const couverts = groupes.flatMap((g) => g.statuts);
      expect(new Set(couverts).size).toBe(couverts.length);
      expect([...couverts].sort()).toEqual([...statutsVisibles(interne)].sort());
    }
  });

  it("le demandeur et l'agent n'ont pas les mêmes filtres", () => {
    expect(GROUPES_DEMANDEUR.map((g) => g.cle)).toContain("actionRequise");
    expect(GROUPES_AGENT.map((g) => g.cle)).toContain("aTraiter");
    expect(GROUPES_AGENT.map((g) => g.cle)).not.toContain("brouillons");
  });

  it("un agent ne voit jamais les brouillons", () => {
    expect(statutsVisibles(true)).not.toContain("brouillon");
    expect(statutsFiltres(true, undefined, undefined)).not.toContain("brouillon");
    expect(statutsFiltres(true, undefined, "brouillon")).not.toContain("brouillon");
    expect(statutsVisibles(false)).toEqual(STATUTS);
  });

  it("le statut détaillé l'emporte sur le groupe", () => {
    expect(statutsFiltres(true, "aTraiter", undefined)).toEqual(["deposee"]);
    expect(statutsFiltres(true, "aTraiter", "rejetee")).toEqual(["rejetee"]);
    expect(statutsFiltres(false, "inconnu", undefined)).toEqual([...STATUTS]);
  });

  it("compte les demandes d'un groupe", () => {
    expect(compter({ deposee: 2, recevable: 1 }, ["deposee", "recevable", "decision"])).toBe(3);
  });
});
