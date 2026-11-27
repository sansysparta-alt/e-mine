import type { ElementAVerifierDto } from "@emine/api-client";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import fr from "../../../messages/fr.json";
import { DecisionsEcart, motifObligatoire } from "./decision-ecart";
import { lienElement } from "./tableau-a-verifier";

const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh, push: vi.fn() }) }));

function afficher(enfant: ReactNode) {
  return render(
    <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
      {enfant}
    </NextIntlClientProvider>,
  );
}

function element(partiel: Partial<ElementAVerifierDto>): ElementAVerifierDto {
  return {
    type: "ligne",
    ligneId: 12,
    releveId: 3,
    dateOperation: "2026-11-26",
    libelle: "VIR HORIZON PAY-2026-000123",
    montantGnf: 4_000_000,
    sens: "C",
    nature: "inferieur",
    ecartGnf: -1_000_000,
    referencePaiement: "PAY-2026-000123",
    montantAttenduGnf: 5_000_000,
    moyen: "VIREMENT",
    demandeId: 42,
    numeroDemande: "DEM-2026-000042",
    signaleLe: "2026-11-26T10:00:00Z",
    decisions: ["REJETER"],
    ...partiel,
  };
}

describe("décisions sur un écart (US-04.9)", () => {
  beforeEach(() => refresh.mockReset());

  it("n'affiche que les décisions données par l'API", () => {
    afficher(<DecisionsEcart element={element({})} decider={vi.fn()} />);
    expect(screen.getByRole("button", { name: /Rejeter/ })).toBeVisible();
    expect(screen.queryByRole("button", { name: /Valider/ })).toBeNull();
  });

  it("exige un motif pour rejeter, communiqué au demandeur", async () => {
    const decider = vi.fn().mockResolvedValue({ ok: true, valeur: null });
    afficher(<DecisionsEcart element={element({})} decider={decider} />);
    fireEvent.click(screen.getByRole("button", { name: /Rejeter/ }));
    const boite = screen.getByRole("dialog", { name: "Rejeter ce paiement ?" });
    expect(within(boite).getByText(/il devra payer à nouveau/)).toBeVisible();
    fireEvent.click(within(boite).getByRole("button", { name: "Rejeter le paiement" }));
    expect(decider).not.toHaveBeenCalled();
    expect(within(boite).getByText(/10 caractères/)).toBeVisible();
    fireEvent.change(within(boite).getByLabelText(/Motif/), {
      target: { value: "Montant reçu inférieur au montant dû." },
    });
    fireEvent.click(within(boite).getByRole("button", { name: "Rejeter le paiement" }));
    await waitFor(() => expect(refresh).toHaveBeenCalled());
    expect(decider).toHaveBeenCalledWith(
      { ligneId: 12 },
      "REJETER",
      "Montant reçu inférieur au montant dû.",
      "",
    );
  });

  it("associe au virement choisi dans la liste des virements du même montant (R4)", async () => {
    const decider = vi.fn().mockResolvedValue({ ok: true, valeur: null });
    const charger = vi.fn().mockResolvedValue({
      ok: true,
      valeur: [
        {
          reference: "PAY-2026-000777",
          montantGnf: 4_000_000,
          numeroDemande: "DEM-2026-000042",
          declareLe: "2026-11-25T10:00:00Z",
        },
        {
          reference: "PAY-2026-000778",
          montantGnf: 4_000_000,
          numeroDemande: null,
          declareLe: null,
        },
      ],
    });
    afficher(
      <DecisionsEcart
        element={element({ nature: "inconnue", decisions: ["ASSOCIER", "CLASSER"] })}
        decider={decider}
        chargerAssociables={charger}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: /Associer/ }));
    const boite = screen.getByRole("dialog");
    await within(boite).findByText("PAY-2026-000777");
    expect(charger).toHaveBeenCalledWith(12);
    expect(within(boite).getByText("Virement déclaré le 25/11/2026")).toBeVisible();
    // Aucune saisie de référence : on choisit.
    expect(within(boite).queryByRole("textbox", { name: /Référence/ })).toBeNull();
    fireEvent.click(within(boite).getByRole("button", { name: "Associer et confirmer" }));
    expect(within(boite).getByText("Choisissez le virement à confirmer.")).toBeVisible();
    fireEvent.click(within(boite).getByRole("radio", { name: /PAY-2026-000777/ }));
    fireEvent.click(within(boite).getByRole("button", { name: "Associer et confirmer" }));
    await waitFor(() =>
      expect(decider).toHaveBeenCalledWith({ ligneId: 12 }, "ASSOCIER", "", "PAY-2026-000777"),
    );
  });

  it("dit clairement quand aucun virement de ce montant n'est en cours, et l'erreur de l'API", async () => {
    const decider = vi.fn().mockResolvedValue({
      ok: false,
      erreur: { code: "PAY-020", message: "Ce paiement est déjà confirmé.", champs: [] },
    });
    const vide = vi.fn().mockResolvedValue({ ok: true, valeur: [] });
    const { unmount } = afficher(
      <DecisionsEcart
        element={element({ nature: "inconnue", decisions: ["ASSOCIER"] })}
        decider={decider}
        chargerAssociables={vide}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: /Associer/ }));
    expect(await screen.findByText("Aucun virement en cours de ce montant")).toBeVisible();
    expect(screen.getByRole("button", { name: "Associer et confirmer" })).toBeDisabled();
    unmount();

    const un = vi.fn().mockResolvedValue({
      ok: true,
      valeur: [
        {
          reference: "PAY-2026-000777",
          montantGnf: 4_000_000,
          numeroDemande: null,
          declareLe: null,
        },
      ],
    });
    afficher(
      <DecisionsEcart
        element={element({ nature: "inconnue", decisions: ["ASSOCIER"] })}
        decider={decider}
        chargerAssociables={un}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: /Associer/ }));
    const boite = screen.getByRole("dialog");
    await within(boite).findByText("PAY-2026-000777");
    // Un seul virement possible : il est présélectionné.
    fireEvent.click(within(boite).getByRole("button", { name: "Associer et confirmer" }));
    expect(await within(boite).findByText("Ce paiement est déjà confirmé.")).toBeVisible();
    expect(refresh).not.toHaveBeenCalled();
  });

  it("montre l'aperçu du motif avant de confirmer (US-04.13)", () => {
    afficher(<DecisionsEcart element={element({})} decider={vi.fn()} />);
    fireEvent.click(screen.getByRole("button", { name: /Rejeter/ }));
    const boite = screen.getByRole("dialog");
    expect(within(boite).queryByText(/Aperçu/)).toBeNull();
    fireEvent.change(within(boite).getByLabelText(/Motif/), {
      target: { value: "Montant reçu inférieur au montant dû." },
    });
    expect(within(boite).getByText("Aperçu : le demandeur lira ce motif")).toBeVisible();
    expect(within(boite).getByRole("blockquote")).toHaveTextContent(
      "Montant reçu inférieur au montant dû.",
    );
  });

  it("vise un paiement en ligne par sa référence", async () => {
    const decider = vi.fn().mockResolvedValue({ ok: true, valeur: null });
    afficher(
      <DecisionsEcart
        element={element({
          type: "paiement",
          ligneId: null,
          releveId: null,
          nature: "montant_divergent",
          decisions: ["VALIDER", "REJETER"],
        })}
        decider={decider}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: /Valider le paiement/ }));
    fireEvent.click(
      within(screen.getByRole("dialog")).getByRole("button", { name: "Valider le paiement" }),
    );
    await waitFor(() =>
      expect(decider).toHaveBeenCalledWith({ reference: "PAY-2026-000123" }, "VALIDER", "", ""),
    );
  });

  it("ouvre la ligne dans son relevé, ou la demande d'un paiement en ligne", () => {
    expect(lienElement(element({}))).toBe("/releves/3?resultat=a-verifier");
    expect(lienElement(element({ type: "paiement", releveId: null }))).toBe("/demandes/42");
    expect(motifObligatoire("CLASSER")).toBe(true);
    expect(motifObligatoire("VALIDER")).toBe(false);
  });
});

