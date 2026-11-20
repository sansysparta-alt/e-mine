import { render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import fr from "../../messages/fr.json";
import { EnTete } from "./en-tete";
import { MessageConnexion, motifDepuisParametres } from "./message-connexion";
import { VueTableauDeBord } from "./vue-tableau-de-bord";

function enFrancais(enfant: ReactNode) {
  return render(
    <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
      {enfant}
    </NextIntlClientProvider>,
  );
}

describe("tableau de bord", () => {
  it("salue l'investisseur par son nom et affiche son profil et ses demandes", () => {
    enFrancais(
      <VueTableauDeBord
        prenom="Fodé"
        nom="Bangoura"
        profilLibelle="Investisseur Extérieur"
        interne={false}
      />,
    );
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("Bonjour, Fodé Bangoura");
    expect(screen.getByText("Profil : Investisseur Extérieur")).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 2, name: "Mes demandes" })).toBeInTheDocument();
    expect(screen.getByText("Vous n'avez encore aucune demande.")).toBeInTheDocument();
  });

  it("montre à un agent interne les dossiers à traiter", () => {
    enFrancais(
      <VueTableauDeBord prenom="Aïcha" nom="Camara" profilLibelle="Agent Guichet Unique" interne />,
    );
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("Bonjour, Aïcha Camara");
    expect(
      screen.getByRole("heading", { level: 2, name: "Dossiers à traiter" }),
    ).toBeInTheDocument();
  });
});

describe("droit de déposer (US-01.6)", () => {
  it("propose une nouvelle demande seulement si le profil peut déposer", () => {
    const { unmount } = enFrancais(
      <VueTableauDeBord
        prenom="Fodé"
        nom="Bangoura"
        profilLibelle="Investisseur"
        interne={false}
        peutDeposer
      />,
    );
    expect(screen.getByRole("link", { name: "Nouvelle demande" })).toHaveAttribute(
      "href",
      "/demandes/nouvelle",
    );
    unmount();
    enFrancais(<VueTableauDeBord prenom="Aïcha" nom="Camara" profilLibelle="Guichet" interne />);
    expect(screen.queryByRole("link", { name: "Nouvelle demande" })).not.toBeInTheDocument();
  });
});

describe("en-tête", () => {
  it("affiche l'utilisateur et déconnecte par un formulaire POST", () => {
    enFrancais(<EnTete nomComplet="Fodé Bangoura" profil="Investisseur Extérieur" />);
    expect(screen.getByText("Connecté : Fodé Bangoura")).toBeInTheDocument();
    const bouton = screen.getByRole("button", { name: "Se déconnecter" });
    const formulaire = bouton.closest("form");
    expect(formulaire).toHaveAttribute("action", "/api/auth/logout");
    expect(formulaire).toHaveAttribute("method", "post");
  });
});

describe("messages de la page de connexion", () => {
  it.each([
    [{ erreur: "connexion" }, "connexion"],
    [{ erreur: "indisponible" }, "indisponible"],
    [{ expiree: "1" }, "expiree"],
    [{ deconnecte: "1" }, "deconnecte"],
    [{}, null],
  ])("%o donne le motif %s", (parametres, motif) => {
    expect(motifDepuisParametres(parametres)).toBe(motif);
  });

  it("annonce un échec de connexion comme une erreur, avec quoi faire", () => {
    enFrancais(<MessageConnexion motif="connexion" />);
    const alerte = screen.getByRole("alert");
    expect(alerte).toHaveTextContent("La connexion n'a pas abouti.");
    expect(alerte).toHaveTextContent("Réessayez.");
  });

  it("confirme poliment la déconnexion", () => {
    enFrancais(<MessageConnexion motif="deconnecte" />);
    expect(screen.getByRole("status")).toHaveTextContent("Vous êtes déconnecté.");
  });
});
