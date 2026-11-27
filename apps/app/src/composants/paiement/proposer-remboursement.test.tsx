import type { PaiementDto } from "@emine/api-client";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import fr from "../../../messages/fr.json";
import { recoitNotifications } from "@/navigation/config";
import { BoutonProposerRemboursement } from "./proposer-remboursement";
import { plafondRemboursement } from "./statut";

const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), refresh }) }));

const paye: PaiementDto = {
  reference: "PAY-2026-000010",
  demandeId: 7,
  objet: "FRAIS_DOSSIER",
  moyen: "VIREMENT",
  montantGnf: 5_000_000,
  statut: "paye",
  dateExpiration: null,
  montantRecuGnf: 5_000_000,
  datePaiement: "2026-11-20T00:00:00Z",
  recuNumero: "REC-2026-000010",
  creeLe: "2026-11-20T10:00:00Z",
  virementDeclareLe: null,
  justificatif: false,
  motifRejet: null,
  justificatifFichier: null,
  remboursements: [
    {
      numero: "RMB-2026-000001",
      statut: "demande",
      montantGnf: 1_000_000,
      demandeLe: "2026-11-21T10:00:00Z",
      executeLe: null,
      referenceOrdre: null,
      motifRefus: null,
    },
  ],
  coordonnees: null,
  complementDe: null,
  resteAPayerGnf: null,
  motifComplement: null,
  complementEcheance: null,
};

describe("proposer un remboursement (R7, US-04.13)", () => {
  it("plafonne à la somme reçue non encore proposée ; rien sur un paiement non payé", () => {
    expect(plafondRemboursement(paye)).toBe(4_000_000);
    expect(plafondRemboursement({ ...paye, remboursements: [] })).toBe(5_000_000);
    expect(plafondRemboursement({ ...paye, statut: "rejete" })).toBe(0);
  });

  it("propose avec un montant et un motif dont on voit l'aperçu", async () => {
    const proposer = vi.fn().mockResolvedValue({ ok: true, valeur: { numero: "RMB-2026-000002" } });
    render(
      <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
        <BoutonProposerRemboursement paiement={paye} proposer={proposer} />
      </NextIntlClientProvider>,
    );
    fireEvent.click(screen.getByRole("button", { name: /Proposer un remboursement/ }));
    const boite = screen.getByRole("dialog");
    const montant = within(boite).getByLabelText("Montant à rembourser");
    expect(montant).toHaveValue("4000000");
    fireEvent.change(montant, { target: { value: "4500000" } });
    fireEvent.click(within(boite).getByRole("button", { name: "Proposer le remboursement" }));
    expect(within(boite).getByText(/entre 1 GNF et 4/)).toBeVisible();
    expect(proposer).not.toHaveBeenCalled();
    fireEvent.change(montant, { target: { value: "500000" } });
    fireEvent.change(within(boite).getByLabelText(/Motif/), {
      target: { value: "Versement en trop constaté au relevé." },
    });
    expect(within(boite).getByText(/le Directeur et le demandeur liront/)).toBeVisible();
    fireEvent.click(within(boite).getByRole("button", { name: "Proposer le remboursement" }));
    await waitFor(() =>
      expect(proposer).toHaveBeenCalledWith(
        "PAY-2026-000010",
        500_000,
        "Versement en trop constaté au relevé.",
      ),
    );
    expect(
      await screen.findByText("Proposition RMB-2026-000002 envoyée au Directeur."),
    ).toBeVisible();
  });
});

describe("cloche des notifications (US-04.17)", () => {
  it("l'usager et le Responsable fiscal l'ont, pas les autres agents", () => {
    expect(recoitNotifications({ interne: false, permissions: ["DEPOSER"] })).toBe(true);
    expect(recoitNotifications({ interne: true, permissions: ["ENCAISSER"] })).toBe(true);
    expect(recoitNotifications({ interne: true, permissions: ["SIGNER"] })).toBe(false);
  });
});
