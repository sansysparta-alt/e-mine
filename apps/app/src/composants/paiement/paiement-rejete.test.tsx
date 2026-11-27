import type { PaiementDto } from "@emine/api-client";
import { render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import fr from "../../../messages/fr.json";
import { ExplicationPaiementRejete, paiementRejeteAExpliquer } from "./paiement-rejete";
import { presentationStatutPaiement } from "./statut";

function paiement(partiel: Partial<PaiementDto>): PaiementDto {
  return {
    reference: "PAY-2026-000010",
    demandeId: 7,
    objet: "FRAIS_DOSSIER",
    moyen: "VIREMENT",
    montantGnf: 5_000_000,
    statut: "rejete",
    dateExpiration: null,
    montantRecuGnf: 4_000_000,
    datePaiement: null,
    recuNumero: null,
    creeLe: "2026-11-20T10:00:00Z",
    virementDeclareLe: "2026-11-20T11:00:00Z",
    justificatif: true,
    motifRejet: "Montant reçu incomplet, à reprendre.",
    justificatifFichier: null,
    remboursements: [],
    coordonnees: null,
    complementDe: null,
    resteAPayerGnf: null,
    motifComplement: null,
    complementEcheance: null,
    ...partiel,
  };
}

const remb = (statut: string) => ({
  numero: "RMB-2026-000003",
  statut,
  montantGnf: 4_000_000,
  demandeLe: "2026-11-21T10:00:00Z",
  executeLe: statut === "execute" ? "2026-11-24T10:00:00Z" : null,
  referenceOrdre: statut === "execute" ? "OR-2026-0042" : null,
  motifRefus: null,
});

function afficher(p: PaiementDto, resteAPayer: boolean) {
  render(
    <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
      <ExplicationPaiementRejete paiement={p} resteAPayer={resteAPayer} demandeId={7} />
    </NextIntlClientProvider>,
  );
}

describe("paiement rejeté et remboursé (US-07.5)", () => {
  it("un seul badge dit ce qu'il est advenu de la somme reçue", () => {
    const cle = (r: ReturnType<typeof remb>[]) =>
      presentationStatutPaiement(paiement({ remboursements: r }), "usager");
    expect(cle([remb("execute")])).toEqual({ cle: "rejeteRembourse", ton: "danger" });
    expect(cle([remb("valide")]).cle).toBe("rejeteRemboursementEnCours");
    expect(cle([]).cle).toBe("rejete");
    expect(fr.statutsPaiementAffiches.rejeteRembourse).toBe("Rejeté · remboursé");
  });

  it("explique le rejet avec son motif, la somme rendue et ce qu'il reste à faire", () => {
    afficher(paiement({ remboursements: [remb("execute")] }), true);
    expect(screen.getByText("Paiement PAY-2026-000010 rejeté")).toBeVisible();
    expect(screen.getByText("Motif : Montant reçu incomplet, à reprendre.")).toBeVisible();
    expect(screen.getByText(/remboursée par le Trésor \(ordre OR-2026-0042\)/)).toBeVisible();
    expect(screen.getByText("Il vous reste à payer les frais de dossier.")).toBeVisible();
    expect(screen.getByRole("link", { name: "Payer les frais de dossier" })).toHaveAttribute(
      "href",
      "/demandes/7/paiement",
    );
  });

  it("sans somme due, dit qu'il n'y a rien d'autre à faire", () => {
    afficher(paiement({ remboursements: [remb("demande")] }), false);
    expect(screen.getByText(/le remboursement RMB-2026-000003 est en cours/)).toBeVisible();
    expect(screen.getByText("Vous n’avez rien d’autre à faire pour ce paiement.")).toBeVisible();
    expect(screen.queryByRole("link")).toBeNull();
  });

  it("n'explique plus un rejet suivi d'un paiement accepté du même objet", () => {
    const rejete = paiement({});
    const paye = paiement({
      reference: "PAY-2026-000011",
      statut: "paye",
      creeLe: "2026-11-25T10:00:00Z",
    });
    expect(paiementRejeteAExpliquer([paye, rejete])).toBeNull();
    expect(paiementRejeteAExpliquer([rejete])).toBe(rejete);
  });
});
