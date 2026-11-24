import { fireEvent, render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import fr from "../../../messages/fr.json";
import { ActionsDemande } from "../demande/actions-demande";
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

  it("un document révoqué avertit (date et motif) avant le téléchargement", () => {
    afficher(
      <TelechargerDocument
        demandeId={7}
        numero="PEX-2026-0001"
        revocation={{ le: "2026-11-20T10:00:00Z", motif: "Fraude constatée." }}
      />,
    );
    expect(screen.queryByRole("link", { name: /Télécharger/ })).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: /Télécharger/ }));
    expect(screen.getByText(/révoqué par le Ministère le 20\/11\/2026/)).toBeVisible();
    expect(screen.getByText("Fraude constatée.")).toBeVisible();
    expect(screen.getByRole("link", { name: /Télécharger quand même/ })).toHaveAttribute(
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

  it("une demande délivrée dont le document est révoqué avertit avant le téléchargement", () => {
    afficher(<ActionsDemande demande={{ ...base, statut: "delivree", documentRevoque: true }} />);
    fireEvent.click(screen.getByRole("button", { name: /Télécharger/ }));
    expect(screen.getByText("Ce document est révoqué")).toBeVisible();
  });
});
