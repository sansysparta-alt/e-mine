import type { EtatAvisDto } from "@emine/api-client";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import fr from "../../../messages/fr.json";
import { presentationStatut } from "../demande/statut";
import { prochaineEtape } from "../demande/prochaine-etape";
import { actionPour, groupesDepuis } from "../travail/file-travail";
import { ListeAvis, RendreAvis } from "./avis";

const push = vi.fn();
const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push, refresh }) }));

function avecIntl(enfant: ReactNode) {
  return render(
    <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
      {enfant}
    </NextIntlClientProvider>,
  );
}

beforeEach(() => {
  push.mockReset();
  refresh.mockReset();
});

describe("avis techniques (US-05.3)", () => {
  it("liste chaque avis requis : sens en texte et icône, auteur, date, motivation ; attente explicite", () => {
    const avis: EtatAvisDto = {
      peutRendre: false,
      requis: [
        {
          profil: "DNM",
          libelle: "Agent DNM",
          rendu: true,
          favorable: false,
          motif: "Plan de restauration insuffisant.",
          auteur: "Nènè Sow",
          renduLe: "2026-11-21T10:00:00Z",
        },
        {
          profil: "INSPECTEUR",
          libelle: "Inspecteur des Mines",
          rendu: false,
          favorable: null,
          motif: null,
          auteur: null,
          renduLe: null,
        },
      ],
    };
    avecIntl(<ListeAvis avis={avis} />);
    expect(screen.getByText(/avis défavorable/)).toBeInTheDocument();
    expect(screen.getByText("Rendu par Nènè Sow le 21/11/2026")).toBeInTheDocument();
    expect(screen.getByText("Plan de restauration insuffisant.")).toBeInTheDocument();
    expect(screen.getByText(/avis attendu/)).toBeInTheDocument();
  });

  it("l'avis exige un sens choisi (rien de présélectionné) et une motivation, puis ouvre la suite", async () => {
    const rendre = vi
      .fn()
      .mockResolvedValue({ ok: true, valeur: { demandeId: 7, statut: "avis_rendu" } });
    avecIntl(<RendreAvis demandeId={7} numero="DEM-2026-000007" rendre={rendre} />);
    expect(screen.getByRole("radio", { name: /Favorable/ })).not.toBeChecked();
    expect(screen.getByRole("radio", { name: /Défavorable/ })).not.toBeChecked();
    fireEvent.click(screen.getByRole("button", { name: "Enregistrer mon avis" }));
    expect(await screen.findByText("Choisissez le sens de votre avis.")).toBeInTheDocument();
    expect(screen.getByText(/le signataire le lira\./)).toBeInTheDocument();
    expect(screen.queryByText(/le demandeur le lira/)).not.toBeInTheDocument();
    expect(rendre).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("radio", { name: /Défavorable/ }));
    fireEvent.change(screen.getByRole("textbox", { name: /Motivation/ }), {
      target: { value: "Plan de restauration insuffisant sur la zone nord." },
    });
    fireEvent.click(screen.getByRole("button", { name: "Enregistrer mon avis" }));
    await waitFor(() =>
      expect(rendre).toHaveBeenCalledWith(
        7,
        false,
        "Plan de restauration insuffisant sur la zone nord.",
      ),
    );
    expect(push).toHaveBeenCalledWith(
      "/a-traiter?groupe=avis-a-rendre&traitee=DEM-2026-000007&resultat=avis_rendu",
    );
  });

  it("libellés de l'agent : « Avis à rendre » orange pour lui seul ; « À décider » après les avis", () => {
    const d = { fraisPayes: true };
    expect(
      presentationStatut({ ...d, statut: "en_instruction", aTraiterParMoi: true }, true),
    ).toMatchObject({ cle: "avisARendre", ton: "avertissement" });
    expect(
      presentationStatut({ ...d, statut: "en_instruction", aTraiterParMoi: false }, true),
    ).toMatchObject({ cle: "instruction", ton: "info" });
    expect(
      presentationStatut({ ...d, statut: "avis_rendu", aTraiterParMoi: true }, true),
    ).toMatchObject({ cle: "aDecider", ton: "avertissement" });
    expect(
      presentationStatut({ ...d, statut: "avis_rendu", aTraiterParMoi: false }, false),
    ).toMatchObject({ cle: "instruction", ton: "info" });
  });

  it("file de travail : le groupe « Avis à rendre » vient de l'API ; actions attendues", () => {
    const groupes = groupesDepuis(
      {
        total: 3,
        groupes: [
          { cle: "avis-a-rendre", nombre: 3 },
          { cle: "sans-reponse", nombre: 0 },
        ],
      },
      { interne: true, permissions: ["INSTRUIRE"], profil: { code: "DNM" } },
    );
    expect(groupes.map((g) => g.cle)).toEqual([
      "tout",
      "avis-a-rendre",
      "sans-reponse",
      "frais-attendus",
    ]);
    expect(actionPour("en_instruction", true)).toBe("aviser");
    expect(actionPour("avis_rendu", true)).toBe("decider");
  });

  it("prochaine étape à l'instruction : un texte qui vaut pour tout agent", () => {
    expect(prochaineEtape("en_instruction", { interne: true, permissions: ["INSTRUIRE"] })).toEqual(
      { cle: "agentAttenteAvis", pour: "agent" },
    );
  });
});
