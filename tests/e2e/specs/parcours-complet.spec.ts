import { type Browser, expect, type Page, test } from "@playwright/test";

/**
 * Démonstration V1 de bout en bout (docs/07 §1, US-07.4), avec les personas fictives du seed :
 * l'investisseur dépose un PEX et paie les frais (TrésorPay simulé) ; le guichet le déclare
 * recevable ; le Ministre (signataire du catalogue) décide ; l'investisseur paie les droits ; le
 * Ministre signe et délivre ; n'importe qui vérifie le QR (« Document authentique ») ; après
 * révocation par le Directeur, la vérification affiche « NON VALIDE ».
 */

/** Mot de passe commun des personas, lu dans .env (chargé par playwright.config.ts). */
const MOT_DE_PASSE = process.env.DEMO_PASSWORD ?? "";
const SITE = process.env.PUBLIC_WEB_URL ?? "http://localhost:3000";

const COMPTES = {
  investisseur: "f.bangoura@horizon-invest.test",
  guichet: "a.camara@e-mine.test",
  ministre: "i.soumah@e-mine.test",
  directeur: "s.kouyate@e-mine.test",
} as const;

/** PDF minimal : l'API reconnaît le format par la signature « %PDF- » du fichier. */
function pdf(nom: string) {
  const contenu = `%PDF-1.4\n% Pièce fictive de démonstration : ${nom}\n%%EOF\n`;
  return { name: `${nom}.pdf`, mimeType: "application/pdf", buffer: Buffer.from(contenu, "utf8") };
}

/** Nouvelle session (contexte de navigateur séparé) connectée par Keycloak. */
async function connecter(browser: Browser, email: string): Promise<Page> {
  expect(MOT_DE_PASSE, "DEMO_PASSWORD doit être défini dans .env").not.toBe("");
  const contexte = await browser.newContext();
  const page = await contexte.newPage();
  await page.goto("/");
  await page.getByRole("link", { name: "Se connecter" }).click();
  await page.locator("#username").fill(email);
  await page.locator("#password").fill(MOT_DE_PASSE);
  await page.locator("#kc-login").click();
  await expect(page).toHaveURL(/\/tableau-de-bord/);
  return page;
}

