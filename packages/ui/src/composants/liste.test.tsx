import { render, screen, within } from "@testing-library/react";
import { TableauDonnees } from "./liste";

type Ligne = { id: number; numero: string; actions: boolean };

const lignes: Ligne[] = [
  { id: 1, numero: "RMB-2026-000001", actions: true },
  { id: 2, numero: "RMB-2026-000002", actions: false },
];

describe("TableauDonnees : colonne Actions jamais vide (US-11.8)", () => {
  it("propose « Voir le détail » quand la ligne n'a aucune action", () => {
    render(
      <TableauDonnees
        legende="Remboursements"
        lignes={lignes}
        cleLigne={(l) => l.id}
        lienLigne={(l) => `/remboursements/${l.id}`}
        colonnes={[
          { cle: "numero", entete: "Numéro", rendu: (l) => l.numero },
          {
            cle: "actions",
            entete: "Actions",
            interactive: true,
            voirSiVide: "Voir le détail",
            rendu: (l) => (l.actions ? <button type="button">Valider</button> : null),
          },
        ]}
      />,
    );
    const [avecAction, sansAction] = screen.getAllByRole("row").slice(1);
    expect(within(avecAction!).getByRole("button", { name: "Valider" })).toBeVisible();
    expect(within(avecAction!).queryByRole("link", { name: "Voir le détail" })).toBeNull();
    const voir = within(sansAction!).getByRole("link", { name: "Voir le détail" });
    expect(voir).toHaveAttribute("href", "/remboursements/2");
    expect(voir).toHaveClass("whitespace-nowrap", "min-h-11");
  });
});
