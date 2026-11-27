import { act, fireEvent, render, screen } from "@testing-library/react";
import { useState } from "react";
import { echellePage, LARGEUR_PAGE_MAX, pageCourante } from "./lecteur-pdf";
import { type DocumentAffiche, VisualiseurDocument, zoomVoisin } from "./visualiseur";

// pdf.js simulé : un document de 3 pages A4 (595 × 842 points).
const couchesTexte = vi.hoisted(() => [] as { container: HTMLElement }[]);

vi.mock("pdfjs-dist", () => ({
  GlobalWorkerOptions: {},
  TextLayer: vi.fn(function (this: { render: () => Promise<void> }, options) {
    couchesTexte.push(options);
    this.render = () => Promise.resolve();
    Object.assign(this, { cancel: vi.fn() });
  }),
  getDocument: vi.fn(() => ({
    destroy: vi.fn(() => Promise.resolve()),
    promise: Promise.resolve({
      numPages: 3,
      destroy: vi.fn(() => Promise.resolve()),
      getPage: vi.fn(() =>
        Promise.resolve({
          getViewport: ({ scale }: { scale: number }) => ({
            width: 595 * scale,
            height: 842 * scale,
          }),
          render: vi.fn(() => ({ promise: Promise.resolve(), cancel: vi.fn() })),
          streamTextContent: vi.fn(() => ({})),
        }),
      ),
    }),
  })),
}));

const libelles = {
  telecharger: "Télécharger",
  imprimer: "Imprimer",
  zoomAvant: "Zoom avant",
  zoomArriere: "Zoom arrière",
  ajuster: "Largeur",
  fermer: "Fermer le document",
  formatNonAffichable: "Format non affichable",
  chargement: "Chargement du document…",
  erreurPdf: "Le document ne peut pas être affiché.",
  page: (n: number, total: number) => `Page ${n} sur ${total}`,
};

const pdf: DocumentAffiche = {
  nomFichier: "eies.pdf",
  typeMime: "application/pdf",
  urlAffichage: "/api/demandes/1/pieces/2/contenu?inline=1",
  urlTelechargement: "/api/demandes/1/pieces/2/contenu",
};

function Banc({ document }: { document: DocumentAffiche }) {
  const [ouvert, setOuvert] = useState<DocumentAffiche | null>(null);
  return (
    <>
      <button type="button" onClick={() => setOuvert(document)}>
        Consulter
      </button>
      <p>Liste toujours visible</p>
      <VisualiseurDocument
        document={ouvert}
        surFermeture={() => setOuvert(null)}
        libelles={libelles}
      />
    </>
  );
}

