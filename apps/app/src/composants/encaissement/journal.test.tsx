import type { IndicateursEncaissementsDto, LigneJournalEncaissementsDto } from "@emine/api-client";
import { render, screen, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import fr from "../../../messages/fr.json";
import { TableauJournalEncaissements } from "./tableau-journal-encaissements";
import { VueFiscal } from "./vue-fiscal";

function afficher(enfant: ReactNode) {
  return render(
    <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
      {enfant}
    </NextIntlClientProvider>,
  );
}

const totaux = {
  encaisseGnf: 5_200_000,
  rembourseGnf: 200_000,
  soldeGnf: 5_000_000,
  nombre: 3,
  tresorPayGnf: 5_000_000,
  virementGnf: 0,
};

describe("journal des encaissements et tableau de bord fiscal (US-04.11)", () => {
  it("montre un remboursement de frais en négatif, avec l'ordre du Trésor", () => {
    const lignes: LigneJournalEncaissementsDto[] = [
      {
        type: "encaissement",
        date: "2026-11-26T09:00:00Z",
        reference: "PAY-2026-000001",
        piece: "REC-2026-000001",
        montantGnf: 5_000_000,
        moyen: "TRESORPAY",
        objet: "FRAIS_DOSSIER",
        demandeId: 1,
        numeroDemande: "DEM-2026-000001",
        codeDocument: "PEX",
        payeur: "Fodé Bangoura",
      },
      {
        type: "remboursement",
        date: "2026-11-26T10:00:00Z",
        reference: "RMB-2026-000001",
        piece: "OR-2026-0042",
        montantGnf: -200_000,
        moyen: "VIREMENT",
        objet: "ATTESTATION",
        demandeId: 2,
        numeroDemande: "DEM-2026-000002",
        codeDocument: "EXC",
        payeur: "Fodé Bangoura",
      },
    ];
    afficher(<TableauJournalEncaissements lignes={lignes} />);
    const rangees = screen.getAllByRole("row").slice(1);
    // Référence et pièce séparées, chacune insécable (US-11.8).
    expect(within(rangees[0]!).getByText("PAY-2026-000001")).toHaveClass("whitespace-nowrap");
    expect(within(rangees[0]!).getByText("· REC-2026-000001")).toBeVisible();
    expect(within(rangees[1]!).getByText("Remboursement de frais")).toBeVisible();
    expect(within(rangees[1]!).getByText(/^− 200\s000\sGNF$/)).toBeVisible();
    expect(within(rangees[1]!).getByText(/OR-2026-0042/)).toBeVisible();
  });

  it("ouvre depuis chaque carte la liste qui donne le même nombre", () => {
    const indicateurs: IndicateursEncaissementsDto = {
      aVerifier: 4,
      remboursementsAValider: 2,
      ordresAEnregistrer: 1,
      mois: totaux,
      debutMois: "2026-11-01",
      finMois: "2026-11-30",
    };
    afficher(<VueFiscal indicateurs={indicateurs} />);
    const lien = (nom: RegExp) => screen.getByRole("link", { name: nom });
    expect(lien(/^À vérifier/)).toHaveAttribute("href", "/a-verifier");
    expect(lien(/Remboursements à valider/)).toHaveAttribute(
      "href",
      "/remboursements?statut=demande",
    );
    expect(lien(/Ordres à enregistrer/)).toHaveAttribute("href", "/remboursements?statut=valide");
    expect(lien(/Recettes du mois/)).toHaveAttribute(
      "href",
      "/journal-encaissements?du=2026-11-01&au=2026-11-30",
    );
    // Chaque carte affiche sa période.
    expect(screen.getAllByText("À ce jour")).toHaveLength(3);
    expect(screen.getByText("Ce mois-ci (du 01/11/2026 au 30/11/2026)")).toBeVisible();
  });
});
