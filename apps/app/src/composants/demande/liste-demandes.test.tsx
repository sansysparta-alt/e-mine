import type { DemandeDto } from "@emine/api-client";
import { render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import fr from "../../../messages/fr.json";
import { VueTableauDeBord } from "../vue-tableau-de-bord";
import { lienDemande, ListeDemandes } from "./liste-demandes";

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

function enFrancais(enfant: ReactNode) {
  return render(
    <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
      {enfant}
    </NextIntlClientProvider>,
  );
}

describe("mes demandes (US-03.5)", () => {
  it("affiche numéro, document, statut et date ; un brouillon n'a pas encore de numéro", () => {
    enFrancais(<ListeDemandes demandes={[deposee, brouillon]} libelles={libelles} />);
    const lienDeposee = screen.getByRole("link", { name: /DEM-2026-000001/ });
    expect(lienDeposee).toHaveAttribute("href", "/demandes/2");
    expect(lienDeposee).toHaveTextContent("Permis d'exploitation");
    expect(lienDeposee).toHaveTextContent("Déposée");
    expect(lienDeposee).toHaveTextContent("déposée le 20/11/2026");
    const lienBrouillon = screen.getByRole("link", {
      name: /Brouillon \(numéro attribué au dépôt\)/,
    });
    expect(lienBrouillon).toHaveAttribute("href", "/demandes/3/modifier");
    expect(lienBrouillon).toHaveTextContent("Carte d'orpailleur");
  });

  it("mène un brouillon au formulaire et une demande déposée à sa fiche", () => {
    expect(lienDemande({ id: 9, statut: "brouillon" })).toBe("/demandes/9/modifier");
    expect(lienDemande({ id: 9, statut: "recevable" })).toBe("/demandes/9");
  });

  it("montre les demandes récentes sur le tableau de bord de l'usager", () => {
    enFrancais(
      <VueTableauDeBord
        prenom="Fodé"
        nom="Bangoura"
        profilLibelle="Investisseur"
        interne={false}
        demandes={[deposee, brouillon]}
        libelles={libelles}
      />,
    );
    expect(screen.getByRole("link", { name: /DEM-2026-000001/ })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Voir toutes mes demandes (2)" })).toHaveAttribute(
      "href",
      "/demandes",
    );
    expect(screen.queryByText("Vous n'avez encore aucune demande.")).not.toBeInTheDocument();
  });
});
