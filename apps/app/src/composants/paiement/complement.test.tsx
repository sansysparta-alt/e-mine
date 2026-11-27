import type { PaiementDto } from "@emine/api-client";
import { render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import fr from "../../../messages/fr.json";
import { complementAPayer, ExplicationComplement } from "./complement";
import { presentationStatutPaiement } from "./statut";

const initial = {
  reference: "PAY-2026-000010",
  demandeId: 7,
  objet: "FRAIS_DOSSIER",
  moyen: "VIREMENT",
  montantGnf: 5_000_000,
  statut: "complement_attendu",
  dateExpiration: null,
  montantRecuGnf: 4_000_000,
  datePaiement: null,
  recuNumero: null,
  creeLe: "2026-11-20T10:00:00Z",
  virementDeclareLe: "2026-11-20T11:00:00Z",
  justificatif: true,
  motifRejet: null,
  justificatifFichier: null,
  remboursements: [],
  coordonnees: null,
  complementDe: null,
  resteAPayerGnf: 1_000_000,
  motifComplement: "Il manque une partie des frais.",
  complementEcheance: "2026-12-05T10:00:00Z",
} satisfies PaiementDto;

describe("complément attendu, vu par l'usager (US-04.12)", () => {
  it("trouve le paiement incomplet et explique le reste à payer", () => {
    expect(complementAPayer([{ ...initial, statut: "paye" }])).toBeNull();
    expect(complementAPayer([initial])?.reference).toBe("PAY-2026-000010");
    render(
      <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
        <ExplicationComplement paiement={initial} demandeId={7} />
      </NextIntlClientProvider>,
    );
    expect(screen.getByText(/il reste 1 000 000 GNF à payer/)).toBeVisible();
    expect(screen.getByText(/n’est pas rejeté/)).toBeVisible();
    expect(screen.getByText("Motif : Il manque une partie des frais.")).toBeVisible();
    expect(screen.getByText(/avant le 05\/12\/2026/)).toBeVisible();
    expect(screen.getByRole("link", { name: /Payer le reste/ })).toHaveAttribute(
      "href",
      "/demandes/7/paiement",
    );
  });

  it("orange pour l'usager, gris pour les agents (un seul badge)", () => {
    expect(presentationStatutPaiement(initial, "usager")).toEqual({
      cle: "complementAPayer",
      ton: "avertissement",
    });
    expect(presentationStatutPaiement(initial, "fiscal").ton).toBe("neutre");
  });
});
