import { type APIRequestContext, type Browser, expect, type Page, test } from "@playwright/test";

/**
 * Catalogue administrable (US-02.2, US-02.4) de bout en bout, avec les personas fictives du seed :
 * l'administrateur propose une modification de la fiche CCC (délai) ; rien ne change avant
 * l'approbation ; le Ministre la voit sur son tableau de bord, l'approuve ; le catalogue public est
 * à jour. La fiche est ensuite remise comme avant, par le même circuit.
 */

const MOT_DE_PASSE = process.env.DEMO_PASSWORD ?? "";
const API = process.env.API_URL ?? "http://localhost:8080";
const COMPTES = { admin: "o.bah@e-mine.test", ministre: "i.soumah@e-mine.test" } as const;

async function connecter(browser: Browser, email: string): Promise<Page> {
  expect(MOT_DE_PASSE, "DEMO_PASSWORD doit être défini dans .env").not.toBe("");
  const page = await (await browser.newContext()).newPage();
  await page.goto("/");
  await page.getByRole("link", { name: "Se connecter" }).click();
  await page.locator("#username").fill(email);
  await page.locator("#password").fill(MOT_DE_PASSE);
  await page.locator("#kc-login").click();
  await expect(page).toHaveURL(/\/tableau-de-bord/);
  return page;
}

/** L'administrateur propose un nouveau délai pour la copie certifiée conforme (CCC). */
async function proposerDelai(admin: Page, jours: number, motif: string) {
  await admin.goto("/catalogue");
  await admin.getByRole("link", { name: /CCC · Copie certifiée conforme/ }).click();
  await admin.getByRole("link", { name: "Proposer une modification" }).click();
  await admin.getByLabel(/Délai cible/).fill(String(jours));
  await admin.getByLabel(/Objet de la modification/).fill(motif);
  await admin.getByRole("button", { name: "Soumettre au Ministre" }).click();
  await expect(admin.getByText(/est envoyée au Ministre pour validation/)).toBeVisible();
  await expect(admin.getByText("En attente de validation").first()).toBeVisible();
}

/** Le Ministre valide depuis son tableau de bord. */
async function approuver(ministre: Page) {
  await ministre.goto("/tableau-de-bord");
  await expect(ministre.getByText("1 modification du catalogue à valider")).toBeVisible();
  await ministre.getByRole("link", { name: "Voir les modifications" }).click();
  await ministre.getByRole("link", { name: /CCC · Copie certifiée conforme/ }).click();
  await expect(ministre.getByText("À valider").first()).toBeVisible();
  await ministre.getByRole("button", { name: "Valider" }).click();
  await ministre.getByRole("button", { name: "Confirmer la validation" }).click();
  await expect(ministre.getByText(/La modification est validée et publiée/)).toBeVisible();
}

async function delaiPublic(request: APIRequestContext) {
  const r = await request.get(`${API}/api/v1/types-documents/CCC`);
  expect(r.ok()).toBeTruthy();
  return ((await r.json()) as { delaiJours: number }).delaiJours;
}

test("catalogue : l'administrateur propose, le Ministre approuve, le catalogue public change", async ({
  browser,
  request,
}) => {
  const initial = await delaiPublic(request);
  const nouveau = initial + 1;
  const admin = await connecter(browser, COMPTES.admin);
  const ministre = await connecter(browser, COMPTES.ministre);

  await proposerDelai(admin, nouveau, "Démonstration E2E : délai allongé d'un jour");
  expect(await delaiPublic(request)).toBe(initial);

  await approuver(ministre);
  expect(await delaiPublic(request)).toBe(nouveau);
  await expect(ministre.getByText(`Fiche publiée`).first()).toBeVisible();

  // Remise en état, par le même circuit.
  await proposerDelai(admin, initial, "Démonstration E2E : retour au délai initial");
  await approuver(ministre);
  expect(await delaiPublic(request)).toBe(initial);
});
