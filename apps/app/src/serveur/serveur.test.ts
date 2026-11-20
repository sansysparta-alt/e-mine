import { cheminDeRetourSur, PAGE_APRES_CONNEXION } from "./retour";
import { aRenouveler, optionsFluxConnexion, optionsSession } from "./session";

describe("destination après connexion (pas de redirection ouverte)", () => {
  it.each([
    ["/tableau-de-bord", "/tableau-de-bord"],
    ["/demandes/12?onglet=pieces", "/demandes/12?onglet=pieces"],
  ])("accepte le chemin interne %s", (entree, attendu) => {
    expect(cheminDeRetourSur(entree)).toBe(attendu);
  });

  it.each([
    null,
    "",
    "https://site-pirate.test",
    "//site-pirate.test",
    "/\\site-pirate.test",
    "javascript:alert(1)",
    "/\u0000x",
    "/api/auth/login",
  ])("refuse %s et renvoie au tableau de bord", (entree) => {
    expect(cheminDeRetourSur(entree)).toBe(PAGE_APRES_CONNEXION);
  });
});

describe("cookies de session", () => {
  beforeEach(() => {
    vi.stubEnv("OIDC_ISSUER", "http://localhost:8081/realms/emine");
    vi.stubEnv("OIDC_CLIENT_ID_APP", "emine-app");
    vi.stubEnv("OIDC_CLIENT_SECRET_APP", "secret-de-test");
    vi.stubEnv("SESSION_SECRET", "x".repeat(32));
  });
  afterEach(() => vi.unstubAllEnvs());

  it("sont httpOnly et SameSite=Lax, donc illisibles par le JavaScript du navigateur", () => {
    for (const options of [optionsSession(), optionsFluxConnexion()]) {
      expect(options.cookieOptions).toMatchObject({ httpOnly: true, sameSite: "lax", path: "/" });
    }
  });

  it("sont Secure en production", () => {
    vi.stubEnv("NODE_ENV", "production");
    expect(optionsSession().cookieOptions?.secure).toBe(true);
  });

  it("refusent un secret de session trop court", () => {
    vi.stubEnv("SESSION_SECRET", "court");
    expect(() => optionsSession()).toThrow(/32 caractères/);
  });

  it("signalent clairement une variable manquante", () => {
    vi.stubEnv("OIDC_ISSUER", "");
    expect(() => optionsSession()).toThrow(/OIDC_ISSUER/);
  });
});

describe("renouvellement du jeton d'accès", () => {
  it("est nécessaire sans date d'expiration ou à moins de 30 s de l'expiration", () => {
    const maintenant = 1_000_000;
    expect(aRenouveler({}, maintenant)).toBe(true);
    expect(aRenouveler({ expireA: maintenant + 10 }, maintenant)).toBe(true);
    expect(aRenouveler({ expireA: maintenant + 120 }, maintenant)).toBe(false);
  });
});
