import fr from "../../../messages/fr.json";
import { prochaineEtape } from "./prochaine-etape";
import { STATUTS } from "./parametres-liste";

const guichet = { interne: true, permissions: ["INSTRUIRE", "VERIFIER"] };
const investisseur = { interne: false, permissions: ["DEPOSER", "PAYER"] };
const fiscal = { interne: true, permissions: ["ENCAISSER", "VERIFIER"] };

describe("prochaine étape selon le profil", () => {
  it("l'agent du guichet voit l'action attendue, pas le paiement du demandeur", () => {
    expect(prochaineEtape("deposee", guichet, { fraisPayes: true })).toEqual({
      cle: "agentVerifier",
      pour: "agent",
    });
    expect(fr.detail.etape.agentVerifier).toMatch(/recevable/);
    expect(fr.detail.etape.agentVerifier).toMatch(/non recevable/);
  });

  it("le demandeur voit le paiement des frais", () => {
    expect(prochaineEtape("deposee", investisseur)).toEqual({
      cle: "demandeurPayerFrais",
      pour: "demandeur",
    });
  });

  it("sans frais payés, l'agent sait qu'il ne peut pas encore déclarer la recevabilité (V8)", () => {
    expect(prochaineEtape("deposee", guichet)).toEqual({
      cle: "agentFraisAttendus",
      pour: "agent",
    });
    expect(fr.detail.etape.agentFraisAttendus).toMatch(/frais/);
  });

  it("frais payés, le demandeur attend l'examen du guichet et n'est plus invité à payer", () => {
    expect(prochaineEtape("deposee", investisseur, { fraisPayes: true })).toEqual({
      cle: "demandeurAttenteGuichet",
      pour: "demandeur",
    });
  });

  it("un profil interne sans droit d'instruire n'a pas de bloc", () => {
    expect(prochaineEtape("deposee", fiscal)).toBeNull();
  });

  it("chaque clé renvoyée a son texte en français", () => {
    const textes = fr.detail.etape as Record<string, unknown>;
    for (const s of STATUTS) {
      for (const profil of [guichet, investisseur]) {
        for (const fraisPayes of [false, true]) {
          const e = prochaineEtape(s, profil, { fraisPayes });
          if (e) expect(textes[e.cle]).toBeTypeOf("string");
        }
      }
    }
  });

  it("une demande recevable attend la décision du signataire (US-05.4)", () => {
    expect(prochaineEtape("recevable", guichet)).toEqual({
      cle: "agentAttenteDecision",
      pour: "agent",
    });
    expect(fr.detail.etape.agentAttenteDecision).toMatch(/\{signataire\}/);
  });
});
