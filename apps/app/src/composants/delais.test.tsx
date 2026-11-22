import type { IndicateursDto } from "@emine/api-client";
import { render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import fr from "../../messages/fr.json";
import { DateAttente } from "./demande/date-attente";
import { VueTableauDeBord } from "./vue-tableau-de-bord";

function enFrancais(enfant: ReactNode) {
  return render(
    <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
      {enfant}
    </NextIntlClientProvider>,
  );
}

const indicateurs: IndicateursDto = {
  periode: "mois",
  debut: "2026-11-01T00:00:00Z",
  fin: "2026-11-22T12:00:00Z",
  enRetard: 2,
  procheDepassement: 3,
  complementsAttendus: 1,
  fraisAttendus: 1,
  bloques: 0,
  traitesAujourdhui: 4,
  delivrees: 5,
  delaiMoyenJours: 20,
  encaissementsGnf: 0,
  parDocument: [],
};

const base = {
  deposeLe: "2026-10-01T10:00:00Z",
  creeLe: "2026-10-01T09:00:00Z",
  etapeDepuis: "2026-11-20T10:00:00Z",
  avecAttente: true,
  maintenant: new Date("2026-11-22T12:00:00Z"),
};

describe("délais par dossier (US-05.6)", () => {
  it("dans les listes, la mention dit l'échéance, la suspension ou le retard (icône + texte)", () => {
    const { rerender } = enFrancais(
      <DateAttente
        {...base}
        delai={{ procheDepassement: true, joursRestants: 3, suspendu: false }}
      />,
    );
    expect(screen.getByText("Échéance dans 3 jours")).toBeInTheDocument();
    rerender(
      <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
        <DateAttente
          {...base}
          delai={{ procheDepassement: true, joursRestants: 0, suspendu: false }}
        />
      </NextIntlClientProvider>,
    );
    expect(screen.getByText("Échéance aujourd’hui")).toBeInTheDocument();
    rerender(
      <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
        <DateAttente
          {...base}
          delai={{ procheDepassement: false, joursRestants: 20, suspendu: true }}
        />
      </NextIntlClientProvider>,
    );
    expect(screen.getByText("Délai suspendu (complément)")).toBeInTheDocument();
    rerender(
      <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
        <DateAttente
          {...base}
          enRetard
          delai={{ procheDepassement: false, joursRestants: -2, suspendu: false }}
        />
      </NextIntlClientProvider>,
    );
    expect(screen.queryByText(/Échéance/)).not.toBeInTheDocument();
  });

  it("sans délai fourni (demandeur), aucune mention de délai", () => {
    enFrancais(<DateAttente {...base} />);
    expect(screen.queryByText(/Échéance|suspendu/)).not.toBeInTheDocument();
  });

  it("guichet : une grille pleine de 6 cartes, dont « Proches du dépassement » vers sa liste", () => {
    enFrancais(
      <VueTableauDeBord
        prenom="Aïcha"
        nom="Camara"
        profilLibelle="Agent Guichet Unique"
        profil="guichet"
        indicateurs={indicateurs}
        groupes={[{ cle: "a-examiner", nombre: 7 }]}
      />,
    );
    const proches = screen.getByRole("link", { name: /Proches du dépassement/ });
    expect(proches).toHaveAttribute("href", "/demandes?procheDepassement=1");
    expect(proches).toHaveTextContent("3");
    expect(proches).toHaveTextContent("À ce jour");
    expect(screen.getByRole("link", { name: /À examiner/ })).toHaveAttribute(
      "href",
      "/a-traiter?groupe=a-examiner",
    );
    expect(screen.getAllByRole("link", { name: /À ce jour|Aujourd/ })).toHaveLength(6);
  });

  it("instruction technique : ses avis, ses compléments, ses délais ; rien sur les frais", () => {
    enFrancais(
      <VueTableauDeBord
        prenom="Nènè"
        nom="Sow"
        profilLibelle="Agent DNM"
        profil="instruction"
        indicateurs={indicateurs}
        groupes={[
          { cle: "avis-a-rendre", nombre: 4 },
          { cle: "sans-reponse", nombre: 1 },
        ]}
      />,
    );
    const avis = screen.getByRole("link", { name: /Avis à rendre/ });
    expect(avis).toHaveAttribute("href", "/a-traiter?groupe=avis-a-rendre");
    expect(avis).toHaveTextContent("4");
    expect(screen.getByRole("link", { name: /Compléments sans réponse/ })).toHaveAttribute(
      "href",
      "/a-traiter?groupe=sans-reponse",
    );
    expect(screen.getByRole("link", { name: /Proches du dépassement/ })).toBeInTheDocument();
    expect(screen.queryByText("Frais attendus")).not.toBeInTheDocument();
  });

  it("décideurs : les dossiers proches du dépassement sont signalés avec leur liste", () => {
    enFrancais(
      <VueTableauDeBord
        prenom="Ibrahima"
        nom="Soumah"
        profilLibelle="Ministre"
        profil="decideur"
        indicateurs={indicateurs}
      />,
    );
    expect(screen.getByText("3 dossiers proches du dépassement de leur délai")).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Voir les dossiers proches du dépassement" }),
    ).toHaveAttribute("href", "/demandes?procheDepassement=1");
  });
});
