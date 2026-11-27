import type { RemboursementDto } from "@emine/api-client";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import en from "../../../messages/en.json";
import fr from "../../../messages/fr.json";
import { ActionsRemboursement, actionsRemboursement } from "./actions-remboursement";
import { presentationStatutRemboursement } from "./statut";

const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh, push: vi.fn() }) }));

function afficher(enfant: ReactNode) {
  return render(
    <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
      {enfant}
    </NextIntlClientProvider>,
  );
}

function remboursement(partiel: Partial<RemboursementDto>): RemboursementDto {
  return {
    id: 5,
    numero: "RMB-2026-000005",
    statut: "demande",
    motif: "double_paiement",
    montantGnf: 5_000_000,
    explication: "Versement en double",
    motifRefus: null,
    referenceOrdre: null,
    demandeLe: "2026-11-26T10:00:00Z",
    demandePar: "Mariama Diallo",
    decideLe: null,
    decidePar: null,
    executeLe: null,
    referencePaiement: "PAY-2026-000123",
    objetPaiement: "FRAIS_DOSSIER",
    moyenPaiement: "VIREMENT",
    demandeId: 42,
    numeroDemande: "DEM-2026-000042",
    beneficiaire: "Fodé Bangoura",
    aTraiterParMoi: true,
    ...partiel,
  };
}

describe("remboursements (US-04.10, US-04.18)", () => {
  beforeEach(() => refresh.mockReset());

  it("dit à chaque profil ce qui le concerne ; l'orange suit ce que l'API lui attribue", () => {
    // Le Directeur valide les propositions ; le Responsable fiscal attend sa décision.
    expect(presentationStatutRemboursement("demande", "directeur", true)).toEqual({
      cle: "aValider",
      ton: "avertissement",
    });
    expect(presentationStatutRemboursement("demande", "fiscal")).toEqual({
      cle: "attenteDirecteur",
      ton: "neutre",
    });
    // Puis le Responsable fiscal enregistre l'ordre du Trésor ; le Directeur n'a plus rien à faire.
    expect(presentationStatutRemboursement("valide", "fiscal", true)).toEqual({
      cle: "ordreAEnregistrer",
      ton: "avertissement",
    });
    expect(presentationStatutRemboursement("valide", "directeur")).toEqual({
      cle: "ordreAttendu",
      ton: "neutre",
    });
    expect(presentationStatutRemboursement("valide", "usager")).toEqual({
      cle: "enCours",
      ton: "info",
    });
    expect(presentationStatutRemboursement("execute", "usager").ton).toBe("succes");
    for (const s of ["demande", "valide", "execute", "refuse"]) {
      for (const profil of ["directeur", "fiscal", "usager"] as const) {
        for (const aMoi of [true, false]) {
          const { cle } = presentationStatutRemboursement(s, profil, aMoi);
          expect(fr.remboursements.statuts).toHaveProperty(cle);
          expect(en.remboursements.statuts).toHaveProperty(cle);
        }
      }
    }
  });

  it("propose seulement les actions que l'API attribue au profil connecté", () => {
    expect(actionsRemboursement({ statut: "demande", aTraiterParMoi: true })).toEqual([
      "valider",
      "refuser",
    ]);
    expect(actionsRemboursement({ statut: "valide", aTraiterParMoi: true })).toEqual(["executer"]);
    // Séparation des tâches : l'auteur de la proposition ne la décide pas.
    expect(actionsRemboursement({ statut: "demande", aTraiterParMoi: false })).toEqual([]);
    expect(actionsRemboursement({ statut: "execute", aTraiterParMoi: true })).toEqual([]);
  });

  it("exige la référence de l'ordre du Trésor avant d'informer le demandeur", async () => {
    const decider = vi.fn().mockResolvedValue({ ok: true, valeur: null });
    afficher(
      <ActionsRemboursement
        remboursement={remboursement({ statut: "valide" })}
        decider={decider}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: /Enregistrer l’ordre/ }));
    const boite = screen.getByRole("dialog");
    expect(within(boite).getByText(/Fodé Bangoura/)).toBeVisible();
    const confirmer = within(boite).getByRole("button", {
      name: "Enregistrer et informer le demandeur",
    });
    fireEvent.click(confirmer);
    expect(within(boite).getByText(/Indiquez la référence/)).toBeVisible();
    expect(decider).not.toHaveBeenCalled();
    fireEvent.change(within(boite).getByLabelText("Référence de l’ordre de remboursement"), {
      target: { value: "OR-2026-0042" },
    });
    fireEvent.click(confirmer);
    await waitFor(() => expect(refresh).toHaveBeenCalled());
    expect(decider).toHaveBeenCalledWith(5, "executer", "OR-2026-0042");
  });

  it("exige un motif pour refuser", async () => {
    const decider = vi.fn().mockResolvedValue({ ok: true, valeur: null });
    afficher(<ActionsRemboursement remboursement={remboursement({})} decider={decider} />);
    fireEvent.click(screen.getByRole("button", { name: /^Refuser/ }));
    const boite = screen.getByRole("dialog");
    fireEvent.click(within(boite).getByRole("button", { name: "Refuser le remboursement" }));
    expect(decider).not.toHaveBeenCalled();
    fireEvent.change(within(boite).getByLabelText(/Motif du refus/), {
      target: { value: "Versement déjà rendu par la banque." },
    });
    fireEvent.click(within(boite).getByRole("button", { name: "Refuser le remboursement" }));
    await waitFor(() =>
      expect(decider).toHaveBeenCalledWith(5, "refuser", "Versement déjà rendu par la banque."),
    );
  });

  it("n'affiche aucune action une fois le remboursement effectué", () => {
    const { container } = afficher(
      <ActionsRemboursement
        remboursement={remboursement({ statut: "execute" })}
        decider={vi.fn()}
      />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it("le tableau de bord du Directeur annonce les remboursements à valider (US-04.18)", async () => {
    const { VueTableauDeBord } = await import("../vue-tableau-de-bord");
    afficher(
      <VueTableauDeBord
        prenom="Awa"
        nom="Test"
        profilLibelle="Directeur"
        profil="decideur"
        remboursementsAValider={2}
      />,
    );
    expect(screen.getByText("2 remboursements à valider")).toBeVisible();
    expect(screen.getByRole("link", { name: "Voir les remboursements à valider" })).toHaveAttribute(
      "href",
      "/remboursements?statut=demande",
    );
  });
});
