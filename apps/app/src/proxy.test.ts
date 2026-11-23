// @vitest-environment node
import { sealData, unsealData } from "iron-session";
import { NextRequest } from "next/server";
import { proxy } from "./proxy";
import type { SessionEmine } from "./serveur/session";

const { lireMoi } = vi.hoisted(() => ({ lireMoi: vi.fn() }));
vi.mock("@emine/api-client", () => ({ lireMoi }));

vi.mock("./serveur/oidc", () => ({
  renouveler: vi.fn(async () => ({
    jetonAcces: "jeton-neuf",
    jetonRafraichissement: "rafraichissement-neuf",
    expireA: Math.floor(Date.now() / 1000) + 300,
  })),
}));

const SECRET = "s".repeat(32);

async function requeteAvecSession(session: SessionEmine, chemin = "/demandes/41/modifier") {
  const cookie = await sealData(session, { password: SECRET });
  return new NextRequest(`http://localhost:3001${chemin}`, {
    method: "POST",
    headers: { cookie: `emine_session=${cookie}; autre=1` },
  });
}

/** Valeur du cookie de session transmise à la suite de la requête (page, action serveur). */
function cookieTransmis(reponse: Response): string | undefined {
  const cookies = reponse.headers.get("x-middleware-request-cookie") ?? "";
  return cookies
    .split(/;\s*/)
    .find((c) => c.startsWith("emine_session="))
    ?.slice("emine_session=".length);
}

describe("proxy : renouvellement du jeton (correctif A2)", () => {
  beforeEach(() => {
    vi.stubEnv("OIDC_ISSUER", "http://localhost:8081/realms/emine");
    vi.stubEnv("OIDC_CLIENT_ID_APP", "emine-app");
    vi.stubEnv("OIDC_CLIENT_SECRET_APP", "secret-de-test");
    vi.stubEnv("SESSION_SECRET", SECRET);
  });
  afterEach(() => vi.unstubAllEnvs());

  it("transmet le jeton renouvelé à la requête en cours, pas seulement au navigateur", async () => {
    const reponse = await proxy(
      await requeteAvecSession({
        jetonAcces: "jeton-expire",
        jetonRafraichissement: "rafraichissement",
        expireA: Math.floor(Date.now() / 1000) - 10,
      }),
    );
    const transmis = cookieTransmis(reponse);
    expect(transmis).toBeDefined();
    const session = await unsealData<SessionEmine>(decodeURIComponent(transmis!), {
      password: SECRET,
    });
    expect(session.jetonAcces).toBe("jeton-neuf");
    expect(reponse.headers.get("x-middleware-request-cookie")).toContain("autre=1");
    expect(reponse.headers.getSetCookie().some((c) => c.startsWith("emine_session="))).toBe(true);
  });

  it("laisse passer la requête telle quelle quand le jeton est encore valide", async () => {
    const reponse = await proxy(
      await requeteAvecSession({
        jetonAcces: "jeton-valide",
        jetonRafraichissement: "rafraichissement",
        expireA: Math.floor(Date.now() / 1000) + 200,
      }),
    );
    expect(cookieTransmis(reponse)).toBeUndefined();
    expect(reponse.headers.get("x-middleware-next")).toBe("1");
  });

  it("répond 401 JSON à un relais du BFF sans session", async () => {
    const reponse = await proxy(new NextRequest("http://localhost:3001/api/demandes/1/pieces"));
    expect(reponse.status).toBe(401);
  });
});

describe("proxy : écrans réservés à d'autres profils", () => {
  beforeEach(() => {
    vi.stubEnv("OIDC_ISSUER", "http://localhost:8081/realms/emine");
    vi.stubEnv("OIDC_CLIENT_ID_APP", "emine-app");
    vi.stubEnv("OIDC_CLIENT_SECRET_APP", "secret-de-test");
    vi.stubEnv("SESSION_SECRET", SECRET);
    lireMoi.mockReset();
  });
  afterEach(() => vi.unstubAllEnvs());

  const valide = {
    jetonAcces: "jeton-valide",
    jetonRafraichissement: "rafraichissement",
    expireA: Math.floor(Date.now() / 1000) + 200,
  };
  const profil = (permissions: string[], interne = true) =>
    lireMoi.mockResolvedValue({ data: { permissions, interne } });

  it("ouvre workflows et paramètres à l'administrateur et au Ministre", async () => {
    for (const droits of [["PARAMETRER"], ["SIGNER", "VALIDER_CATALOGUE"]]) {
      profil(droits);
      for (const chemin of ["/workflows", "/parametres", "/workflows/EXPORT/modifier"]) {
        const reponse = await proxy(await requeteAvecSession(valide, chemin));
        expect(reponse.headers.get("x-middleware-next")).toBe("1");
        expect(reponse.headers.get("x-middleware-rewrite")).toBeNull();
      }
    }
  });

  it("répond comme une adresse inconnue (404, page introuvable) à un autre profil", async () => {
    profil(["DEPOSER", "PAYER"], false);
    const reponse = await proxy(await requeteAvecSession(valide, "/parametres"));
    expect(reponse.status).toBe(404);
    expect(reponse.headers.get("x-middleware-rewrite")).toContain("/introuvable");
  });

  it("ne consulte pas l'API pour un écran ouvert à tous", async () => {
    await proxy(await requeteAvecSession(valide, "/tableau-de-bord"));
    expect(lireMoi).not.toHaveBeenCalled();
  });

  it("laisse l'écran décider si l'API est injoignable", async () => {
    lireMoi.mockRejectedValue(new Error("ECONNREFUSED"));
    const reponse = await proxy(await requeteAvecSession(valide, "/workflows"));
    expect(reponse.headers.get("x-middleware-next")).toBe("1");
  });
});
