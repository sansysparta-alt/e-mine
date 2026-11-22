import type { EtatDecisionDto } from "@emine/api-client";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import fr from "../../../messages/fr.json";
import { DecisionRendue } from "./decision-rendue";
import { PriseDecision } from "./prise-decision";

const push = vi.fn();
const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push, refresh }) }));

function avecTraductions(enfant: React.ReactNode) {
  return render(
    <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
      {enfant}
    </NextIntlClientProvider>,
  );
}

function afficher(droitsGnf: number | null, decider = vi.fn()) {
  avecTraductions(
    <PriseDecision
      demandeId={41}
      numero="DEM-2026-000041"
      signataire="Ministre"
      droitsGnf={droitsGnf}
      decider={decider}
    />,
  );
  return decider;
}

const enregistrer = () => screen.getByRole("button", { name: "Enregistrer la décision…" });
const confirmer = () => screen.getByRole("button", { name: "Confirmer la décision" });

beforeEach(() => {
  push.mockReset();
  refresh.mockReset();
});

describe("décision du signataire (US-05.4)", () => {
  it("rappelle le signataire du catalogue et exige un choix avant toute confirmation", () => {
    const decider = afficher(15_000_000);
    expect(screen.getByText(/signataire désigné par le catalogue .*: Ministre/)).toBeVisible();
    fireEvent.click(enregistrer());
    expect(screen.getByText("Choisissez une décision : favorable ou rejet.")).toBeVisible();
    expect(screen.queryByRole("button", { name: "Confirmer la décision" })).toBeNull();
    expect(decider).not.toHaveBeenCalled();
  });

  it("annonce les droits à payer puis enregistre la décision favorable après confirmation", async () => {
    const decider = afficher(
      15_000_000,
      vi.fn().mockResolvedValue({ ok: true, valeur: { demandeId: 41, statut: "decision" } }),
    );
    fireEvent.click(screen.getByLabelText(/Décision favorable/));
    expect(screen.getAllByText(/payer les droits \(15\s000\s000\sGNF\)/)[0]).toBeVisible();
    expect(screen.getByLabelText(/Observations/)).not.toBeRequired();
    fireEvent.click(enregistrer());
    expect(screen.getByText(/Cette décision est définitive/)).toBeInTheDocument();
    fireEvent.click(confirmer());
    await waitFor(() => expect(decider).toHaveBeenCalledWith(41, true, null));
    await waitFor(() =>
      expect(push).toHaveBeenCalledWith(
        "/a-traiter?groupe=a-decider&traitee=DEM-2026-000041&resultat=decision",
      ),
    );
  });

  it("indique qu'un document sans droits sera aussitôt prêt pour la délivrance", () => {
    afficher(null);
    fireEvent.click(screen.getByLabelText(/Décision favorable/));
    expect(screen.getByText(/n’a pas de droits à payer/)).toBeVisible();
  });

  it("exige le motif d'un rejet et l'envoie nettoyé", async () => {
    const decider = afficher(
      15_000_000,
      vi.fn().mockResolvedValue({ ok: true, valeur: { demandeId: 41, statut: "rejetee" } }),
    );
    fireEvent.click(screen.getByLabelText(/^Rejet/));
    const motif = screen.getByLabelText(/Motif du rejet communiqué au demandeur/);
    expect(motif).toBeRequired();
    fireEvent.click(enregistrer());
    expect(motif).toHaveAttribute("aria-invalid", "true");
    expect(screen.queryByRole("button", { name: "Confirmer la décision" })).toBeNull();
    fireEvent.change(motif, { target: { value: "  Le périmètre chevauche un titre.  " } });
    fireEvent.click(enregistrer());
    fireEvent.click(confirmer());
    await waitFor(() =>
      expect(decider).toHaveBeenCalledWith(41, false, "Le périmètre chevauche un titre."),
    );
  });

  it("affiche le refus de l'API (signataire non désigné) sans quitter la fiche", async () => {
    afficher(
      15_000_000,
      vi.fn().mockResolvedValue({
        ok: false,
        erreur: { code: "DEM-008", message: "Seul le signataire désigné…", champs: [] },
      }),
    );
    fireEvent.click(screen.getByLabelText(/Décision favorable/));
    fireEvent.click(enregistrer());
    fireEvent.click(confirmer());
    expect(await screen.findByText("Seul le signataire désigné…")).toBeInTheDocument();
    expect(push).not.toHaveBeenCalled();
  });
});

describe("décision rendue et file des décisions", () => {
  const etat: EtatDecisionDto = {
    aDecider: false,
    signataire: "Ministre",
    signataireProfil: "MINISTRE",
    peutDecider: false,
    droitsGnf: 15_000_000,
    decisionFavorable: false,
    decisionMotif: "Le périmètre chevauche un titre.",
    decisionRenduLe: "2026-11-21T10:00:00Z",
    decisionAuteur: "Ibrahima Soumah",
    decisionAuteurProfil: "Ministre / Secrétaire Général",
  };

  it("montre le sens, la date, le signataire et le motif, sans dépendre de la couleur", () => {
    avecTraductions(<DecisionRendue etat={etat} />);
    expect(screen.getByText("Rejet")).toBeVisible();
    expect(screen.getByText("21/11/2026 à 10:00")).toBeVisible();
    expect(screen.getByText(/Ibrahima Soumah · Ministre/)).toBeVisible();
    expect(screen.getByText("Le périmètre chevauche un titre.")).toBeVisible();
  });

  it("ne montre rien tant qu'aucune décision n'est rendue", () => {
    const { container } = avecTraductions(
      <DecisionRendue etat={{ ...etat, decisionFavorable: null, decisionRenduLe: null }} />,
    );
    expect(container).toBeEmptyDOMElement();
  });
});
