import { STATUTS } from "./parametres-liste";
import { TON_PAR_STATUT, tonStatut } from "./statut";

describe("couleur des statuts de demande", () => {
  it("chaque statut a un ton, selon le code commun", () => {
    for (const s of STATUTS) expect(TON_PAR_STATUT[s]).toBeDefined();
    expect(tonStatut("brouillon")).toBe("neutre");
    expect(tonStatut("en_instruction")).toBe("info");
    expect(tonStatut("complement_requis")).toBe("avertissement");
    expect(tonStatut("delivree")).toBe("succes");
    expect(tonStatut("rejetee")).toBe("danger");
    expect(tonStatut("inconnu")).toBe("neutre");
  });
});
