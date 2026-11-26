import { fireEvent, render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import fr from "../../../messages/fr.json";
import { ActionsDemande } from "../demande/actions-demande";
import { attendrePdf } from "@/test/pdf";
import { TelechargerDocument } from "./telecharger-document";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }) }));

function afficher(enfant: React.ReactNode) {
  return render(
    <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
      {enfant}
    </NextIntlClientProvider>,
  );
}

describe("téléchargement d'un document délivré", () => {
  it("un document valide se télécharge directement", () => {
    afficher(<TelechargerDocument demandeId={7} numero="PEX-2026-0001" />);
    const lien = screen.getByRole("link", { name: /Télécharger/ });
    expect(lien).toHaveAttribute("href", "/api/demandes/7/document/contenu");
    expect(lien).toHaveAttribute("download", "PEX-2026-0001.pdf");
  });

  it("un document révoqué se « Voit » d'abord, sous un avertissement (date, autorité, motif)", async () => {
    afficher(
      <TelechargerDocument
        demandeId={7}
        numero="PEX-2026-0001"
        revocation={{
          le: "2026-11-20T10:00:00Z",
          autorite: "Directeur national",
          motif: "Fraude constatée.",
        }}
      />,
    );
    // Pas de téléchargement direct dans la liste.
    expect(screen.queryByRole("link", { name: /Télécharger/ })).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: /Voir/ }));
    expect(screen.getByText("Document révoqué le 20/11/2026 : il n’est plus valide")).toBeVisible();
    expect(screen.getByText(/Révoqué par : Directeur national/)).toBeVisible();
    expect(screen.getByText(/Motif : Fraude constatée\./)).toBeVisible();
    await attendrePdf("/api/demandes/7/document/contenu?inline=1");
    // Le téléchargement reste possible depuis le visualiseur, fichier marqué « REVOQUE ».
    expect(screen.getByRole("link", { name: "Télécharger" })).toHaveAttribute(
      "download",
      "PEX-2026-0001-REVOQUE.pdf",
    );
  });
});

describe("colonne Actions de « Mes demandes »", () => {
  const base = { id: 3, fraisPayes: true, documentRevoque: false };

  it("une demande en instruction propose « Suivre » : la colonne n'est jamais vide", () => {
    afficher(<ActionsDemande demande={{ ...base, statut: "en_instruction" }} />);
    expect(screen.getByRole("link", { name: "Suivre" })).toHaveAttribute("href", "/demandes/3");
  });

  it("une demande délivrée dont le document est révoqué propose « Voir » (la fiche explique)", () => {
    afficher(<ActionsDemande demande={{ ...base, statut: "delivree", documentRevoque: true }} />);
    expect(screen.queryByRole("link", { name: /Télécharger/ })).toBeNull();
    expect(screen.getByRole("link", { name: "Voir" })).toHaveAttribute("href", "/demandes/3");
  });
});
