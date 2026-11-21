import type { PaiementDto } from "@emine/api-client";
import { fireEvent, render, screen, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import fr from "../../../messages/fr.json";
import { adresseRecu, LienRecu } from "./lien-recu";
import { lienPaiement, TableauPaiements } from "./tableau-paiements";

const paye: PaiementDto = {
  reference: "PAY-2026-000002",
  demandeId: 42,
  objet: "FRAIS_DOSSIER",
  moyen: "TRESORPAY",
  montantGnf: 5_000_000,
  statut: "paye",
  dateExpiration: "2026-11-21T10:15:00Z",
  montantRecuGnf: 5_000_000,
  datePaiement: "2026-11-21T10:03:00Z",
  recuNumero: "REC-2026-000001",
  creeLe: "2026-11-21T10:00:00Z",
};
const refuse: PaiementDto = {
  ...paye,
  reference: "PAY-2026-000001",
  statut: "echoue",
  montantRecuGnf: null,
  datePaiement: null,
  recuNumero: null,
};

function afficher(enfant: React.ReactNode) {
  render(
    <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
      {enfant}
    </NextIntlClientProvider>,
  );
}

describe("historique des paiements et reçu (US-04.4, US-04.6)", () => {
  it("affiche statut, montant et reçu téléchargeable ; la ligne ouvre le détail", () => {
    afficher(<TableauPaiements paiements={[paye, refuse]} />);
    const [premiere, seconde] = screen.getAllByRole("row").slice(1) as [HTMLElement, HTMLElement];
    expect(within(premiere).getByText("Payé")).toBeInTheDocument();
    expect(within(premiere).getByText("Frais de dossier")).toBeInTheDocument();
    expect(within(premiere).getByText(/5\s000\s000/)).toBeInTheDocument();
    fireEvent.click(within(premiere).getByRole("button", { name: /REC-2026-000001/ }));
    expect(document.querySelector("iframe")).toHaveAttribute(
      "src",
      "/api/paiements/PAY-2026-000002/recu?inline=1",
    );
    expect(within(seconde).getByText("Refusé")).toBeInTheDocument();
    expect(within(seconde).queryByRole("button", { name: /REC-/ })).not.toBeInTheDocument();
    expect(lienPaiement(paye)).toBe("/demandes/42/paiement/retour?reference=PAY-2026-000002");
  });

  it("ouvre le reçu dans le visualiseur, qui propose le téléchargement", () => {
    afficher(<LienRecu reference="PAY-2026-000002" numero="REC-2026-000001" />);
    fireEvent.click(screen.getByRole("button", { name: "Voir le reçu REC-2026-000001" }));
    expect(document.querySelector("iframe")).toHaveAttribute(
      "src",
      `${adresseRecu("PAY-2026-000002")}?inline=1`,
    );
    expect(screen.getByRole("link", { name: /Télécharger/ })).toHaveAttribute(
      "href",
      adresseRecu("PAY-2026-000002"),
    );
  });
});
