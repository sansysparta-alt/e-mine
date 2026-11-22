import { fireEvent, render, screen } from "@testing-library/react";
import { IndicateurEtapes } from "./indicateur-etapes";

const libellesEtat = { faite: "étape faite", courante: "étape en cours", "a-venir": "à venir" };

describe("indicateur de progression du formulaire (correctifs ergonomie)", () => {
  it("distingue étape faite, en cours et à venir ; une étape non atteinte n'est pas cliquable", () => {
    const surChoix = vi.fn();
    render(
      <IndicateurEtapes
        titre="Étapes"
        libellesEtat={libellesEtat}
        surChoix={surChoix}
        etapes={[
          { cle: "document", libelle: "Document", etat: "faite", accessible: true },
          { cle: "pieces", libelle: "Pièces", etat: "courante", accessible: true },
          { cle: "recapitulatif", libelle: "Récapitulatif", etat: "a-venir", accessible: false },
        ]}
      />,
    );
    const faite = screen.getByRole("button", { name: /Document\s*\(étape faite\)/ });
    fireEvent.click(faite);
    expect(surChoix).toHaveBeenCalledWith("document");
    // L'étape en cours est signalée et n'est pas un bouton.
    expect(screen.queryByRole("button", { name: /Pièces/ })).toBeNull();
    expect(screen.getByText("Pièces").closest("li")).toHaveAttribute("aria-current", "step");
    // L'étape à venir n'a pas l'air cliquable.
    expect(screen.queryByRole("button", { name: /Récapitulatif/ })).toBeNull();
    expect(screen.getByText("(à venir)", { exact: false })).toBeInTheDocument();
  });
});
