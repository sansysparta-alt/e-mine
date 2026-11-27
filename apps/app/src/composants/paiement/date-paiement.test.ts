import { formaterDatePaiement } from "./date-paiement";

const n = (s: string) => s.replace(/\s/g, " ");

describe("date d'un paiement payé (US-04.14)", () => {
  it("un virement n'affiche que la date du relevé, jamais « à 00:00 »", () => {
    expect(
      n(formaterDatePaiement({ moyen: "VIREMENT", datePaiement: "2026-11-05T00:00:00Z" })),
    ).toBe("05/11/2026");
  });

  it("un paiement TrésorPay garde son heure de Conakry", () => {
    expect(
      n(formaterDatePaiement({ moyen: "TRESORPAY", datePaiement: "2026-11-05T10:30:00Z" })),
    ).toBe("05/11/2026 à 10:30");
  });

  it("sans date de paiement, rien n'est affiché", () => {
    expect(formaterDatePaiement({ moyen: "VIREMENT", datePaiement: null })).toBe("");
  });
});
