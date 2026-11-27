import { render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import en from "../../../messages/en.json";
import fr from "../../../messages/fr.json";
import { HistoriqueVersions, LigneMotif, type VersionGouvernee } from "./gouvernance";

const version: VersionGouvernee = {
  version: 2,
  etat: "refusee",
  dateEffet: "2026-12-01",
  motif: "Délai du guichet porté à 3 jours",
  motifRefus: "À revoir avec la DNM",
  proposeePar: "Admin Test",
  proposeeLe: "2026-11-20T10:00:00Z",
  decideePar: "Ministre Test",
  decideeLe: "2026-11-21T10:00:00Z",
};

describe("motif des propositions (lot 5)", () => {
  it("précède le motif de son étiquette, avec la ponctuation française", () => {
    const { container } = render(
      <NextIntlClientProvider locale="fr" messages={fr}>
        <HistoriqueVersions versions={[version]} decideur={false} contenu={() => null} />
      </NextIntlClientProvider>,
    );
    expect(container.textContent).toContain("Motif\u00a0: Délai du guichet porté à 3 jours");
    expect(container.textContent).toContain("Motif du refus\u00a0: À revoir avec la DNM");
  });

  it("suit la ponctuation anglaise", () => {
    render(
      <NextIntlClientProvider locale="en" messages={en}>
        <LigneMotif texte="Shorter filing time" />
      </NextIntlClientProvider>,
    );
    expect(screen.getByText("Reason:")).toHaveClass("font-semibold");
    expect(screen.getByText("Reason:").parentElement).toHaveTextContent(
      "Reason: Shorter filing time",
    );
  });
});
