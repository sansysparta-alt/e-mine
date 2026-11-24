import { expect, type Page, test } from "@playwright/test";

/**
 * Porte d'entrée de l'application : une seule page de connexion (Keycloak, thème e-mine) à
 * l'identité de l'État ; erreurs qui ne disent pas ce qui est faux ; menu sous l'avatar ; langue
 * FR / EN (cahier des charges : « Bilingue FR / EN », « Connexion FR / EN ») ; message après la
 * déconnexion ; bandeau en haut sur mobile.
 */

const MOT_DE_PASSE = process.env.DEMO_PASSWORD ?? "";
const ERREUR = "Identifiant ou mot de passe incorrect. Vérifiez votre saisie et réessayez.";

async function seConnecter(
  page: Page,
  identifiant: string,
  motDePasse: string,
  bouton = "Accéder à mon espace",
) {
  await page.locator("#username").fill(identifiant);
  await page.locator("#password").fill(motDePasse);
  await page.getByRole("button", { name: bouton }).click();
}

test("sans session, « / » mène directement à la page de connexion officielle", async ({ page }) => {
  await page.goto("/");
  await expect(page).toHaveURL(/\/realms\/emine\/protocol\/openid-connect\/auth/);
  await expect(page).toHaveTitle("Se connecter · e-mine");
  await expect(page.getByRole("heading", { level: 1, name: "Se connecter" })).toBeVisible();
  await expect(page.getByText("République de Guinée")).toBeVisible();
  await expect(page.getByText("Ministère des Mines et de la Géologie")).toBeVisible();
  await expect(page.getByText(/toutes les données sont fictives/)).toBeVisible();
  await expect(page.getByLabel("Identifiant ou e-mail")).toBeVisible();
  await expect(page.getByRole("link", { name: "Mot de passe oublié ?" })).toBeVisible();
  await expect(page.locator("html")).toHaveAttribute("lang", "fr");
  // L'œil du mot de passe a la même hauteur que le champ.
  const champ = await page.locator("#password").boundingBox();
  const oeil = await page.locator("#password + button").boundingBox();
  expect(Math.abs((champ?.height ?? 0) - (oeil?.height ?? 0))).toBeLessThanOrEqual(1);
  expect(Math.abs((champ?.y ?? 0) - (oeil?.y ?? 0))).toBeLessThanOrEqual(1);
});

test("l'erreur de connexion ne dit pas si c'est l'identifiant ou le mot de passe", async ({
  page,
}) => {
  await page.goto("/");
  await seConnecter(page, "inconnu@e-mine.test", "Mauvais-mot-2-passe!");
  await expect(page.getByText(ERREUR)).toBeVisible();
  await seConnecter(page, "o.bah@e-mine.test", "Mauvais-mot-2-passe!");
  await expect(page.getByText(ERREUR)).toBeVisible();
});

test("connexion, menu sous l'avatar, anglais, puis déconnexion avec message", async ({ page }) => {
  expect(MOT_DE_PASSE, "DEMO_PASSWORD doit être défini dans .env").not.toBe("");
  await page.goto("/");
  await seConnecter(page, "o.bah@e-mine.test", MOT_DE_PASSE);
  await expect(page).toHaveURL(/\/tableau-de-bord/);
  await expect(page).toHaveTitle(/· e-mine$/);
  await expect(page.getByRole("button", { name: "Se déconnecter" })).toHaveCount(0);

  const menu = page.getByRole("button", { name: /Menu de l’utilisateur/ });
  await menu.click();
  await expect(page.getByRole("menuitem", { name: "Mon profil" })).toBeFocused();
  await page.keyboard.press("Escape");
  await expect(menu).toBeFocused();

  await menu.click();
  await page.getByRole("menuitemradio", { name: "English" }).click();
  await expect(page.locator("html")).toHaveAttribute("lang", "en");
  await page.getByRole("button", { name: /User menu/ }).click();
  await page.getByRole("menuitem", { name: "Sign out" }).click();
  await expect(page.getByRole("heading", { level: 1, name: "Sign in" })).toBeVisible();
  await expect(page.getByText("You have been signed out.")).toBeVisible();
});

test("sur mobile, l'identité de l'État devient un bandeau au-dessus du formulaire", async ({
  browser,
}) => {
  const page = await (
    await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true })
  ).newPage();
  await page.goto("/api/auth/login?motif=deconnecte");
  await expect(page.getByText("Vous avez été déconnecté.")).toBeVisible();
  const bandeau = await page.locator(".emine-identite").boundingBox();
  const formulaire = await page.locator(".emine-carte").boundingBox();
  expect((bandeau?.y ?? 0) + (bandeau?.height ?? 0)).toBeLessThanOrEqual(formulaire?.y ?? 0);
  const largeur = await page.evaluate(() => document.documentElement.scrollWidth);
  expect(largeur).toBeLessThanOrEqual(390);
});

test("la langue choisie sur la page de connexion est reprise par l'application", async ({
  page,
}) => {
  expect(MOT_DE_PASSE, "DEMO_PASSWORD doit être défini dans .env").not.toBe("");
  await page.goto("/");
  // Sélecteur discret en bas : chaque langue dans sa propre langue, la langue active indiquée.
  const langues = page.getByRole("navigation", { name: "Langue" });
  await expect(langues.locator('[aria-current="true"]')).toHaveText("Français");
  await langues.getByRole("link", { name: "English" }).click();
  await expect(page.getByRole("heading", { level: 1, name: "Sign in" })).toBeVisible();
  await seConnecter(page, "f.bangoura@horizon-invest.test", MOT_DE_PASSE, "Access my account");
  await expect(page).toHaveURL(/\/tableau-de-bord/);
  await expect(page.locator("html")).toHaveAttribute("lang", "en");
  await expect(page.getByText("Foreign investor").first()).toBeVisible();
  // Retour au français depuis l'application : la page de connexion suit.
  await page.getByRole("button", { name: /User menu/ }).click();
  await page.getByRole("menuitemradio", { name: "Français" }).click();
  await expect(page.locator("html")).toHaveAttribute("lang", "fr");
});
