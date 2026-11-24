import { type Browser, expect, type Page, test } from "@playwright/test";

/**
 * Gabarit commun de l'espace connecté : action principale en tête du menu, cloche (seul accès aux
 * notifications), recherche globale Ctrl+K limitée aux écrans du profil, fil d'Ariane, aide et
 * pied de page.
 */

const MOT_DE_PASSE = process.env.DEMO_PASSWORD ?? "";
const INVESTISSEUR = "f.bangoura@horizon-invest.test";
const ADMIN = "o.bah@e-mine.test";

/** Ouvre la recherche globale au clavier (nouvel essai tant que la page n'est pas interactive). */
async function ouvrirRecherche(page: Page) {
  const champ = page.getByRole("combobox", { name: "Rechercher dans e-mine" });
  await expect(async () => {
    await page.keyboard.press("Control+k");
    await expect(champ).toBeVisible({ timeout: 1000 });
  }).toPass();
  return champ;
}

async function connecter(browser: Browser, email: string): Promise<Page> {
  expect(MOT_DE_PASSE, "DEMO_PASSWORD doit être défini dans .env").not.toBe("");
  const page = await (await browser.newContext()).newPage();
  await page.goto("/");
  await page.locator("#username").fill(email);
  await page.locator("#password").fill(MOT_DE_PASSE);
  await page.locator("#kc-login").click();
  await expect(page).toHaveURL(/\/tableau-de-bord/);
  return page;
}

test("le demandeur : « Nouvelle demande » en tête du menu, cloche, aide et pied de page", async ({
  browser,
}) => {
  const page = await connecter(browser, INVESTISSEUR);
  const menu = page.getByRole("navigation", { name: "Navigation principale" }).first();
  await expect(menu.getByRole("link", { name: "Notifications" })).toHaveCount(0);

  await page.getByRole("link", { name: "Nouvelle demande" }).first().click();
  await expect(page).toHaveURL(/\/demandes\/nouvelle$/);
  const fil = page.getByRole("navigation", { name: "Fil d’Ariane" });
  await expect(fil.getByRole("link", { name: "Mes demandes" })).toHaveAttribute(
    "href",
    "/demandes",
  );

  await page.getByRole("button", { name: /^Notifications/ }).click();
  await page.getByRole("link", { name: "Voir toutes les notifications" }).click();
  await expect(page).toHaveURL(/\/notifications$/);

  await page.getByRole("contentinfo").getByRole("link", { name: "Mentions légales" }).click();
  await expect(page.getByRole("heading", { level: 1, name: "Mentions légales" })).toBeVisible();
  await page.getByRole("link", { name: "Aide" }).first().click();
  await expect(page.getByRole("heading", { level: 1, name: "Aide" })).toBeVisible();
  await expect(page.getByText("Comment déposer une demande ?")).toBeVisible();
});

test("la recherche globale ne propose que les écrans du profil", async ({ browser }) => {
  const investisseur = await connecter(browser, INVESTISSEUR);
  const champ = await ouvrirRecherche(investisseur);
  await champ.fill("journal");
  await expect(investisseur.getByText("Aucun résultat pour « journal »")).toBeVisible();

  const admin = await connecter(browser, ADMIN);
  await (await ouvrirRecherche(admin)).fill("journal");
  await admin.getByRole("option", { name: /Journal d’audit/ }).click();
  await expect(admin).toHaveURL(/\/journal-audit$/);
  // Les agents ne reçoivent pas de notification : ni cloche, ni centre de notifications.
  await expect(admin.getByRole("button", { name: /^Notifications/ })).toHaveCount(0);
  expect((await admin.goto("/notifications"))?.status()).toBe(404);
});

test("l'investisseur retrouve ses paiements et reçus, puis ses documents délivrés", async ({
  browser,
}) => {
  const page = await connecter(browser, INVESTISSEUR);
  const menu = page.getByRole("navigation", { name: "Navigation principale" }).first();
  await menu.getByRole("link", { name: "Paiements & reçus" }).click();
  await expect(page.getByRole("heading", { level: 1, name: "Paiements & reçus" })).toBeVisible();
  await menu.getByRole("link", { name: "Documents délivrés" }).click();
  await expect(page.getByRole("heading", { level: 1, name: "Documents délivrés" })).toBeVisible();
  // Un agent n'a pas ces rubriques : l'adresse répond comme une page inconnue.
  const admin = await connecter(browser, ADMIN);
  expect((await admin.goto("/paiements"))?.status()).toBe(404);
});

test("l'administrateur technique : vue d'ensemble, disponibilité, utilisateurs et rôles", async ({
  browser,
}) => {
  const admin = await connecter(browser, ADMIN);
  await expect(admin.getByRole("region", { name: "Disponibilité des services" })).toBeVisible();
  await expect(admin.getByRole("heading", { name: "Comptes par profil" })).toBeVisible();
  await admin.getByRole("link", { name: /Utilisateurs actifs/ }).click();
  await expect(admin).toHaveURL(/\/utilisateurs\?statut=actif$/);
  await expect(
    admin.getByRole("heading", { level: 1, name: "Utilisateurs & rôles" }),
  ).toBeVisible();
  // L'administrateur vient de se connecter : sa connexion est datée dans la liste.
  const ligne = admin.getByRole("row", { name: /o\.bah@e-mine\.test/ });
  await expect(ligne).not.toContainText("Jamais connecté");
});
