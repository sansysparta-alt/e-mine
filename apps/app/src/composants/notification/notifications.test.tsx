import type { NotificationDto } from "@emine/api-client";
import { render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import en from "../../../messages/en.json";
import fr from "../../../messages/fr.json";
import { ClocheNotifications, pastille } from "./cloche-notifications";
import { adresseOuverture, ListeNotifications, resume } from "./liste-notifications";

vi.mock("next/navigation", () => ({ usePathname: () => "/demandes" }));

function enFrancais(enfant: ReactNode) {
  return render(
    <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
      {enfant}
    </NextIntlClientProvider>,
  );
}

const notification = (n: Partial<NotificationDto>): NotificationDto => ({
  id: 1,
  modele: "DEMANDE_DEPOSEE",
  titre: "Demande déposée · DEM-2026-000123",
  texte:
    "Votre demande DEM-2026-000123 est bien déposée.\n\nProchaine étape : le paiement des frais.",
  demandeId: 42,
  numeroDemande: "DEM-2026-000123",
  creeLe: "2026-11-22T10:30:00Z",
  lue: false,
  ...n,
});

describe("centre de notifications (US-07.2)", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("chaque ligne est un vrai lien qui ouvre le dossier et marque la notification lue", () => {
    enFrancais(
      <ListeNotifications
        notifications={[notification({}), notification({ id: 2, lue: true, demandeId: null })]}
      />,
    );
    const liens = screen.getAllByRole("link");
    expect(liens[0]).toHaveAttribute("href", "/notifications/1/ouvrir?demande=42");
    expect(liens[1]).toHaveAttribute("href", "/notifications/2/ouvrir");
    // Non lue : annoncée en texte (jamais la couleur seule), titre en gras.
    expect(liens[0]).toHaveAccessibleName("Non lue : Demande déposée · DEM-2026-000123");
    expect(liens[0]).toHaveClass("font-semibold");
    expect(liens[1]).toHaveAccessibleName("Demande déposée · DEM-2026-000123");
    // Date au format de l'application, heure de Conakry ; résumé = premier paragraphe.
    expect(screen.getAllByText(/22\/11\/2026/)).toHaveLength(2);
    expect(screen.queryByText(/Prochaine étape/)).toBeNull();
  });

  it("résume un message à son premier paragraphe", () => {
    expect(resume("Premier.\n\nSecond.")).toBe("Premier.");
    expect(adresseOuverture({ id: 7, demandeId: 9 })).toBe("/notifications/7/ouvrir?demande=9");
  });

  it("la cloche annonce le nombre de non lues et l'affiche en pastille (9+ au-delà de 9)", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response(JSON.stringify({ nombre: 12 }), { status: 200 })),
    );
    enFrancais(<ClocheNotifications initial={3} />);
    const cloche = screen.getByRole("link");
    expect(cloche).toHaveAttribute("href", "/notifications");
    expect(cloche).toHaveAccessibleName("Notifications, 3 non lues");
    await waitFor(() => expect(cloche).toHaveAccessibleName("Notifications, 12 non lues"));
    expect(screen.getByText("9+")).toBeInTheDocument();
    expect(pastille(1)).toBe("1");
  });

  it("sans non lue ni réseau, la cloche reste utilisable sans pastille", async () => {
    const appel = vi.fn(async () => {
      throw new Error("hors ligne");
    });
    vi.stubGlobal("fetch", appel);
    enFrancais(<ClocheNotifications />);
    await waitFor(() => expect(appel).toHaveBeenCalled());
    expect(screen.getByRole("link")).toHaveAccessibleName("Notifications");
    expect(screen.queryByText(/^\d/)).toBeNull();
  });

  it("les libellés existent en français et en anglais", () => {
    const cles = (m: Record<string, unknown>, prefixe = ""): string[] =>
      Object.entries(m).flatMap(([k, v]) =>
        typeof v === "object" && v !== null
          ? cles(v as Record<string, unknown>, `${prefixe}${k}.`)
          : [`${prefixe}${k}`],
      );
    expect(cles(en.notifications)).toEqual(cles(fr.notifications));
  });
});