describe("complément d'un paiement insuffisant (US-04.12)", () => {
  beforeEach(() => refresh.mockReset());

  it("propose « Demander le complément » avec le reste, motif obligatoire", async () => {
    const decider = vi.fn().mockResolvedValue({ ok: true, valeur: null });
    afficher(
      <DecisionsEcart
        element={element({ decisions: ["COMPLETER", "REJETER"] })}
        decider={decider}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: /Demander le complément/ }));
    const boite = screen.getByRole("dialog", { name: "Demander le complément ?" });
    expect(within(boite).getByText(/n’est pas rejeté/)).toHaveTextContent("1 000 000 GNF");
    fireEvent.click(within(boite).getByRole("button", { name: "Demander le complément" }));
    expect(decider).not.toHaveBeenCalled();
    fireEvent.change(within(boite).getByLabelText(/Motif/), {
      target: { value: "Il manque une partie des frais de dossier." },
    });
    fireEvent.click(within(boite).getByRole("button", { name: "Demander le complément" }));
    await waitFor(() =>
      expect(decider).toHaveBeenCalledWith(
        { ligneId: 12 },
        "COMPLETER",
        "Il manque une partie des frais de dossier.",
        "",
      ),
    );
    expect(motifObligatoire("COMPLETER")).toBe(true);
  });

  it("affiche aussi « Proposer le remboursement » (doublon) sans erreur", () => {
    afficher(
      <DecisionsEcart
        element={element({ nature: "doublon", decisions: ["REMBOURSER", "CLASSER"] })}
        decider={vi.fn()}
      />,
    );
    expect(screen.getByRole("button", { name: /Proposer le remboursement/ })).toBeVisible();
  });
});
