import en from "../../../messages/en.json";
import fr from "../../../messages/fr.json";
import { STATUTS } from "./parametres-liste";
import { presentationStatut } from "./statut";

const cas = (statut: string, fraisPayes = true, aTraiterParMoi = false) => ({
  statut,
  fraisPayes,
  aTraiterParMoi,
});

describe("libellés de statut selon le profil (table unique)", () => {
  it("dit au demandeur ce qu'il doit faire ou attendre", () => {
    expect(presentationStatut(cas("deposee", false, true), false)).toEqual({
      profil: "demandeur",
      cle: "fraisAPayer",
      ton: "avertissement",
    });
    expect(presentationStatut(cas("deposee", true), false).cle).toBe("verification");
    expect(presentationStatut(cas("decision", true, true), false)).toMatchObject({
      cle: "droitsAPayer",
      ton: "avertissement",
    });
    expect(presentationStatut(cas("delivree"), false)).toMatchObject({
      cle: "delivre",
      ton: "succes",
    });
  });

  it("met en orange, pour l'agent, seulement ce qu'il doit faire lui-même", () => {
    expect(presentationStatut(cas("recevable", true, true), true)).toMatchObject({
      cle: "aDecider",
      ton: "avertissement",
    });
    expect(presentationStatut(cas("recevable", true, false), true)).toMatchObject({
      cle: "attenteDecision",
      ton: "info",
    });
    expect(presentationStatut(cas("deposee", false), true)).toMatchObject({
      cle: "fraisAttendus",
      ton: "neutre",
    });
    expect(presentationStatut(cas("droits_payes", true, true), true).cle).toBe("aSigner");
  });

  it("un document révoqué est rouge pour tous, comme dans « Documents délivrés »", () => {
    for (const interne of [false, true]) {
      expect(
        presentationStatut({ ...cas("delivree", true), documentRevoque: true }, interne),
      ).toMatchObject({ cle: "documentRevoque", ton: "danger" });
      expect(presentationStatut(cas("delivree", true), interne).ton).toBe("succes");
    }
  });

  it("chaque statut a un libellé pour chaque profil, en français et en anglais", () => {
    for (const statut of STATUTS) {
      for (const interne of [false, true]) {
        for (const [frais, moi] of [
          [false, false],
          [true, false],
          [true, true],
        ] as const) {
          const p = presentationStatut(cas(statut, frais, moi), interne);
          expect(fr.statutsAffiches[p.profil]).toHaveProperty([p.cle]);
          expect(en.statutsAffiches[p.profil]).toHaveProperty([p.cle]);
        }
      }
    }
  });
});
