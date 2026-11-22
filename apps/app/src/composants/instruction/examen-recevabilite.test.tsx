import type { ExamenRecevabiliteDto } from "@emine/api-client";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import fr from "../../../messages/fr.json";
import { erreurMotif } from "./champ-motif";
import { ExamenRecevabilite } from "./examen-recevabilite";

const push = vi.fn();
const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push, refresh }) }));

const PIECES = ["Étude de faisabilité", "EIES", "Plan de restauration"];
const complet: ExamenRecevabiliteDto = {
  aExaminer: true,
  fraisPayes: true,
  piecesManquantes: [],
  peutEtreRecevable: true,
};

function afficher(examen: ExamenRecevabiliteDto, declarer = vi.fn()) {
  render(
    <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
      <ExamenRecevabilite
        demandeId={41}
        numero="DEM-2026-000041"
        examen={examen}
        piecesRequises={PIECES}
        declarer={declarer}
      />
    </NextIntlClientProvider>,
  );
  return declarer;
}

beforeEach(() => {
  push.mockReset();
  refresh.mockReset();
});

describe("examen de recevabilité (US-05.1)", () => {
  it("liste les contrôles : frais et chaque pièce obligatoire, avec un texte explicite", () => {
    afficher({
      ...complet,
      fraisPayes: false,
      piecesManquantes: ["EIES"],
      peutEtreRecevable: false,
    });
    const liste = screen.getByRole("list");
    expect(within(liste).getByText("Frais de dossier non payés")).toBeInTheDocument();
    expect(within(liste).getByText("Étude de faisabilité : fournie")).toBeInTheDocument();
    expect(within(liste).getByText("EIES : manquante")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Déclarer recevable/ })).not.toBeInTheDocument();
    expect(screen.getByText(/ne peut pas encore être déclarée recevable/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Déclarer non recevable/ })).toBeInTheDocument();
  });

  it("exige la vérification des pièces avant de déclarer la demande recevable", async () => {
    const declarer = afficher(
      complet,
      vi.fn().mockResolvedValue({ ok: true, valeur: { demandeId: 41, statut: "recevable" } }),
    );
    fireEvent.click(screen.getByRole("button", { name: /Déclarer recevable/ }));
    expect(declarer).not.toHaveBeenCalled();
    expect(screen.getByText("Cochez cette case après avoir vérifié les pièces.")).toBeVisible();
    fireEvent.click(screen.getByLabelText(/J’ai ouvert et vérifié chaque pièce jointe/));
    fireEvent.click(screen.getByRole("button", { name: /Déclarer recevable/ }));
    await waitFor(() => expect(declarer).toHaveBeenCalledWith(41, true, null));
    await waitFor(() =>
      expect(push).toHaveBeenCalledWith(
        "/a-traiter?groupe=a-examiner&traitee=DEM-2026-000041&resultat=recevable",
      ),
    );
  });

  it("refuse un motif trop court puis envoie le motif nettoyé de la non-recevabilité", async () => {
    const declarer = afficher(
      complet,
      vi.fn().mockResolvedValue({ ok: true, valeur: { demandeId: 41, statut: "rejetee" } }),
    );
    fireEvent.click(screen.getByRole("button", { name: /Déclarer non recevable/ }));
    const motif = screen.getByLabelText(/Motif communiqué au demandeur/);
    fireEvent.change(motif, { target: { value: "  Court " } });
    fireEvent.click(screen.getByRole("button", { name: "Confirmer la non-recevabilité" }));
    expect(declarer).not.toHaveBeenCalled();
    expect(motif).toHaveAttribute("aria-invalid", "true");
    expect(screen.getByText(/10 caractères au moins : le demandeur le lira/)).toBeInTheDocument();
    fireEvent.change(motif, { target: { value: "  L'EIES n'est pas signée.  " } });
    fireEvent.click(screen.getByRole("button", { name: "Confirmer la non-recevabilité" }));
    await waitFor(() =>
      expect(declarer).toHaveBeenCalledWith(41, false, "L'EIES n'est pas signée."),
    );
    await waitFor(() =>
      expect(push).toHaveBeenCalledWith(
        "/a-traiter?groupe=a-examiner&traitee=DEM-2026-000041&resultat=rejetee",
      ),
    );
  });

  it("affiche le refus de l'API sans quitter la fiche (frais devenus non payés…)", async () => {
    afficher(
      complet,
      vi.fn().mockResolvedValue({
        ok: false,
        erreur: { code: "DEM-006", message: "Les frais de dossier ne sont pas payés.", champs: [] },
      }),
    );
    fireEvent.click(screen.getByLabelText(/J’ai ouvert et vérifié chaque pièce jointe/));
    fireEvent.click(screen.getByRole("button", { name: /Déclarer recevable/ }));
    expect(await screen.findByText("Les frais de dossier ne sont pas payés.")).toBeInTheDocument();
    expect(push).not.toHaveBeenCalled();
    expect(refresh).toHaveBeenCalled();
  });

  it("borne le motif comme l'API : 10 à 2000 caractères hors espaces", () => {
    expect(erreurMotif("   ")).toBe("court");
    expect(erreurMotif(" 123456789 ")).toBe("court");
    expect(erreurMotif("1234567890")).toBeNull();
    expect(erreurMotif("x".repeat(2001))).toBe("long");
  });
});
