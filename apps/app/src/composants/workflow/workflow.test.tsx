import type { VersionWorkflowDto, WorkflowDto } from "@emine/api-client";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import fr from "../../../messages/fr.json";
import { FormulaireWorkflow } from "./formulaire-workflow";
import { TableauWorkflows } from "./tableau-workflows";

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

const enVigueur: VersionWorkflowDto = {
  version: 1,
  etat: "en_vigueur",
  libelle: "Export et expertise",
  reglePaiement: "Frais d'expertise puis droits d'export",
  motif: "Workflow initial",
  modifieePar: null,
  modifieeLe: "2026-01-01T00:00:00Z",
  dateEffet: "2026-01-01",
  decideePar: null,
  decideeLe: "2026-01-01T00:00:00Z",
  motifRefus: null,
  libelleEn: "Export and appraisal",
  reglePaiementEn: null,
  etapes: [
    { acteur: "BNE", libelle: "Expertise du lot", acteurEn: "BNE", libelleEn: "Lot appraisal" },
    { acteur: "DNM, Fiscalité ou Brigade", libelle: "Contrôle", acteurEn: null, libelleEn: null },
    { acteur: "Signataire", libelle: "Signature", acteurEn: null, libelleEn: null },
  ],
};

beforeEach(() => {
  push.mockReset();
  refresh.mockReset();
});

describe("liste des workflows (US-02.5)", () => {
  it("la ligne entière ouvre le workflow ; délais en fourchette", () => {
    const w: WorkflowDto = {
      code: "EXPORT",
      delaiMinJours: 2,
      delaiMaxJours: 3,
      documents: [
        {
          code: "AEX",
          libelleFr: "Autorisation d'exportation",
          libelleEn: "Export authorisation",
          actif: true,
        },
        {
          code: "CEX",
          libelleFr: "Certificat d'expertise (BNE)",
          libelleEn: "Expert appraisal certificate (BNE)",
          actif: false,
        },
      ],
      enVigueur,
      aValider: { ...enVigueur, version: 2, etat: "a_valider", decideeLe: null },
      programmee: null,
    };
    avecTraductions(<TableauWorkflows lignes={[w]} legende="Workflows" decideur />);
    expect(screen.getByRole("link", { name: /Export et expertise/ })).toHaveAttribute(
      "href",
      "/workflows/EXPORT",
    );
    expect(screen.getAllByText("2 à 3 jours").length).toBeGreaterThan(0);
    // Codes expliqués en clair, sans survol (lisible sur mobile) ; un document désactivé le dit.
    expect(document.querySelector("abbr")).toBeNull();
    expect(screen.getByText(/Autorisation d'exportation/)).toBeVisible();
    expect(screen.getByText(/Certificat d'expertise \(BNE\) \(désactivé\)/)).toBeVisible();
    // Une proposition attend le Ministre : un seul badge, orange pour lui.
    expect(screen.getByText("À valider")).toBeVisible();
  });
});

describe("proposition de modification d'un workflow (US-02.5, US-02.8)", () => {
  it("réordonne, ajoute et retire des étapes, exige un motif, puis soumet au Ministre", async () => {
    const enregistrer = vi.fn().mockResolvedValue({ ok: true, valeur: { version: 2 } });
    avecTraductions(
      <FormulaireWorkflow code="EXPORT" enVigueur={enVigueur} enregistrer={enregistrer} />,
    );
    fireEvent.click(screen.getByRole("button", { name: "Descendre l’étape 1" }));
    fireEvent.click(screen.getByRole("button", { name: "Retirer l’étape 3" }));
    fireEvent.click(screen.getByRole("button", { name: "Ajouter une étape" }));
    const nouvelle = screen.getByRole("listitem", { name: "Étape 3" });
    fireEvent.change(within(nouvelle).getByLabelText(/^Intervenant(?!, en anglais)/), {
      target: { value: "Directeur" },
    });
    fireEvent.change(within(nouvelle).getByLabelText(/^Action(?!, en anglais)/), {
      target: { value: "Signature" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Soumettre au Ministre" }));
    expect(
      await screen.findByText("Expliquez la modification en 10 à 2 000 caractères."),
    ).toBeInTheDocument();
    expect(enregistrer).not.toHaveBeenCalled();

    fireEvent.change(screen.getByLabelText(/Motif de la modification/), {
      target: { value: "Contrôle avant expertise (test)" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Soumettre au Ministre" }));
    await waitFor(() =>
      expect(enregistrer).toHaveBeenCalledWith(
        "EXPORT",
        expect.objectContaining({
          libelleEn: "Export and appraisal",
          reglePaiementEn: null,
          etapes: [
            {
              acteur: "DNM, Fiscalité ou Brigade",
              libelle: "Contrôle",
              acteurEn: null,
              libelleEn: null,
            },
            {
              acteur: "BNE",
              libelle: "Expertise du lot",
              acteurEn: "BNE",
              libelleEn: "Lot appraisal",
            },
            { acteur: "Directeur", libelle: "Signature", acteurEn: null, libelleEn: null },
          ],
        }),
      ),
    );
    // La date d'effet proposée est aujourd'hui par défaut.
    expect(enregistrer.mock.calls[0]?.[1]?.dateEffet).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(push).toHaveBeenCalledWith("/workflows/EXPORT?proposee=2");
  });

  it("une version identique est signalée clairement", async () => {
    const enregistrer = vi.fn().mockResolvedValue({
      ok: false,
      erreur: { code: "DOC-010", message: "x", champs: [] },
    });
    avecTraductions(
      <FormulaireWorkflow code="EXPORT" enVigueur={enVigueur} enregistrer={enregistrer} />,
    );
    fireEvent.change(screen.getByLabelText(/Motif de la modification/), {
      target: { value: "Aucun changement réel" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Soumettre au Ministre" }));
    expect(
      await screen.findByText("Rien n’a changé par rapport à la dernière version validée."),
    ).toBeInTheDocument();
  });

  it("la seule étape restante ne peut pas être retirée", () => {
    avecTraductions(
      <FormulaireWorkflow
        code="EXPORT"
        enVigueur={{ ...enVigueur, etapes: [enVigueur.etapes[0]!] }}
        enregistrer={vi.fn()}
      />,
    );
    expect(screen.getByRole("button", { name: "Retirer l’étape 1" })).toBeDisabled();
  });
});
