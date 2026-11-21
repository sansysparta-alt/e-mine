import { act, fireEvent, render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import fr from "../../../messages/fr.json";
import { SurveillanceSession } from "./surveillance-session";

const delais = { maintien: 1_000, avertissement: 10_000, expiration: 14_000, verification: 500 };

function afficher(reponseMaintien = 200) {
  const appels = vi.fn(async () => new Response("{}", { status: reponseMaintien }));
  vi.stubGlobal("fetch", appels);
  const naviguer = vi.fn();
  render(
    <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
      <SurveillanceSession delais={delais} naviguer={naviguer} />
    </NextIntlClientProvider>,
  );
  return { appels, naviguer };
}

async function avancer(ms: number) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
}

const urls = (appels: ReturnType<typeof vi.fn>) =>
  appels.mock.calls.map((c) => String((c as unknown[])[0]));

describe("surveillance de la session (correctif A1)", () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it("renouvelle la session pendant l'activité, au plus une fois par période", async () => {
    const { appels } = afficher();
    await avancer(1_200);
    fireEvent.keyDown(window, { key: "a" });
    fireEvent.keyDown(window, { key: "b" });
    await avancer(10);
    expect(urls(appels).filter((u) => u === "/api/session")).toHaveLength(1);
  });

  it("ne renouvelle rien sans activité, puis avertit avant la déconnexion", async () => {
    const { appels } = afficher();
    await avancer(10_500);
    expect(appels).not.toHaveBeenCalled();
    expect(screen.getByRole("heading", { name: "Votre session va expirer" })).toBeInTheDocument();
  });

  it("« Rester connecté » ferme l'avertissement et renouvelle la session", async () => {
    const { appels } = afficher();
    await avancer(10_500);
    fireEvent.click(screen.getByRole("button", { name: "Rester connecté" }));
    await avancer(10);
    expect(screen.queryByRole("heading", { name: "Votre session va expirer" })).toBeNull();
    expect(urls(appels)).toContain("/api/session");
  });

  it("déconnecte après l'inactivité prolongée", async () => {
    const { appels, naviguer } = afficher();
    await avancer(14_600);
    expect(urls(appels)).toContain("/api/auth/logout");
    expect(naviguer).toHaveBeenCalledWith("/?expiree=1");
  });

  it("propose de se reconnecter en revenant sur la page si la session a déjà expiré", async () => {
    const { naviguer } = afficher(401);
    await avancer(1_200);
    fireEvent.pointerDown(window);
    await avancer(10);
    fireEvent.click(screen.getByRole("button", { name: "Se reconnecter" }));
    expect(naviguer).toHaveBeenCalledWith(expect.stringMatching(/^\/api\/auth\/login\?retour=/));
  });
});
