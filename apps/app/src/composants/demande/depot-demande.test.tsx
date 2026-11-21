import type { DemandeDto, PieceDto } from "@emine/api-client";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import fr from "../../../messages/fr.json";
import { DepotDemande } from "./depot-demande";

const push = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));

const demande = { id: 41, statut: "brouillon" } as DemandeDto;
const eies: PieceDto = {
  id: 7,
  typePiece: "EIES",
  nomFichier: "eies.pdf",
  typeMime: "application/pdf",
  tailleOctets: 2048,
  empreinteSha256: "ab".repeat(32),
  deposeLe: "2026-11-20T10:00:00Z",
};

function afficher(pieces: PieceDto[], deposer = vi.fn()) {
  render(
    <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
      <DepotDemande
        demande={demande}
        piecesRequises={["Étude de faisabilité", "EIES"]}
        pieces={pieces}
        deposer={deposer}
      />
    </NextIntlClientProvider>,
  );
  return deposer;
}

const declaration = () =>
  screen.getByLabelText(/Je déclare sur l'honneur l'exactitude des informations fournies/);

beforeEach(() => push.mockReset());

describe("dépôt de la demande", () => {
  it("exige la déclaration sur l'honneur et au moins une pièce avant d'appeler l'API", () => {
    const deposer = afficher([]);
    fireEvent.click(screen.getByRole("button", { name: "Déposer ma demande" }));
    const alerte = screen.getByRole("alert");
    expect(alerte).toHaveTextContent("Cochez la déclaration sur l'honneur.");
    expect(alerte).toHaveTextContent("Joignez au moins une pièce");
    expect(declaration()).toHaveAttribute("aria-invalid", "true");
    expect(deposer).not.toHaveBeenCalled();
  });

  it("signale les pièces attendues manquantes sans bloquer le dépôt", () => {
    afficher([eies]);
    expect(screen.getByText(/Non fournies : Étude de faisabilité/)).toBeInTheDocument();
  });

  it("dépose puis affiche la demande déposée", async () => {
    const deposer = afficher(
      [eies],
      vi.fn(async () => ({
        ok: true as const,
        valeur: { ...demande, statut: "deposee", numero: "DEM-2026-000001" },
      })),
    );
    fireEvent.click(declaration());
    fireEvent.click(screen.getByRole("button", { name: "Déposer ma demande" }));
    await waitFor(() => expect(push).toHaveBeenCalledWith("/demandes/41?deposee=1"));
    expect(deposer).toHaveBeenCalledWith(41, true);
  });

  it("affiche les manques renvoyés par l'API", async () => {
    afficher(
      [eies],
      vi.fn(async () => ({
        ok: false as const,
        erreur: { code: "DEM-004", message: "", champs: [], manques: ["pieces"] },
      })),
    );
    fireEvent.click(declaration());
    fireEvent.click(screen.getByRole("button", { name: "Déposer ma demande" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Joignez au moins une pièce");
    expect(push).not.toHaveBeenCalled();
  });
});
