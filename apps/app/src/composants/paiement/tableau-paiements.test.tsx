import type { PaiementDto } from "@emine/api-client";
import { render, screen, within } from "@testing-library/react";
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
    const recu = within(premiere).getByRole("link", { name: /REC-2026-000001/ });
    expect(recu).toHaveAttribute("href", "/api/paiements/PAY-2026-000002/recu");
    expect(recu).toHaveAttribute("download");
    expect(within(seconde).getByText("Refusé")).toBeInTheDocument();
    expect(within(seconde).queryByRole("link", { name: /REC-/ })).not.toBeInTheDocument();
    expect(lienPaiement(paye)).toBe("/demandes/42/paiement/retour?reference=PAY-2026-000002");
  });

  it("propose un bouton explicite sur la page de résultat", () => {
    afficher(<LienRecu reference="PAY-2026-000002" numero="REC-2026-000001" />);
    expect(
      screen.getByRole("link", { name: "Télécharger le reçu REC-2026-000001 (PDF)" }),
    ).toHaveAttribute("href", adresseRecu("PAY-2026-000002"));
  });
});
