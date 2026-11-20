import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Alerte } from "./alerte";
import { Bouton } from "./bouton";
import { Carte } from "./carte";
import { LienEvitement } from "./lien-evitement";

describe("Bouton", () => {
  it("est un vrai bouton, activable au clavier, avec une zone cliquable suffisante", async () => {
    const clic = vi.fn();
    render(<Bouton onClick={clic}>Se connecter</Bouton>);
    const bouton = screen.getByRole("button", { name: "Se connecter" });
    bouton.focus();
    await userEvent.keyboard("{Enter}");
    expect(clic).toHaveBeenCalledOnce();
    expect(bouton.className).toContain("min-h-11");
  });

  it("peut habiller un lien sans perdre son rôle de lien", () => {
    render(
      <Bouton asChild>
        <a href="/connexion">Connexion</a>
      </Bouton>,
    );
    expect(screen.getByRole("link", { name: "Connexion" })).toHaveAttribute("href", "/connexion");
  });
});

describe("Alerte", () => {
  it("annonce tout de suite une erreur aux lecteurs d'écran", () => {
    render(
      <Alerte nature="erreur" titre="Connexion impossible">
        Réessayez dans quelques minutes.
      </Alerte>,
    );
    const alerte = screen.getByRole("alert");
    expect(alerte).toHaveTextContent("Connexion impossible");
    expect(alerte).toHaveTextContent("Réessayez dans quelques minutes.");
  });

  it("annonce poliment une information", () => {
    render(<Alerte titre="Vous êtes déconnecté" />);
    expect(screen.getByRole("status")).toHaveTextContent("Vous êtes déconnecté");
  });
});

describe("Carte et lien d'évitement", () => {
  it("titre la carte au niveau 2", () => {
    render(<Carte titre="Mes demandes">Aucune demande</Carte>);
    expect(screen.getByRole("heading", { level: 2, name: "Mes demandes" })).toBeInTheDocument();
  });

  it("pointe vers le contenu principal", () => {
    render(<LienEvitement>Aller au contenu</LienEvitement>);
    expect(screen.getByRole("link", { name: "Aller au contenu" })).toHaveAttribute(
      "href",
      "#contenu",
    );
  });
});