/** Choisit TrésorPay, paie sur le simulateur (scénario « succès » par défaut), attend la confirmation. */
async function payerParTresorPay(page: Page, lien: RegExp) {
  await page.getByRole("link", { name: lien }).first().click();
  await page.getByRole("radio", { name: /TrésorPay/ }).check();
  await page.getByRole("button", { name: /avec TrésorPay/ }).click();
  await expect(page).toHaveURL(/localhost:8090\/payer\//);
  await page.getByRole("button", { name: /^Payer/ }).click();
  await expect(page.getByText("Paiement confirmé")).toBeVisible({ timeout: 30_000 });
}

test("parcours V1 : dépôt, paiements, recevabilité, décision, délivrance, vérification, révocation", async ({
  browser,
}) => {
  // 1. L'investisseur dépose un permis d'exploitation avec ses pièces obligatoires.
  const investisseur = await connecter(browser, COMPTES.investisseur);
  await investisseur.goto("/demandes/nouvelle");
  await investisseur.locator("#typeDocumentCode").selectOption("PEX");
  await investisseur.getByRole("button", { name: "Enregistrer et continuer" }).click();
  for (const piece of ["Étude de faisabilité", "EIES", "Plan de restauration"]) {
    await investisseur.getByLabel(`Fichier pour la pièce « ${piece} »`).setInputFiles(pdf(piece));
    await expect(investisseur.getByText(`Pièce « ${piece}.pdf » ajoutée.`)).toBeVisible();
  }
  await expect(investisseur.getByText("3 sur 3 pièces obligatoires fournies")).toBeVisible();
  await investisseur.getByRole("button", { name: "Enregistrer et continuer" }).click();
  await investisseur.getByLabel(/Je déclare sur l'honneur/).check();
  await investisseur.getByRole("button", { name: "Déposer ma demande" }).click();
  await expect(investisseur).toHaveURL(/\/demandes\/\d+\?deposee=1/);
  const id = /\/demandes\/(\d+)/.exec(investisseur.url())?.[1];
  expect(id).toBeTruthy();
  const numero = (await investisseur.getByRole("heading", { level: 1 }).textContent())?.match(
    /DEM-\d{4}-\d{6}/,
  )?.[0];
  expect(numero).toBeTruthy();

  // 2. Il paie les frais de dossier par TrésorPay (simulé).
  await payerParTresorPay(investisseur, /Payer les frais de dossier/);

  // 3. L'agent du guichet déclare la demande recevable.
  const guichet = await connecter(browser, COMPTES.guichet);
  // Le dossier figure dans sa file « À traiter », action attendue « À examiner ».
  await guichet.goto("/a-traiter");
  await expect(
    guichet.getByRole("row", { name: new RegExp(numero ?? "") }).getByText("À examiner"),
  ).toBeVisible();
  // Traitement en série : la fiche s'ouvre dans la file, puis le dossier suivant (ou la file).
  await guichet.goto(`/demandes/${id}?file=a-examiner`);
  await expect(guichet.getByRole("navigation", { name: "Traitement en série" })).toBeVisible();
  await guichet.getByLabel(/J’ai ouvert et vérifié chaque pièce jointe/).check();
  await guichet.getByRole("button", { name: "Déclarer recevable" }).click();
  await expect(guichet.getByText(`Demande ${numero} déclarée recevable.`)).toBeVisible();

  // 4. Le Ministre, signataire du PEX au catalogue (D4), rend une décision favorable.
  const ministre = await connecter(browser, COMPTES.ministre);
  await expect(ministre.getByRole("heading", { name: /Dossiers à traiter/ })).toBeVisible();
  await ministre.goto(`/demandes/${id}?file=tout`);
  await ministre.getByRole("radio", { name: /Décision favorable/ }).check();
  await expect(ministre.getByText(/payer les droits \(15\s000\s000\sGNF\)/).first()).toBeVisible();
  await ministre.getByRole("button", { name: "Enregistrer la décision…" }).click();
  await ministre.getByRole("button", { name: "Confirmer la décision" }).click();
  await expect(
    ministre.getByText(`Décision favorable enregistrée pour la demande ${numero}.`),
  ).toBeVisible();

  // 5. L'investisseur paie les droits.
  await investisseur.goto(`/demandes/${id}`);
  await payerParTresorPay(investisseur, /Payer les droits/);

  // 6. Le Ministre signe et délivre le document (PDF/A, QR, PAdES).
  await ministre.goto(`/demandes/${id}?file=a-signer`);
  await ministre.getByRole("button", { name: "Signer et délivrer…" }).click();
  await ministre.getByRole("button", { name: "Signer et délivrer", exact: true }).click();
  await expect(
    ministre.getByText(
      new RegExp(`Document PEX-\\d{4}-\\d{4} signé et délivré pour la demande ${numero}`),
    ),
  ).toBeVisible();

  // 7. L'investisseur voit son document ; n'importe qui le vérifie par le QR.
  await investisseur.goto(`/demandes/${id}`);
  await expect(investisseur.getByText("Valide", { exact: true })).toBeVisible();
  await investisseur.getByRole("button", { name: "Voir le document" }).click();
  await expect(investisseur.locator("iframe")).toHaveAttribute(
    "src",
    `/api/demandes/${id}/document/contenu?inline=1`,
  );
  const verification = await investisseur
    .getByRole("link", { name: /Page de vérification/ })
    .getAttribute("href");
  expect(verification).toMatch(/\/verifier\/[0-9a-f]{32}$/);
  const adressePublique = `${SITE}${new URL(verification ?? "").pathname}`;

  const publicContexte = await browser.newContext();
  const visiteur = await publicContexte.newPage();
  await visiteur.goto(adressePublique);
  await expect(visiteur.getByRole("heading", { name: "Document authentique" })).toBeVisible();
  await expect(visiteur.getByText("Bangoura")).toHaveCount(0);

  // 8. Le Directeur révoque le document : la vérification affiche « NON VALIDE ».
  const directeur = await connecter(browser, COMPTES.directeur);
  await directeur.goto(`/demandes/${id}`);
  await directeur.getByRole("button", { name: "Révoquer…" }).click();
  await directeur
    .getByLabel(/Motif de la révocation/)
    .fill("Document retiré lors de la démonstration de bout en bout.");
  await directeur.getByRole("button", { name: "Révoquer définitivement" }).click();
  // Attendre la confirmation de la révocation : le dialogue contient déjà « NON VALIDE ».
  await expect(directeur.getByText(/est révoqué : sa vérification affiche/)).toBeVisible();

  await visiteur.reload();
  await expect(visiteur.getByRole("heading", { name: "NON VALIDE" })).toBeVisible();
});
