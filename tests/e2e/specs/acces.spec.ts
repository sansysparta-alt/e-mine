import { type Browser, expect, type Page, test } from "@playwright/test";

/**
 * Accès aux écrans selon le profil (règle unique : la configuration de la navigation) :
 * l'administrateur et le Ministre ouvrent le catalogue, les workflows et les paramètres ; un écran
 * réservé à un autre profil répond exactement comme une adresse inconnue (404, page « introuvable »
 * dans le gabarit de l'espace connecté), sans rien révéler de son existence.
 */

const MOT_DE_PASSE = process.env.DEMO_PASSWORD ?? "";
const INTROUVABLE = "Cette page est introuvable";

async function connecter(browser: Browser, email: string): Promise<Page> {
  expect(MOT_DE_PASSE, "DEMO_PASSWORD doit être défini dans .env").not.toBe("");
  const page = await (await browser.newContext()).newPage();
  await page.goto("/");
  // Seule porte d'entrée : « / » mène directement à la page de connexion Keycloak.
  await page.locator("#username").fill(email);
  await page.locator("#password").fill(MOT_DE_PASSE);
  await page.locator("#kc-login").click();
  await expect(page).toHaveURL(/\/tableau-de-bord/);
  return page;
}

async function ouvrir(page: Page, chemin: string) {
  const reponse = await page.goto((process.env.CIBLE ?? "") + chemin);
  return reponse?.status();
}

test("l'administrateur et le Ministre ouvrent workflows et paramètres", async ({ browser }) => {
  for (const email of ["o.bah@e-mine.test", "i.soumah@e-mine.test"]) {
    const page = await connecter(browser, email);
    expect(await ouvrir(page, "/workflows")).toBe(200);
    await expect(page.getByRole("heading", { level: 1, name: "Workflows" })).toBeVisible();
    expect(await ouvrir(page, "/workflows/EXPORT")).toBe(200);
    await expect(
      page.getByRole("heading", { level: 1, name: "Export et expertise" }),
    ).toBeVisible();
    expect(await ouvrir(page, "/parametres")).toBe(200);
    await expect(
      page.getByRole("heading", { level: 1, name: "Règles et notifications" }),
    ).toBeVisible();
  }
});

test("un écran réservé répond comme une adresse inconnue, dans l'espace connecté", async ({
  browser,
}) => {
  const investisseur = await connecter(browser, "f.bangoura@horizon-invest.test");
  for (const chemin of ["/workflows", "/parametres", "/catalogue", "/journal-audit", "/inconnue"]) {
    expect(await ouvrir(investisseur, chemin), chemin).toBe(404);
    await expect(investisseur.getByText(INTROUVABLE)).toBeVisible();
    // Gabarit de l'espace connecté : le menu de l'usager reste disponible.
    await expect(investisseur.getByRole("link", { name: "Tableau de bord" }).first()).toBeVisible();
  }
  const ministre = await connecter(browser, "i.soumah@e-mine.test");
  expect(await ouvrir(ministre, "/journal-audit")).toBe(404);
  await expect(ministre.getByText(INTROUVABLE)).toBeVisible();
});
