import { render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import fr from "../../../messages/fr.json";
import { StatutDemande } from "./statut-demande";

function afficher(statut: string, fraisPayes: boolean, aTraiterParMoi: boolean, interne: boolean) {
  return render(
    <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
      <StatutDemande demande={{ statut, fraisPayes, aTraiterParMoi }} interne={interne} />
    </NextIntlClientProvider>,
  );
}

describe("StatutDemande : un seul badge, libellé selon le profil", () => {
  it("l'investisseur lit « En cours de vérification », le guichet « À examiner »", () => {
    const { unmount } = afficher("deposee", true, false, false);
    expect(screen.getByText("En cours de vérification")).toBeInTheDocument();
    expect(screen.queryByText("Frais payés")).not.toBeInTheDocument();
    unmount();
    afficher("deposee", true, true, true);
    expect(screen.getByText("À examiner")).toBeInTheDocument();
  });

  it("n'affiche qu'un seul badge par demande", () => {
    const { container } = afficher("decision", true, true, false);
    expect(screen.getByText("Droits à payer")).toBeInTheDocument();
    expect(container.querySelectorAll("svg")).toHaveLength(1);
  });
});
