import { fireEvent, render, screen } from "@testing-library/react";
import { useState } from "react";
import { type DocumentAffiche, VisualiseurDocument } from "./visualiseur";

const libelles = {
  telecharger: "Télécharger",
  pleinEcran: "Plein écran",
  quitterPleinEcran: "Quitter le plein écran",
  fermer: "Fermer le document",
  formatNonAffichable: "Format non affichable",
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
  it("affiche un PDF dans un panneau latéral, sans masquer la page", () => {
    render(<Banc document={pdf} />);
    fireEvent.click(screen.getByRole("button", { name: "Consulter" }));
    const panneau = screen.getByRole("complementary", { name: "eies.pdf" });
    expect(panneau).toHaveFocus();
    expect(document.querySelector("iframe")).toHaveAttribute("src", pdf.urlAffichage);
    expect(screen.getByRole("link", { name: "Télécharger" })).toHaveAttribute(
      "href",
      pdf.urlTelechargement,
    );
    expect(screen.getByText("Liste toujours visible")).toBeVisible();
  });

  it("se ferme par Échap et rend le focus au bouton qui l'a ouvert", () => {
    render(<Banc document={pdf} />);
    const bouton = screen.getByRole("button", { name: "Consulter" });
    bouton.focus();
    fireEvent.click(bouton);
    fireEvent.keyDown(document, { key: "Escape" });
    expect(screen.queryByRole("complementary")).toBeNull();
    expect(bouton).toHaveFocus();
  });

  it("bascule en plein écran et se ferme par le bouton Fermer", () => {
    render(<Banc document={pdf} />);
    fireEvent.click(screen.getByRole("button", { name: "Consulter" }));
    const plein = screen.getByRole("button", { name: "Plein écran" });
    fireEvent.click(plein);
    expect(screen.getByRole("button", { name: "Quitter le plein écran" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    fireEvent.click(screen.getByRole("button", { name: "Fermer le document" }));
    expect(screen.queryByRole("complementary")).toBeNull();
  });

  it("affiche une image avec son nom comme texte de remplacement", () => {
    render(<Banc document={{ ...pdf, nomFichier: "plan.png", typeMime: "image/png" }} />);
    fireEvent.click(screen.getByRole("button", { name: "Consulter" }));
    expect(screen.getByRole("img", { name: "plan.png" })).toHaveAttribute("src", pdf.urlAffichage);
  });

  it("se ferme par un bouton « Fermer » visible ou par un clic à côté du panneau", () => {
    const { container } = render(<Banc document={pdf} />);
    fireEvent.click(screen.getByRole("button", { name: "Consulter" }));
    expect(screen.getByRole("button", { name: "Fermer le document" })).toHaveTextContent(
      "Fermer le document",
    );
    const fond = container.querySelector("div[aria-hidden='true'].fixed");
    expect(fond).not.toBeNull();
    fireEvent.click(fond!);
    expect(screen.queryByTitle("eies.pdf")).not.toBeInTheDocument();
    expect(screen.getByText("Liste toujours visible")).toBeInTheDocument();
  });
});
