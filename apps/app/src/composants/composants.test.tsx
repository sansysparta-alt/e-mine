import { render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import fr from "../../messages/fr.json";
import type { DemandeDto } from "@emine/api-client";
import { MessageConnexion, motifDepuisParametres } from "./message-connexion";
import { actionAttendue, VueTableauDeBord } from "./vue-tableau-de-bord";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }) }));

function enFrancais(enfant: ReactNode) {
  return render(
    <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
      {enfant}
    </NextIntlClientProvider>,
  );
}

function demande(partiel: Partial<DemandeDto>): DemandeDto {
  return {
    id: 1,
    numero: null,
    statut: "brouillon",
    typeDocumentCode: "PEX",
    demandeur: { prenom: "Fodé", nom: "Bangoura", email: "f@x.test", organisation: null },
    prefectureId: null,
    prefectureNom: null,
    substanceCode: null,
    substanceLibelle: null,
    superficieHa: null,
    creeLe: "2026-11-20T09:00:00Z",
    deposeLe: null,
    fraisPayes: false,
    motifRejet: null,
    etapeDepuis: "2026-11-15T10:00:00Z",
    aTraiterParMoi: false,
    ...partiel,
  };
}

const deposee = demande({
  id: 2,
  numero: "DEM-2026-000001",
  statut: "deposee",
  deposeLe: "2026-11-20T10:30:00Z",
});
const brouillon = demande({ id: 3, typeDocumentCode: "COR" });
const libelles = { PEX: "Permis d'exploitation", COR: "Carte d'orpailleur" };

describe("tableau de bord (gabarit du socle)", () => {
  it("salue l'investisseur, donne ses chiffres clés et ses demandes récentes", () => {
    enFrancais(
      <VueTableauDeBord
        prenom="Fodé"
        nom="Bangoura"
        profilLibelle="Investisseur Extérieur"
        interne={false}
        demandes={[deposee, brouillon]}
        libelles={libelles}
      />,
    );
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("Bonjour, Fodé Bangoura");
    expect(screen.getByText("Profil : Investisseur Extérieur")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Mes demandes\s*2/ })).toHaveAttribute(
      "href",
      "/demandes",
    );
    expect(screen.getByRole("link", { name: /Brouillons\s*1/ })).toHaveAttribute(
      "href",
      "/demandes?groupe=brouillons",
    );
    const lien = screen.getByRole("link", { name: "DEM-2026-000001" });
    expect(lien).toHaveAttribute("href", "/demandes/2");
    expect(screen.getByRole("link", { name: "Brouillon" })).toHaveAttribute(
      "href",
      "/demandes/3/modifier",
    );
    expect(screen.getByText("Frais à payer")).toBeInTheDocument();
    expect(screen.getByText("Déposée le 20/11/2026")).toBeInTheDocument();
    expect(screen.getAllByText(/En attente depuis \d+ jours?/).length).toBeGreaterThan(0);
  });

  it("affiche un état vide explicite sans demande", () => {
    enFrancais(
      <VueTableauDeBord
        prenom="Fodé"
        nom="Bangoura"
        profilLibelle="Investisseur"
        interne={false}
      />,
    );
    expect(screen.getByText("Vous n'avez encore aucune demande.")).toBeInTheDocument();
  });

  it("montre à un agent interne les dossiers à traiter", () => {
    enFrancais(
      <VueTableauDeBord
        prenom="Aïcha"
        nom="Camara"
        profilLibelle="Agent Guichet Unique"
        interne
        demandes={[deposee]}
        libelles={libelles}
      />,
    );
    expect(screen.getByRole("link", { name: /Déposées, à traiter\s*1/ })).toHaveAttribute(
      "href",
      "/demandes?groupe=a-traiter",
    );
    expect(
      screen.getByRole("heading", { level: 2, name: "Dernières demandes" }),
    ).toBeInTheDocument();
  });
});

describe("tableau de bord orienté action (correctifs J4)", () => {
  it("dit au demandeur ce qu'il doit payer, avec le montant et un bouton direct", () => {
    const decidee = demande({ id: 5, numero: "DEM-2026-000005", statut: "decision" });
    enFrancais(
      <VueTableauDeBord
        prenom="Fodé"
        nom="Bangoura"
        profilLibelle="Investisseur"
        interne={false}
        demandes={[deposee, decidee]}
        libelles={libelles}
        aFaire={[
          { demande: decidee, action: "DROITS", montantGnf: 15_000_000 },
          { demande: deposee, action: "FRAIS", montantGnf: 5_000_000 },
        ]}
      />,
    );
    expect(screen.getByRole("heading", { name: "À faire maintenant (2)" })).toBeInTheDocument();
    expect(screen.getByText(/droits à payer, 15\s000\s000\sGNF/)).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: /Payer les droits · DEM-2026-000005/ }),
    ).toHaveAttribute("href", "/demandes/5/paiement");
    expect(screen.getByRole("link", { name: /Payer les frais · DEM-2026-000001/ })).toHaveAttribute(
      "href",
      "/demandes/2/paiement",
    );
  });

  it("donne à l'agent ses files de travail selon ses droits", () => {
    enFrancais(
      <VueTableauDeBord
        prenom="Ibrahima"
        nom="Soumah"
        profilLibelle="Ministre"
        interne
        taches={[{ cle: "decisions", valeur: 3 }]}
      />,
    );
    expect(screen.getByRole("link", { name: /Décisions à prendre\s*3/ })).toHaveAttribute(
      "href",
      "/a-traiter?groupe=a-decider",
    );
    expect(screen.queryByText("Déposées, à traiter")).not.toBeInTheDocument();
  });

  it("déduit l'action attendue du statut et des frais", () => {
    expect(actionAttendue({ ...deposee, fraisPayes: false })).toBe("FRAIS");
    expect(actionAttendue({ ...deposee, fraisPayes: true })).toBeNull();
    expect(actionAttendue({ ...deposee, statut: "decision" })).toBe("DROITS");
    expect(actionAttendue({ ...deposee, statut: "recevable" })).toBeNull();
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
    expect(screen.getAllByRole("link", { name: "Nouvelle demande" })[0]).toHaveAttribute(
      "href",
      "/demandes/nouvelle",
    );
    unmount();
    enFrancais(<VueTableauDeBord prenom="Aïcha" nom="Camara" profilLibelle="Guichet" interne />);
    expect(screen.queryByRole("link", { name: "Nouvelle demande" })).not.toBeInTheDocument();
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
