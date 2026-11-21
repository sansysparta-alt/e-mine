import type { TypeDocumentDto } from "@emine/api-client";
import { render, screen, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import fr from "../../messages/fr.json";
import { ListeCatalogue, parFamille } from "./liste-catalogue";

function document(partiel: Partial<TypeDocumentDto>): TypeDocumentDto {
  return {
    code: "PEX",
    libelleFr: "Permis d'exploitation",
    libelleEn: null,
    famille: { code: "TITRES", libelle: "Titres miniers" },
    delaiJours: 60,
    renouvelable: true,
    piecesRequises: ["Étude de faisabilité", "EIES"],
    instruitPar: "DNM + Inspection",
    signataire: "Ministre",
    tarifs: {
      fraisDossierGnf: 5_000_000,
      droitsGnf: 15_000_000,
      attestationGnf: null,
      aPayerAuDepotGnf: 5_000_000,
    },
    ...partiel,
  };
}

const exc = document({
  code: "EXC",
  libelleFr: "Extrait de cadastre",
  famille: { code: "ATTESTATIONS", libelle: "Attestations et copies" },
  delaiJours: 2,
  signataire: "Directeur national",
  piecesRequises: ["Référence de la zone"],
  tarifs: {
    fraisDossierGnf: null,
    droitsGnf: null,
    attestationGnf: 200_000,
    aPayerAuDepotGnf: 200_000,
  },
});

describe("catalogue public", () => {
  it("regroupe les documents par famille dans l'ordre reçu", () => {
    const familles = parFamille([document({}), exc, document({ code: "PRC" })]);
    expect(familles.map((f) => f.code)).toEqual(["TITRES", "ATTESTATIONS"]);
    expect(familles[0]?.documents.map((d) => d.code)).toEqual(["PEX", "PRC"]);
  });

  it("affiche délai, signataire, tarifs en GNF et pièces requises", () => {
    render(
      <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
        <ListeCatalogue documents={[document({}), exc]} />
      </NextIntlClientProvider>,
    );
    expect(screen.getByRole("heading", { level: 2, name: "Titres miniers" })).toBeInTheDocument();
    const pex = screen.getByRole("heading", { level: 3, name: /Permis d'exploitation/ })
      .parentElement as HTMLElement;
    expect(within(pex).getByText("60 jours")).toBeInTheDocument();
    expect(within(pex).getByText("Ministre")).toBeInTheDocument();
    expect(within(pex).getByText(/^5\s000\s000\sGNF$/)).toBeInTheDocument();
    expect(within(pex).getByText("EIES")).toBeInTheDocument();
    expect(within(pex).queryByText("Paiement unique")).not.toBeInTheDocument();
    const attestation = screen.getByRole("heading", { level: 3, name: /Extrait de cadastre/ })
      .parentElement as HTMLElement;
    expect(within(attestation).getByText("Paiement unique")).toBeInTheDocument();
    expect(within(attestation).queryByText("Frais de dossier")).not.toBeInTheDocument();
  });
});
