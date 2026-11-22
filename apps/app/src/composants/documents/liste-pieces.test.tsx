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
  remplaceeLe: null,
  remplaceeParId: null,
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

  it("une pièce remplacée en réponse au complément apparaît sous sa remplaçante, consultable", () => {
    const ancienne: PieceDto = {
      ...piece,
      id: 5,
      nomFichier: "eies-v1.pdf",
      remplaceeLe: "2026-11-22T10:00:00Z",
      remplaceeParId: 7,
    };
    const nouvelle: PieceDto = { ...piece, deposeLe: "2026-11-21T10:00:00Z" };
    const supplement: PieceDto = {
      ...piece,
      id: 9,
      typePiece: "Accord foncier",
      nomFichier: "accord.pdf",
    };
    render(
      <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
        <ListePieces demandeId={41} pieces={[ancienne, nouvelle, supplement]} />
      </NextIntlClientProvider>,
    );
    expect(screen.getAllByText("EIES")).toHaveLength(1);
    expect(screen.getByText(/nouvelle version du 21\/11\/2026/)).toBeInTheDocument();
    const versions = screen.getByRole("list", { name: "Versions précédentes : EIES" });
    expect(versions).toHaveTextContent("Version remplacée le 22/11/2026");
    expect(versions).toHaveTextContent("eies-v1.pdf");
    expect(screen.getByText("Accord foncier")).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: /Consulter/ })).toHaveLength(3);
  });
});
