import { fireEvent, render, screen, within } from "@testing-library/react";
import { CircleHelp, FileText, LayoutDashboard, Plus } from "lucide-react";
import { FilAriane } from "./fil-ariane";
import { GabaritApplication } from "./gabarit";
import { BadgeStatut } from "./badge-statut";
import { EtatVide } from "./page";
import { TableauDonnees } from "./liste";

const libelles = {
  navigation: "Navigation principale",
  ouvrirMenu: "Ouvrir le menu",
  fermerMenu: "Fermer le menu",
  reduireMenu: "Réduire le menu",
  deplierMenu: "Déplier le menu",
};

function afficher(supplements: Partial<Parameters<typeof GabaritApplication>[0]> = {}) {
  return render(
    <GabaritApplication
      {...supplements}
      marque="e-mine"
      titre="Mes demandes"
      utilisateur={{ nomComplet: "Fodé Bangoura", profil: "Investisseur" }}
      libelles={libelles}
      sections={[
        {
          cle: "principal",
          titre: "Principal",
          entrees: [
            {
              cle: "a",
              href: "/tableau-de-bord",
              libelle: "Tableau de bord",
              icone: LayoutDashboard,
              actif: false,
            },
            {
              cle: "b",
              href: "/demandes",
              libelle: "Mes demandes",
              icone: FileText,
              actif: true,
              compteur: 3,
              libelleCompteur: "3 à traiter",
            },
          ],
        },
      ]}
    >
      <p>Contenu</p>
    </GabaritApplication>,
  );
}

