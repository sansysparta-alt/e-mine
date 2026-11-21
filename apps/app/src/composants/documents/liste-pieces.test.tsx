import type { PieceDto } from "@emine/api-client";
import { fireEvent, render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import fr from "../../../messages/fr.json";
import { ListePieces } from "./liste-pieces";

const piece: PieceDto = {
  id: 7,
  typePiece: "EIES",
  nomFichier: "eies.pdf",
  typeMime: "application/pdf",
  tailleOctets: 2048,
  empreinteSha256: "ab".repeat(32),
  deposeLe: "2026-11-20T10:00:00Z",
};

describe("pièces consultables (fiche de la demande)", () => {
  it("ouvre la pièce dans le visualiseur par le relais BFF", () => {
    render(
      <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
        <ListePieces demandeId={41} pieces={[piece]} />
      </NextIntlClientProvider>,
    );
    fireEvent.click(screen.getByRole("button", { name: /Consulter/ }));
    expect(document.querySelector("iframe")).toHaveAttribute(
      "src",
      "/api/demandes/41/pieces/7/contenu?inline=1",
    );
    expect(screen.getByRole("link", { name: "Télécharger" })).toHaveAttribute(
      "href",
      "/api/demandes/41/pieces/7/contenu",
    );
  });
});