describe("visualiseur de documents", () => {
  it("affiche un PDF en pleine largeur, pages empilées, avec « Page X sur Y »", async () => {
    render(<Banc document={pdf} />);
    fireEvent.click(screen.getByRole("button", { name: "Consulter" }));
    const panneau = screen.getByRole("complementary", { name: "eies.pdf" });
    expect(panneau).toHaveFocus();
    expect(panneau.className).not.toMatch(/sm:w-\[/);
    expect(screen.getByRole("status")).toHaveTextContent("Chargement du document…");
    expect(await screen.findByText("Page 1 sur 3")).toBeVisible();
    // Une page par emplacement, empilées dans l'ordre.
    const pages = screen.getAllByRole("img");
    expect(pages.map((p) => p.getAttribute("aria-label"))).toEqual([
      "Page 1 sur 3",
      "Page 2 sur 3",
      "Page 3 sur 3",
    ]);
    // Texte sélectionnable : une couche texte pdf.js posée sur chaque page rendue.
    await vi.waitFor(() => expect(couchesTexte.length).toBeGreaterThanOrEqual(2));
    for (const c of couchesTexte) {
      expect(c.container).toHaveClass("textLayer");
      expect(c.container.closest("[data-page]")).not.toBeNull();
    }
    // Une seule barre d'outils : un seul « Télécharger ».
    expect(screen.getAllByRole("link", { name: "Télécharger" })).toHaveLength(1);
    expect(screen.getByRole("link", { name: "Télécharger" })).toHaveAttribute(
      "href",
      pdf.urlTelechargement,
    );
    expect(screen.getByRole("button", { name: "Imprimer" })).toBeVisible();
    expect(screen.getByText("Liste toujours visible")).toBeVisible();
  });

  it("affiche l'avertissement et son détail au-dessus du document", async () => {
    render(
      <Banc
        document={{
          ...pdf,
          avertissement: "Document révoqué le 20/11/2026 : il n’est plus valide",
          avertissementDetail: "Révoqué par : Directeur national\nMotif : fraude",
        }}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: "Consulter" }));
    expect(screen.getByText(/Document révoqué le 20\/11\/2026/)).toBeVisible();
    expect(screen.getByText(/Révoqué par : Directeur national/)).toBeVisible();
    await screen.findByText("Page 1 sur 3");
  });

  it("zoome par paliers et revient à la largeur", async () => {
    render(<Banc document={pdf} />);
    fireEvent.click(screen.getByRole("button", { name: "Consulter" }));
    await screen.findByText("Page 1 sur 3");
    fireEvent.click(screen.getByRole("button", { name: "Zoom avant" }));
    expect(screen.getByText("125 %")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Zoom arrière" }));
    fireEvent.click(screen.getByRole("button", { name: "Zoom arrière" }));
    expect(screen.getByText("75 %")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "75 %" }));
    expect(screen.getByRole("button", { name: "Largeur" })).toHaveAttribute("aria-pressed", "true");
  });

  it("calcule zoom, échelle des pages et page la plus visible", () => {
    expect(zoomVoisin("largeur", 1)).toBe(125);
    expect(zoomVoisin(50, -1)).toBe(50);
    expect(zoomVoisin(200, 1)).toBe(200);
    const a4 = { largeur: 794, hauteur: 1123 };
    // À la largeur : la zone moins les marges, plafonnée pour rester lisible sur grand écran.
    expect(echellePage(a4, "largeur", 375)).toBeCloseTo((375 - 32) / 794);
    expect(echellePage(a4, "largeur", 3000)).toBeCloseTo(LARGEUR_PAGE_MAX / 794);
    expect(echellePage(a4, 150, 375)).toBe(1.5);
    expect(pageCourante([0, 0, 0])).toBe(1);
    expect(pageCourante([120, 600, 0])).toBe(2);
    expect(pageCourante([0, 200, 700])).toBe(3);
  });

  it("se ferme par Échap et rend le focus au bouton qui l'a ouvert", async () => {
    render(<Banc document={pdf} />);
    const bouton = screen.getByRole("button", { name: "Consulter" });
    bouton.focus();
    fireEvent.click(bouton);
    await act(async () => undefined);
    fireEvent.keyDown(document, { key: "Escape" });
    expect(screen.queryByRole("complementary")).toBeNull();
    expect(bouton).toHaveFocus();
  });

  it("affiche une image avec son nom comme texte de remplacement", () => {
    render(<Banc document={{ ...pdf, nomFichier: "plan.png", typeMime: "image/png" }} />);
    fireEvent.click(screen.getByRole("button", { name: "Consulter" }));
    expect(screen.getByRole("img", { name: "plan.png" })).toHaveAttribute("src", pdf.urlAffichage);
  });

  it("se ferme par un bouton « Fermer » visible ou par un clic à côté du panneau", async () => {
    render(<Banc document={pdf} />);
    fireEvent.click(screen.getByRole("button", { name: "Consulter" }));
    await screen.findByText("Page 1 sur 3");
    expect(screen.getByRole("button", { name: "Fermer le document" })).toHaveTextContent(
      "Fermer le document",
    );
    // Rendu par un portail dans <body>, au-dessus de toute la page.
    const fond = document.body.querySelector(":scope > div[aria-hidden='true'].fixed");
    expect(fond).not.toBeNull();
    fireEvent.click(fond!);
    expect(screen.queryByRole("complementary")).toBeNull();
    expect(screen.getByText("Liste toujours visible")).toBeInTheDocument();
  });
});
