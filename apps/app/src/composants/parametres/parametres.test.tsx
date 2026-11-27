import type { CanauxDto, ParametresDelaiDto } from "@emine/api-client";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import fr from "../../../messages/fr.json";
import { FormulaireCanaux } from "./formulaire-canaux";
import { FormulaireDelais } from "./formulaire-delais";

const refresh = vi.fn();
const push = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push, refresh }) }));

function avecTraductions(enfant: React.ReactNode) {
  return render(
    <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
      {enfant}
    </NextIntlClientProvider>,
  );
}

const delais: ParametresDelaiDto = {
  depart: "paiement_frais",
  pauseComplement: true,
  pauseDroits: true,
  seuilProchePourcent: 20,
  seuilProcheMinJours: 2,
};

const canaux: CanauxDto[] = [
  {
    modele: "DEMANDE_DEPOSEE",
    libelleFr: "Demande déposée",
    libelleEn: "Application submitted",
    sms: true,
    email: true,
    interne: true,
  },
  {
    modele: "DEMANDE_REJETEE",
    libelleFr: "Demande rejetée",
    libelleEn: "Application rejected",
    sms: false,
    email: true,
    interne: false,
  },
];

describe("proposition d'une règle des délais (US-02.6, US-02.8)", () => {
  it("l'administrateur propose une règle avec motif et date d'effet, soumise au Ministre", async () => {
    const proposer = vi.fn().mockResolvedValue({ ok: true, valeur: { version: 2 } });
    avecTraductions(<FormulaireDelais initial={delais} proposer={proposer} />);
    fireEvent.click(screen.getByLabelText(/Au dépôt de la demande/));
    fireEvent.click(screen.getByLabelText("Pendant l’attente du paiement des droits"));
    fireEvent.change(screen.getByLabelText("Part du délai restant, en %"), {
      target: { value: "25" },
    });
    // Sans motif, rien n'est envoyé.
    fireEvent.click(screen.getByRole("button", { name: "Soumettre au Ministre" }));
    expect(
      screen.getByText("Expliquez la modification en 10 caractères au moins."),
    ).toBeInTheDocument();
    expect(proposer).not.toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText("Motif de la modification"), {
      target: { value: "Aligner le départ sur le dépôt (test)" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Soumettre au Ministre" }));
    await waitFor(() =>
      expect(proposer).toHaveBeenCalledWith(
        expect.objectContaining({
          depart: "depot",
          pauseComplement: true,
          pauseDroits: false,
          seuilProchePourcent: 25,
          seuilProcheMinJours: 2,
          motif: "Aligner le départ sur le dépôt (test)",
          dateEffet: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/),
        }),
      ),
    );
    expect(push).toHaveBeenCalledWith("/parametres?proposee=2&regle=delais");
  });

  it("affiche chaque changement « de … à … », seulement pour les valeurs modifiées (US-02.10)", () => {
    avecTraductions(<FormulaireDelais initial={delais} proposer={vi.fn()} />);
    const zone = () => screen.getByRole("heading", { name: "Vos changements" }).parentElement!;
    expect(
      within(zone()).getByText("Aucun changement par rapport à la dernière règle validée."),
    ).toBeVisible();
    fireEvent.click(screen.getByLabelText(/Au dépôt de la demande/));
    expect(zone()).toHaveTextContent(
      "Le délai commence : de « Au paiement des frais de dossier » à « Au dépôt de la demande »",
    );
    // Les critères inchangés ne sont pas listés.
    expect(zone()).not.toHaveTextContent("Le délai est en pause");
    // Saisie en cours non valide : pas de « NaN » dans la comparaison.
    fireEvent.change(screen.getByLabelText("Part du délai restant, en %"), {
      target: { value: "" },
    });
    expect(zone()).not.toHaveTextContent("NaN");
  });

  it("une date d'effet passée ou un seuil hors bornes est signalé et rien n'est envoyé", () => {
    const proposer = vi.fn();
    avecTraductions(<FormulaireDelais initial={delais} proposer={proposer} />);
    fireEvent.change(screen.getByLabelText("Part du délai restant, en %"), {
      target: { value: "0" },
    });
    expect(screen.getByText("Saisissez un nombre entier entre 1 et 100.")).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Date d’effet"), { target: { value: "2020-01-01" } });
    fireEvent.change(screen.getByLabelText("Motif de la modification"), {
      target: { value: "Motif suffisamment long" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Soumettre au Ministre" }));
    expect(screen.getByText("Choisissez aujourd’hui ou une date à venir.")).toBeInTheDocument();
    expect(proposer).not.toHaveBeenCalled();
  });

  it("une règle identique à la dernière validée est signalée clairement", async () => {
    const proposer = vi
      .fn()
      .mockResolvedValue({ ok: false, erreur: { code: "DOC-010", message: "x", champs: [] } });
    avecTraductions(<FormulaireDelais initial={delais} proposer={proposer} />);
    fireEvent.change(screen.getByLabelText("Motif de la modification"), {
      target: { value: "Aucun changement réel" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Soumettre au Ministre" }));
    expect(
      await screen.findByText("Rien n’a changé par rapport à la dernière règle validée."),
    ).toBeInTheDocument();
  });
});

describe("canaux des notifications (US-02.7)", () => {
  it("chaque case est nommée par son canal et son événement ; le coût des SMS est rappelé", async () => {
    const enregistrer = vi.fn().mockImplementation(async (c: CanauxDto[]) => ({
      ok: true,
      valeur: c.map((x, i) => ({ ...canaux[i]!, ...x })),
    }));
    avecTraductions(<FormulaireCanaux initial={canaux} modifiable enregistrer={enregistrer} />);
    expect(screen.getByText(/Le SMS est actif pour 1 événement sur 2/)).toBeInTheDocument();
    fireEvent.click(screen.getByLabelText("SMS — Demande déposée"));
    expect(screen.getByText(/Le SMS est actif pour aucun événement sur 2/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Enregistrer les canaux" }));
    await waitFor(() =>
      expect(enregistrer).toHaveBeenCalledWith([
        { modele: "DEMANDE_DEPOSEE", sms: false, email: true, interne: true },
        { modele: "DEMANDE_REJETEE", sms: false, email: true, interne: true },
      ]),
    );
  });

  it("l'espace en ligne est toujours coché et ne se décoche pas", () => {
    avecTraductions(<FormulaireCanaux initial={canaux} modifiable enregistrer={vi.fn()} />);
    const interne = screen.getByLabelText("Espace en ligne — Demande rejetée (toujours actif)");
    expect(interne).toBeChecked();
    expect(interne).toBeDisabled();
  });
});

describe("proposition d'une règle de remboursement (US-02.10)", () => {
  it("montre le changement « de … à … » et renvoie vers la règle nommée", async () => {
    const { FormulaireRegleRemboursement } = await import("./regle-remboursement");
    const proposer = vi.fn().mockResolvedValue({ ok: true, valeur: { version: 3 } });
    avecTraductions(<FormulaireRegleRemboursement enVigueur={false} proposer={proposer} />);
    fireEvent.click(screen.getByRole("button", { name: "Proposer une modification" }));
    const zone = screen.getByRole("heading", { name: "Vos changements" }).parentElement!;
    expect(zone).toHaveTextContent(
      "Frais de dossier d’une demande rejetée : de « non remboursés » à « remboursés »",
    );
    fireEvent.change(screen.getByLabelText(/Motif de la modification/), {
      target: { value: "Décision du Ministère (test)" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Proposer au Ministre" }));
    await waitFor(() =>
      expect(push).toHaveBeenCalledWith("/parametres?proposee=3&regle=remboursement"),
    );
  });
});
