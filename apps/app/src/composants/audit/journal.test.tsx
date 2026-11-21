import type { EntreeJournalDto } from "@emine/api-client";
import { render, screen, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import fr from "../../../messages/fr.json";
import { formaterValeur, lireParametresJournal } from "./journal";
import { TableauJournal } from "./tableau-journal";

function traduire(table: Record<string, string>) {
  const t = (cle: string) => table[cle] ?? cle;
  t.has = (cle: string) => cle in table;
  return t;
}
const traductions = {
  statuts: traduire(fr.statuts),
  statutsPaiement: traduire(fr.statutsPaiement),
  oui: "Oui",
  non: "Non",
};

describe("journal d'audit : valeurs lisibles (US-11.7)", () => {
  it("traduit statuts, booléens, montants et horodatages ; « — » si absent", () => {
    expect(formaterValeur("demande", "statut", "recevable", traductions)).toBe("Recevable");
    expect(formaterValeur("paiement", "statut", "paye", traductions)).toBe(fr.statutsPaiement.paye);
    expect(formaterValeur("utilisateur", "double_auth_actif", "false", traductions)).toBe("Non");
    expect(formaterValeur("paiement", "montant_gnf", "5000000", traductions)).toMatch(
      /^5\s000\s000\sGNF$/,
    );
    expect(
      formaterValeur("demande", "date_depot", "2026-11-21T10:00:00.123+00:00", traductions),
    ).toBe("21/11/2026 à 10:00");
    expect(formaterValeur("demande", "motif_rejet", null, traductions)).toBe("—");
    expect(formaterValeur("demande", "numero", "DEM-2026-000001", traductions)).toBe(
      "DEM-2026-000001",
    );
  });

  it("valide les paramètres et repère une période inversée sans appeler l'API", () => {
    expect(lireParametresJournal({ categorie: "demandes" }).categorie).toBe("demandes");
    expect(lireParametresJournal({ categorie: "inconnue" }).categorie).toBeUndefined();
    expect(lireParametresJournal({ du: "21/11/2026" }).du).toBeUndefined();
    expect(lireParametresJournal({ du: "2026-11-21", au: "2026-11-01" }).periodeInversee).toBe(
      true,
    );
    expect(lireParametresJournal({ du: "2026-11-01", au: "2026-11-21" }).periodeInversee).toBe(
      false,
    );
  });
});

describe("tableau du journal", () => {
  const entree = (partiel: Partial<EntreeJournalDto>): EntreeJournalDto => ({
    id: 7,
    date: "2026-11-21T10:00:00Z",
    auteur: "Aïcha Camara",
    auteurProfil: "Agent Guichet Unique (CPDM)",
    table: "demande",
    categorie: "demandes",
    operation: "U",
    objetId: "12",
    objetLibelle: "DEM-2026-000012",
    demandeId: 12,
    changements: [{ champ: "statut", avant: "deposee", apres: "recevable" }],
    adresseIp: "10.0.0.1",
    ...partiel,
  });

  function afficher(entrees: EntreeJournalDto[]) {
    render(
      <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
        <TableauJournal entrees={entrees} legende="Journal d’audit" />
      </NextIntlClientProvider>,
    );
  }

  it("dit qui a fait quoi, sur quel objet, et ce qui a changé en clair", () => {
    afficher([
      entree({}),
      entree({
        id: 8,
        auteur: null,
        auteurProfil: null,
        table: "paiement",
        operation: "U",
        objetLibelle: "PAY-2026-000003",
        changements: [
          { champ: "statut", avant: "en_attente", apres: "paye" },
          { champ: "recu_numero", avant: null, apres: "REC-2026-000003" },
          { champ: "montant_recu_gnf", avant: null, apres: "5000000" },
        ],
      }),
    ]);
    const lignes = screen.getAllByRole("row").slice(1);
    const premiere = within(lignes[0]!);
    expect(premiere.getByText("Aïcha Camara")).toBeVisible();
    expect(premiere.getByText("Modification")).toBeVisible();
    expect(premiere.getByText(/Déposée/)).toBeVisible();
    expect(premiere.getByText(/Recevable/)).toBeVisible();
    expect(premiere.getByRole("link", { name: "DEM-2026-000012" })).toHaveAttribute(
      "href",
      "/demandes/12",
    );
    const seconde = within(lignes[1]!);
    expect(seconde.getByText("Système")).toBeVisible();
    expect(seconde.getByText("et 1 autre champ")).toBeVisible();
  });

  it("ouvre l'entrée du journal par un vrai lien sur toute la ligne", () => {
    afficher([entree({})]);
    const liens = screen.getAllByRole("link");
    expect(liens.map((l) => l.getAttribute("href"))).toContain("/journal-audit/7");
  });
});
