import type { DemandeDto } from "@emine/api-client";
import { render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import fr from "../../../messages/fr.json";
import { TableauFile } from "./tableau-file";

const demande = {
  id: 12,
  numero: "DEM-2026-000012",
  statut: "deposee",
  typeDocumentCode: "PEX",
  demandeur: { prenom: "Fodé", nom: "Bangoura", email: "f@exemple.test", organisation: null },
  deposeLe: "2026-11-15T10:00:00Z",
  fraisPayes: true,
} as DemandeDto;

function afficher(depuis: "guichet" | "decisions", avecFrais = true) {
  render(
    <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
      <TableauFile
        demandes={[demande]}
        libelles={{ PEX: "Permis d'exploitation" }}
        legende="File"
        maintenant={new Date("2026-11-21T10:00:00Z")}
        depuis={depuis}
        avecFrais={avecFrais}
      />
    </NextIntlClientProvider>,
  );
}

describe("files de travail (correctifs J4 : parcours fluide)", () => {
  it("ouvre la fiche en retenant la file d'origine, pour y revenir d'un clic", () => {
    afficher("guichet");
    expect(screen.getByRole("link", { name: "DEM-2026-000012" })).toHaveAttribute(
      "href",
      "/demandes/12?depuis=guichet",
    );
    expect(screen.getByText("depuis 6 jours")).toBeInTheDocument();
    expect(screen.getByText("Payés")).toBeInTheDocument();
  });

  it("n'affiche pas la colonne des frais dans la file des décisions", () => {
    afficher("decisions", false);
    expect(screen.getByRole("link", { name: "DEM-2026-000012" })).toHaveAttribute(
      "href",
      "/demandes/12?depuis=decisions",
    );
    expect(screen.queryByText("Payés")).not.toBeInTheDocument();
  });
});
