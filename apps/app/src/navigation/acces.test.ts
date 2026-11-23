import { cheminAutorise, entreeDuChemin } from "./acces";

const admin = { permissions: ["PARAMETRER", "VERIFIER"], interne: true };
const ministre = { permissions: ["SIGNER", "VALIDER_CATALOGUE"], interne: true };
const guichet = { permissions: ["INSTRUIRE"], interne: true };
const investisseur = { permissions: ["DEPOSER", "PAYER"], interne: false };

describe("accès aux écrans selon la navigation", () => {
  it("rattache une adresse à l'entrée du menu dont elle est le plus long préfixe", () => {
    expect(entreeDuChemin("/workflows/EXPORT/modifier")?.cle).toBe("workflows");
    expect(entreeDuChemin("/demandes/nouvelle")?.cle).toBe("nouvelleDemande");
    expect(entreeDuChemin("/demandes/12")?.cle).toBe("mesDemandes");
    expect(entreeDuChemin("/inconnue")).toBeUndefined();
  });

  it("ouvre workflows, paramètres et catalogue à l'administrateur et au Ministre", () => {
    for (const chemin of ["/workflows", "/workflows/EXPORT", "/parametres", "/catalogue/PEX"]) {
      expect(cheminAutorise(chemin, admin)).toBe(true);
      expect(cheminAutorise(chemin, ministre)).toBe(true);
      expect(cheminAutorise(chemin, guichet)).toBe(false);
      expect(cheminAutorise(chemin, investisseur)).toBe(false);
    }
  });

  it("réserve le journal d'audit à l'administrateur", () => {
    expect(cheminAutorise("/journal-audit", admin)).toBe(true);
    expect(cheminAutorise("/journal-audit/7", ministre)).toBe(false);
  });

  it("laisse à l'écran les adresses hors du menu ou sans restriction", () => {
    expect(cheminAutorise("/tableau-de-bord", investisseur)).toBe(true);
    expect(cheminAutorise("/demandes/12", guichet)).toBe(true);
    expect(cheminAutorise("/inconnue", investisseur)).toBe(true);
  });
});
