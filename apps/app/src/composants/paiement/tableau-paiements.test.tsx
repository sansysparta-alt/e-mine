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
    expect(document.querySelector("iframe")?.getAttribute("src")).toMatch(
      new RegExp(`^${echapper("/api/paiements/PAY-2026-000002/recu?inline=1")}#toolbar=0`),
    );
    expect(within(seconde).getByText("Refusé")).toBeInTheDocument();
    expect(within(seconde).queryByRole("button", { name: /REC-/ })).not.toBeInTheDocument();
    expect(lienPaiement(paye)).toBe("/demandes/42/paiement/retour?reference=PAY-2026-000002");
  });

  it("ouvre le reçu dans le visualiseur, qui propose le téléchargement", () => {
    afficher(<LienRecu reference="PAY-2026-000002" numero="REC-2026-000001" />);
    fireEvent.click(screen.getByRole("button", { name: "Voir le reçu REC-2026-000001" }));
    expect(document.querySelector("iframe")?.getAttribute("src")).toMatch(
      new RegExp(`^${echapper(`${adresseRecu("PAY-2026-000002")}?inline=1`)}#toolbar=0`),
    );
    expect(screen.getByRole("link", { name: /Télécharger/ })).toHaveAttribute(
      "href",
      adresseRecu("PAY-2026-000002"),
    );
  });
});

/** Texte pris littéralement dans une expression régulière. */
function echapper(texte: string): string {
  return texte.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

describe("« Paiements & reçus » (historique de l'usager)", () => {
  const demande = () => ({ numero: "DEM-2026-000042", document: "Permis d’exploitation" });

  it("le numéro de demande ouvre le dossier, le reçu se télécharge, la ligne ouvre le détail", () => {
    afficher(<TableauPaiements paiements={[paye, refuse]} demande={demande} recherche="000042" />);
    const lignes = screen.getAllByRole("row").slice(1);
    const premiere = within(lignes[0]!);
    expect(premiere.getByRole("link", { name: "DEM-2026-000042" })).toHaveAttribute(
      "href",
      "/demandes/42",
    );
    const recu = premiere.getByRole("link", { name: /Télécharger le reçu/ });
    expect(recu).toHaveAttribute("href", adresseRecu("PAY-2026-000002"));
    expect(recu).toHaveAttribute("download", "recu-REC-2026-000001.pdf");
    expect(premiere.getByRole("link", { name: /PAY-2026-000002/ })).toHaveAttribute(
      "href",
      "/paiements/PAY-2026-000002",
    );
    // Sans reçu, la colonne Actions n'est jamais vide.
    expect(within(lignes[1]!).getByRole("link", { name: /Voir le détail/ })).toHaveAttribute(
      "href",
      "/paiements/PAY-2026-000001",
    );
    expect(lignes[0]!.querySelector("mark")).toHaveTextContent("000042");
  });
});
