import type { TableauDto } from "@emine/api-client";
import { render, screen, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import en from "../../../messages/en.json";
import fr from "../../../messages/fr.json";
import { VueAdministration } from "./vue-administration";

const tableau: TableauDto = {
  comptes: { actifs: 18, suspendus: 1, invites: 0, connectes30Jours: 7 },
  profils: [
    {
      code: "ADMIN_TECH",
      libelle: "Administrateur technique",
      interne: true,
      utilisateurs: 1,
      droits: 3,
    },
    {
      code: "TUTELLE",
      libelle: "Organismes sous tutelle",
      interne: true,
      utilisateurs: 0,
      droits: 0,
    },
  ],
  services: [
    { cle: "base", disponible: true },
    { cle: "authentification", disponible: true },
    { cle: "stockage", disponible: false },
    { cle: "messagerie", disponible: true },
  ],
  verifieLe: "2026-11-24T10:00:00Z",
};

function afficher() {
  return render(
    <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
      <VueAdministration tableau={tableau} operations={[]} />
    </NextIntlClientProvider>,
  );
}

describe("vue d'ensemble de l'administrateur technique", () => {
  it("chaque carte ouvre la liste qui donne le même nombre", () => {
    afficher();
    expect(screen.getByRole("link", { name: /Utilisateurs actifs/ })).toHaveAttribute(
      "href",
      "/utilisateurs?statut=actif",
    );
    expect(screen.getByRole("link", { name: /Utilisateurs connectés/ })).toHaveAttribute(
      "href",
      "/utilisateurs?recents=1",
    );
    expect(screen.getByRole("link", { name: /Profils configurés.*1 sur 2/ })).toBeVisible();
    expect(screen.getByRole("link", { name: /Disponibilité.*3 sur 4/ })).toBeVisible();
  });

  it("dit quel service est indisponible, en clair et pas seulement en couleur", () => {
    afficher();
    const services = screen.getByRole("region", { name: "Disponibilité des services" });
    const stockage = within(services).getByText("Stockage des documents").closest("li")!;
    expect(stockage).toHaveTextContent("Indisponible");
    expect(within(services).getAllByText("Disponible")).toHaveLength(3);
  });

  it("signale un profil sans droit et ouvre ses comptes depuis la ligne", () => {
    afficher();
    const tutelle = screen.getByRole("row", { name: /Organismes sous tutelle/ });
    expect(within(tutelle).getByText("Aucun droit")).toBeVisible();
    expect(within(tutelle).getByRole("link")).toHaveAttribute(
      "href",
      "/utilisateurs?profil=TUTELLE",
    );
  });

  it("les libellés existent en français et en anglais", () => {
    const cles = (m: Record<string, unknown>, prefixe = ""): string[] =>
      Object.entries(m).flatMap(([k, v]) =>
        typeof v === "object" && v !== null
          ? cles(v as Record<string, unknown>, `${prefixe}${k}.`)
          : [`${prefixe}${k}`],
      );
    expect(cles(en.administration).sort()).toEqual(cles(fr.administration).sort());
  });
});
