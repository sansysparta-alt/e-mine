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

describe("écrans de règles exacts (US-02.10)", () => {
  const avec = (enfant: React.ReactNode) =>
    render(
      <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
        {enfant}
      </NextIntlClientProvider>,
    );

  it("la version initiale dit son origine, sans le motif technique de la migration", () => {
    avec(
      <HistoriqueVersions
        decideur
        versions={[
          {
            version: 1,
            etat: "en_vigueur",
            dateEffet: "2026-01-01",
            motif: "Règle initiale (choix provisoires, analyse § 3, points 17 à 22)",
            proposeePar: null,
            proposeeLe: "2026-01-01T00:00:00Z",
            decideePar: null,
            decideeLe: "2026-01-01T00:00:00Z",
          },
        ]}
        contenu={() => null}
      />,
    );
    expect(screen.getByText(/Version initiale, à l’installation de la plateforme/)).toBeVisible();
    expect(screen.queryByText(/points 17 à 22/)).toBeNull();
    expect(screen.queryByText(/schéma fourni/)).toBeNull();
  });

  it("liste seulement les critères modifiés, chacun « de … à … »", async () => {
    const { ChangementsProposes } = await import("./gouvernance");
    avec(
      <ChangementsProposes
        lignes={[
          { libelle: "Le délai commence", avant: "Au dépôt", apres: "Au paiement" },
          { libelle: "Le délai est en pause", avant: "Jamais", apres: "Jamais" },
        ]}
      />,
    );
    expect(screen.getByRole("heading", { name: "Changements proposés" })).toBeVisible();
    expect(
      screen.getByText(
        (_, n) =>
          n?.tagName === "LI" &&
          n.textContent === "Le délai commence : de « Au dépôt » à « Au paiement »",
      ),
    ).toBeVisible();
    expect(screen.queryByText(/en pause/)).toBeNull();
  });
});
