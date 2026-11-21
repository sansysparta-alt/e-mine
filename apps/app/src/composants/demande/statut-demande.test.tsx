import { render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import fr from "../../../messages/fr.json";
import { afficherFraisPayes, StatutDemande } from "./statut-demande";

function afficher(statut: string, fraisPayes: boolean) {
  render(
    <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
      <StatutDemande statut={statut} fraisPayes={fraisPayes} />
    </NextIntlClientProvider>,
  );
}

describe("StatutDemande (décision J3 : « Déposée · Frais payés »)", () => {
  it("affiche « Déposée » et « Frais payés » quand les frais sont payés", () => {
    afficher("deposee", true);
    expect(screen.getByText("Déposée")).toBeInTheDocument();
    expect(screen.getByText("Frais payés")).toBeInTheDocument();
  });

  it("n'affiche pas « Frais payés » tant que rien n'est payé", () => {
    afficher("deposee", false);
    expect(screen.getByText("Déposée")).toBeInTheDocument();
    expect(screen.queryByText("Frais payés")).not.toBeInTheDocument();
  });

  it("n'encombre plus le statut une fois la demande recevable", () => {
    expect(afficherFraisPayes("recevable", true)).toBe(false);
    expect(afficherFraisPayes("complement_requis", true)).toBe(true);
  });
});
