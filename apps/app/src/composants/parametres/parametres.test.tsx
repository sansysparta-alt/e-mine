import type { CanauxDto, ParametresDelaiDto } from "@emine/api-client";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import fr from "../../../messages/fr.json";
import { FormulaireCanaux } from "./formulaire-canaux";
import { FormulaireDelais } from "./formulaire-delais";

const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), refresh }) }));

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

describe("règle des délais (US-02.6)", () => {
  it("l'administrateur modifie la règle et voit la confirmation", async () => {
    const enregistrer = vi
      .fn()
      .mockImplementation(async (p: ParametresDelaiDto) => ({ ok: true, valeur: p }));
    avecTraductions(<FormulaireDelais initial={delais} modifiable enregistrer={enregistrer} />);
    fireEvent.click(screen.getByLabelText(/Au dépôt de la demande/));
    fireEvent.click(screen.getByLabelText("Pendant l’attente du paiement des droits"));
    fireEvent.change(screen.getByLabelText("Part du délai restant, en %"), {
      target: { value: "25" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Enregistrer la règle des délais" }));
    await waitFor(() =>
      expect(enregistrer).toHaveBeenCalledWith({
        depart: "depot",
        pauseComplement: true,
        pauseDroits: false,
        seuilProchePourcent: 25,
        seuilProcheMinJours: 2,
      }),
    );
    expect(await screen.findByText("C’est enregistré")).toBeInTheDocument();
  });

  it("un seuil hors bornes est signalé et rien n'est envoyé", () => {
    const enregistrer = vi.fn();
    avecTraductions(<FormulaireDelais initial={delais} modifiable enregistrer={enregistrer} />);
    fireEvent.change(screen.getByLabelText("Part du délai restant, en %"), {
      target: { value: "0" },
    });
    expect(screen.getByText("Saisissez un nombre entier entre 1 et 100.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Enregistrer la règle des délais" }));
    expect(enregistrer).not.toHaveBeenCalled();
  });

  it("le Ministre consulte sans pouvoir modifier", () => {
    avecTraductions(<FormulaireDelais initial={delais} modifiable={false} enregistrer={vi.fn()} />);
    expect(screen.queryByRole("button")).toBeNull();
    expect(screen.getByLabelText(/Au dépôt de la demande/)).toBeDisabled();
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
