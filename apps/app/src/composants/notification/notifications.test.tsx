import type { NotificationDto } from "@emine/api-client";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
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
  montantGnf: null,
  action: null,
  ...n,
});

const MAINTENANT = new Date("2026-11-24T09:00:00Z");

describe("centre de notifications (US-07.2)", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("chaque ligne est un vrai lien qui ouvre le dossier et marque la notification lue", () => {
    enFrancais(
      <ListeNotifications
        notifications={[notification({}), notification({ id: 2, lue: true, demandeId: null })]}
        maintenant={MAINTENANT}
      />,
    );
    const liens = screen.getAllByRole("link");
    expect(liens[0]).toHaveAttribute("href", "/notifications/1/ouvrir?demande=42");
    expect(liens[1]).toHaveAttribute("href", "/notifications/2/ouvrir");
    // Non lue : annoncée en texte (jamais la couleur seule), titre en gras.
    expect(liens[0]).toHaveAccessibleName("Non lue : Demande déposée · DEM-2026-000123");
    expect(liens[0]).toHaveClass("font-semibold");
    expect(liens[1]).toHaveAccessibleName("Demande déposée · DEM-2026-000123");
    // Groupées sous leur jour (date au format de l'application), heure de Conakry sur la ligne ;
    // résumé = premier paragraphe.
    expect(screen.getByRole("heading", { name: "22/11/2026" })).toBeVisible();
    expect(screen.getAllByText("10:30")).toHaveLength(2);
    expect(screen.queryByText(/Prochaine étape/)).toBeNull();
  });

  it("groupe les notifications par jour : Aujourd'hui, Hier, puis la date", () => {
    enFrancais(
      <ListeNotifications
        maintenant={MAINTENANT}
        notifications={[
          notification({ id: 3, creeLe: "2026-11-24T08:00:00Z" }),
          notification({ id: 4, creeLe: "2026-11-23T18:00:00Z" }),
          notification({ id: 5, creeLe: "2026-11-20T18:00:00Z" }),
        ]}
      />,
    );
    expect(screen.getAllByRole("heading").map((h) => h.textContent)).toEqual([
      "Aujourd’hui",
      "Hier",
      "20/11/2026",
    ]);
  });

  it("des droits encore à payer : le montant et le bouton « Payer les droits »", () => {
    enFrancais(
      <ListeNotifications
        maintenant={MAINTENANT}
        notifications={[
          notification({
            modele: "DECISION_DROITS",
            montantGnf: 15_000_000,
            action: "payer-droits",
          }),
          notification({ id: 6, modele: "DECISION_DROITS", montantGnf: 15_000_000 }),
        ]}
      />,
    );
    expect(screen.getByText(/Droits à payer : 15\s000\s000 GNF/)).toBeVisible();
    const payer = screen.getAllByRole("link", { name: /Payer les droits/ });
    expect(payer).toHaveLength(1);
    expect(payer[0]).toHaveAttribute("href", "/demandes/42/paiement");
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
    const cloche = screen.getByRole("button");
    expect(cloche).toHaveAttribute("aria-expanded", "false");
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
    expect(screen.getByRole("button")).toHaveAccessibleName("Notifications");
    expect(screen.queryByText(/^\d/)).toBeNull();
  });

  it("la cloche ouvre les dernières notifications, avec « Voir toutes » ; Échap la referme", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string) =>
        url === "/api/notifications"
          ? new Response(
              JSON.stringify({
                elements: [
                  notification({ id: 4, titre: "Frais à payer", lue: false, demandeId: 9 }),
                  notification({ id: 3, titre: "Demande déposée", lue: true }),
                ],
                nonLues: 1,
              }),
              { status: 200 },
            )
          : new Response(JSON.stringify({ nombre: 1 }), { status: 200 }),
      ),
    );
    enFrancais(<ClocheNotifications initial={1} />);
    const cloche = screen.getByRole("button");
    fireEvent.click(cloche);
    expect(cloche).toHaveAttribute("aria-expanded", "true");
    const panneau = await screen.findByRole("region", { name: "Notifications" });
    const nonLue = await screen.findByRole("link", { name: /Non lue : Frais à payer/ });
    expect(nonLue).toHaveAttribute("href", "/notifications/4/ouvrir?demande=9");
    expect(panneau).toContainElement(
      screen.getByRole("link", { name: "Voir toutes les notifications" }),
    );
    fireEvent.keyDown(document, { key: "Escape" });
    expect(cloche).toHaveAttribute("aria-expanded", "false");
    expect(cloche).toHaveFocus();
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
