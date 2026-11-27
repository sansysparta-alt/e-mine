import type { SignalementDto } from "@emine/api-client";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import fr from "../../../messages/fr.json";
import { ListeSignalements, presentationSignalement, SignalerDocument } from "./signalements";
import { TableauSignalements } from "./tableau-signalements";

const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), refresh }) }));

function afficher(enfant: React.ReactNode) {
  return render(
    <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
      {enfant}
    </NextIntlClientProvider>,
  );
}

const ouvert: SignalementDto = {
  id: 7,
  demandeId: 41,
  numeroDemande: "DEM-2026-000041",
  numeroDocument: "PEX-2026-0001",
  statut: "ouvert",
  motif: "Copie falsifiée présentée à un contrôle routier.",
  auteur: "Alpha Condé",
  signaleLe: "2026-11-27T10:00:00Z",
  decidePar: null,
  decideLe: null,
  motifDecision: null,
  aTraiterParMoi: true,
};

beforeEach(() => refresh.mockReset());

describe("présentation d'un signalement (un seul badge, selon le profil)", () => {
  it("orange pour le signataire, gris pour les autres, rouge si révoqué, vert si classé", () => {
    expect(presentationSignalement(ouvert)).toEqual({ cle: "aDecider", ton: "avertissement" });
    expect(presentationSignalement({ ...ouvert, aTraiterParMoi: false })).toEqual({
      cle: "attenteSignataire",
      ton: "neutre",
    });
    expect(presentationSignalement({ statut: "annule", aTraiterParMoi: false }).ton).toBe("danger");
    expect(presentationSignalement({ statut: "classe", aTraiterParMoi: false }).ton).toBe("succes");
  });
});

describe("la Brigade signale (US-06.8)", () => {
  it("exige un motif, envoie le signalement et l'annonce", async () => {
    const signaler = vi.fn().mockResolvedValue({ ok: true, valeur: null });
    afficher(<SignalerDocument demandeId={41} numero="PEX-2026-0001" signaler={signaler} />);
    fireEvent.click(screen.getByRole("button", { name: "Signaler au signataire…" }));
    expect(screen.getByText(/reste « authentique »/)).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Envoyer le signalement" }));
    expect(signaler).not.toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText(/Motif du signalement/), {
      target: { value: "Copie falsifiée constatée." },
    });
    fireEvent.click(screen.getByRole("button", { name: "Envoyer le signalement" }));
    await waitFor(() => expect(signaler).toHaveBeenCalledWith(41, "Copie falsifiée constatée."));
    expect(await screen.findByRole("status")).toHaveTextContent("signalé à son signataire");
    expect(refresh).toHaveBeenCalled();
  });

  it("affiche l'erreur de l'API sans fermer sur un motif refusé", async () => {
    const signaler = vi.fn().mockResolvedValue({
      ok: false,
      erreur: {
        code: "VAL-001",
        message: "x",
        champs: [{ champ: "motif", message: "Trop court" }],
      },
    });
    afficher(<SignalerDocument demandeId={41} numero="PEX-2026-0001" signaler={signaler} />);
    fireEvent.click(screen.getByRole("button", { name: "Signaler au signataire…" }));
    fireEvent.change(screen.getByLabelText(/Motif du signalement/), {
      target: { value: "Copie falsifiée constatée." },
    });
    fireEvent.click(screen.getByRole("button", { name: "Envoyer le signalement" }));
    expect(await screen.findByText("Trop court")).toBeVisible();
  });
});

describe("le signataire décide (US-06.8)", () => {
  it("révoque ou classe, avec un motif, seulement sur ses signalements ouverts", async () => {
    const decider = vi.fn().mockResolvedValue({ ok: true, valeur: null });
    afficher(<ListeSignalements signalements={[ouvert]} decider={decider} />);
    expect(screen.getByText("À décider")).toBeVisible();
    expect(screen.getByText(/Signalé par Alpha Condé, le 27\/11\/2026/)).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Classer le signalement…" }));
    const dialogue = screen.getByRole("dialog");
    fireEvent.change(within(dialogue).getByLabelText(/Motif du classement/), {
      target: { value: "Vérifié : l'original est authentique." },
    });
    fireEvent.click(within(dialogue).getByRole("button", { name: "Classer le signalement" }));
    await waitFor(() =>
      expect(decider).toHaveBeenCalledWith(7, "CLASSER", "Vérifié : l'original est authentique."),
    );
    fireEvent.click(screen.getByRole("button", { name: "Révoquer le document…" }));
    fireEvent.change(screen.getByLabelText(/Motif de la révocation/), {
      target: { value: "Fraude confirmée." },
    });
    fireEvent.click(screen.getByRole("button", { name: "Révoquer définitivement" }));
    await waitFor(() => expect(decider).toHaveBeenCalledWith(7, "ANNULER", "Fraude confirmée."));
  });

  it("n'offre aucune décision à un autre profil et montre la décision prise", () => {
    afficher(
      <ListeSignalements
        decider={vi.fn()}
        signalements={[
          { ...ouvert, aTraiterParMoi: false },
          {
            ...ouvert,
            id: 8,
            statut: "classe",
            aTraiterParMoi: false,
            decidePar: "Sékou Kouyaté",
            decideLe: "2026-11-27T12:00:00Z",
            motifDecision: "Original vérifié aux archives.",
          },
        ]}
      />,
    );
    expect(screen.queryByRole("button")).toBeNull();
    expect(screen.getByText("Attend le signataire")).toBeVisible();
    expect(screen.getByText(/Signalement classé par Sékou Kouyaté/)).toBeVisible();
    expect(screen.getByText("Original vérifié aux archives.")).toBeVisible();
  });

  it("liste du signataire : la ligne ouvre la demande à ses signalements", () => {
    afficher(<TableauSignalements signalements={[ouvert]} />);
    const lien = screen.getAllByRole("link")[0];
    expect(lien).toHaveAttribute("href", "/demandes/41#signalements");
    expect(screen.getByText("PEX-2026-0001")).toBeVisible();
    expect(screen.getByText("Décider")).toBeVisible();
  });
});