describe("gabarit de l'application", () => {
  beforeEach(() => localStorage.clear());

  it("affiche la navigation, l'entrée active, le titre de la page et l'utilisateur", () => {
    afficher();
    const nav = screen.getAllByRole("navigation", { name: "Navigation principale" })[0]!;
    expect(within(nav).getByRole("link", { name: /^Mes demandes/ })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(within(nav).getByRole("link", { name: "Tableau de bord" })).not.toHaveAttribute(
      "aria-current",
    );
    expect(screen.getByText("Fodé Bangoura")).toBeInTheDocument();
    expect(screen.getByRole("main")).toHaveTextContent("Contenu");
  });

  it("réduit le menu et mémorise ce choix", () => {
    afficher();
    fireEvent.click(screen.getByRole("button", { name: "Réduire le menu" }));
    expect(screen.getByRole("button", { name: "Déplier le menu" })).toHaveAttribute(
      "aria-expanded",
      "false",
    );
    expect(localStorage.getItem("emine:interface:menu-replie")).toBe("1");
  });

  it("ouvre le tiroir mobile et le ferme avec Échap", () => {
    afficher();
    fireEvent.click(screen.getByRole("button", { name: "Ouvrir le menu" }));
    expect(screen.getAllByRole("navigation", { name: "Navigation principale" })).toHaveLength(2);
    fireEvent.keyDown(document, { key: "Escape" });
    expect(screen.getAllByRole("navigation", { name: "Navigation principale" })).toHaveLength(1);
  });

  it("tiroir mobile : fenêtre modale, focus gardé à l'intérieur, page derrière immobile", () => {
    afficher();
    fireEvent.click(screen.getByRole("button", { name: "Ouvrir le menu" }));
    const tiroir = screen.getByRole("dialog", { name: "Navigation principale" });
    expect(tiroir).toHaveAttribute("aria-modal", "true");
    expect(document.body.style.overflow).toBe("hidden");
    const liens = within(tiroir).getAllByRole("link");
    liens[liens.length - 1]!.focus();
    fireEvent.keyDown(document, { key: "Tab" });
    expect(tiroir).toContainElement(document.activeElement as HTMLElement);
    fireEvent.keyDown(document, { key: "Escape" });
    expect(document.body.style.overflow).toBe("");
  });

  it("action principale en tête du menu, compteur à côté de la rubrique, aide en bas, pied de page", () => {
    afficher({
      actionPrincipale: { libelle: "Nouvelle demande", href: "/demandes/nouvelle", icone: Plus },
      aide: { libelle: "Aide", href: "/aide", icone: CircleHelp },
      recherche: <button type="button">Rechercher</button>,
      piedDePage: <p>Mentions légales</p>,
    });
    const nav = screen.getAllByRole("navigation", { name: "Navigation principale" })[0]!;
    const aside = nav.closest("aside") ?? document.body;
    const liens = within(aside as HTMLElement).getAllByRole("link");
    expect(liens[0]).toHaveAccessibleName("Nouvelle demande");
    expect(liens[0]).toHaveAttribute("href", "/demandes/nouvelle");
    expect(liens[liens.length - 1]).toHaveAccessibleName("Aide");
    // Le compteur est visible et lu en clair par les lecteurs d'écran.
    const rubrique = within(nav).getByRole("link", { name: /Mes demandes/ });
    expect(rubrique).toHaveTextContent("3");
    expect(rubrique).toHaveAccessibleName("Mes demandes, 3 à traiter");
    expect(screen.getByRole("button", { name: "Rechercher" })).toBeInTheDocument();
    expect(screen.getByRole("contentinfo")).toHaveTextContent("Mentions légales");
  });

  it("fil d'Ariane : étapes parentes en liens, page courante annoncée", () => {
    render(
      <FilAriane
        libelle="Fil d'Ariane"
        etapes={[{ libelle: "Demandes", href: "/demandes" }, { libelle: "DEM-2026-000009" }]}
      />,
    );
    const fil = screen.getByRole("navigation", { name: "Fil d'Ariane" });
    expect(within(fil).getByRole("link", { name: "Demandes" })).toHaveAttribute(
      "href",
      "/demandes",
    );
    expect(within(fil).getByText("DEM-2026-000009")).toHaveAttribute("aria-current", "page");
  });
});

describe("composants de liste et de page", () => {
  it("tableau : en-têtes triables annoncés (aria-sort) et intitulés de cellule pour le mobile", () => {
    render(
      <TableauDonnees
        legende="Demandes"
        lignes={[{ id: 1, numero: "DEM-2026-000001" }]}
        cleLigne={(l) => l.id}
        tri={{ colonne: "numero", sens: "desc" }}
        lienTri={(c) => `/demandes?tri=${c}`}
        colonnes={[{ cle: "numero", entete: "Numéro", triable: true, rendu: (l) => l.numero }]}
      />,
    );
    expect(screen.getByRole("table", { name: "Demandes" })).toBeInTheDocument();
    expect(screen.getByRole("columnheader")).toHaveAttribute("aria-sort", "descending");
    expect(screen.getByRole("link", { name: /Trier par Numéro, tri décroissant/ })).toHaveAttribute(
      "href",
      "/demandes?tri=numero",
    );
    expect(screen.getByRole("cell")).toHaveTextContent("DEM-2026-000001");
  });

  it("ligne cliquable : la ligne entière est un vrai lien, les actions gardent leur comportement", () => {
    const action = vi.fn();
    render(
      <TableauDonnees
        legende="Demandes"
        lignes={[{ id: 7, numero: "DEM-2026-000007" }]}
        cleLigne={(l) => l.id}
        lienLigne={(l) => `/demandes/${l.id}`}
        colonnes={[
          { cle: "numero", entete: "Numéro", rendu: (l) => l.numero },
          {
            cle: "actions",
            entete: "Actions",
            interactive: true,
            rendu: () => (
              <button type="button" onClick={action}>
                Archiver
              </button>
            ),
          },
        ]}
      />,
    );
    const lien = screen.getByRole("link", { name: "DEM-2026-000007" });
    expect(lien).toHaveAttribute("href", "/demandes/7");
    expect(lien.className).toMatch(/after:absolute/);
    expect(lien.className).toMatch(/after:inset-0/);
    const ligne = lien.closest("tr") as HTMLElement;
    expect(ligne.className).toMatch(/relative/);
    expect(ligne.className).toMatch(/cursor-pointer/);
    expect(ligne.className).toMatch(/hover:bg-fond/);
    const bouton = screen.getByRole("button", { name: "Archiver" });
    expect(bouton.parentElement?.className).toMatch(/relative z-10/);
    fireEvent.click(bouton);
    expect(action).toHaveBeenCalledTimes(1);
  });

  it("badge : pastille décorative et libellé lisible", () => {
    render(<BadgeStatut ton="succes">Délivrée</BadgeStatut>);
    expect(screen.getByText("Délivrée")).toBeInTheDocument();
  });

  it("état vide : titre et action", () => {
    render(<EtatVide titre="Aucune demande" action={<a href="/n">Nouvelle demande</a>} />);
    expect(screen.getByText("Aucune demande")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Nouvelle demande" })).toBeInTheDocument();
  });
});
