import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { type ElementMenuUtilisateur, initiales, MenuUtilisateur } from "./menu-utilisateur";

function afficher(surAnglais = vi.fn(), surDeconnexion = vi.fn()) {
  const elements: ElementMenuUtilisateur[] = [
    { genre: "lien", cle: "profil", libelle: "Mon profil", href: "/profil" },
    {
      genre: "groupe",
      cle: "langue",
      libelle: "Langue",
      choix: [
        { cle: "fr", libelle: "Français", lang: "fr", coche: true, surSelection: vi.fn() },
        { cle: "en", libelle: "English", lang: "en", coche: false, surSelection: surAnglais },
      ],
    },
    { genre: "separateur", cle: "sep" },
    {
      genre: "action",
      cle: "deconnexion",
      libelle: "Se déconnecter",
      surSelection: surDeconnexion,
    },
  ];
  render(
    <div>
      <MenuUtilisateur
        nomComplet="Ousmane Bah"
        profil="Administrateur Technique"
        libelle="Menu de l’utilisateur"
        elements={elements}
      />
      <p>Ailleurs</p>
    </div>,
  );
  return { surAnglais, surDeconnexion };
}

const bouton = () => screen.getByRole("button", { name: /Menu de l’utilisateur/ });

describe("menu de l'utilisateur (sous l'avatar)", () => {
  it("affiche les initiales et annonce un menu fermé", () => {
    afficher();
    expect(initiales("Ousmane Bah")).toBe("OB");
    expect(bouton()).toHaveAttribute("aria-haspopup", "menu");
    expect(bouton()).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByRole("menu")).toBeNull();
  });

  it("s'ouvre avec en-tête, éléments, séparateur, et place le focus sur le premier élément", async () => {
    afficher();
    await userEvent.click(bouton());
    expect(bouton()).toHaveAttribute("aria-expanded", "true");
    const menu = screen.getByRole("menu");
    expect(menu).toHaveTextContent("Ousmane Bah");
    expect(menu).toHaveTextContent("Administrateur Technique");
    expect(screen.getByRole("menuitem", { name: "Mon profil" })).toHaveFocus();
    expect(screen.getByRole("menuitemradio", { name: "Français" })).toHaveAttribute(
      "aria-checked",
      "true",
    );
    expect(screen.getAllByRole("separator").length).toBeGreaterThan(0);
  });

  it("se parcourt aux flèches, Début et Fin, et se ferme par Échap en rendant le focus", async () => {
    afficher();
    await userEvent.click(bouton());
    await userEvent.keyboard("{ArrowDown}");
    expect(screen.getByRole("menuitemradio", { name: "Français" })).toHaveFocus();
    await userEvent.keyboard("{End}");
    expect(screen.getByRole("menuitem", { name: "Se déconnecter" })).toHaveFocus();
    await userEvent.keyboard("{ArrowDown}");
    expect(screen.getByRole("menuitem", { name: "Mon profil" })).toHaveFocus();
    await userEvent.keyboard("{Home}{ArrowUp}");
    expect(screen.getByRole("menuitem", { name: "Se déconnecter" })).toHaveFocus();
    await userEvent.keyboard("{Escape}");
    expect(screen.queryByRole("menu")).toBeNull();
    expect(bouton()).toHaveFocus();
  });

  it("s'ouvre aussi au clavier depuis le bouton (flèche bas)", async () => {
    afficher();
    bouton().focus();
    await userEvent.keyboard("{ArrowDown}");
    expect(screen.getByRole("menu")).toBeInTheDocument();
  });

  it("se ferme par un clic à l'extérieur", async () => {
    afficher();
    await userEvent.click(bouton());
    fireEvent.pointerDown(screen.getByText("Ailleurs"));
    expect(screen.queryByRole("menu")).toBeNull();
  });

  it("exécute le choix et referme le menu", async () => {
    const { surAnglais, surDeconnexion } = afficher();
    await userEvent.click(bouton());
    await userEvent.click(screen.getByRole("menuitemradio", { name: "English" }));
    expect(surAnglais).toHaveBeenCalled();
    expect(screen.queryByRole("menu")).toBeNull();
    await userEvent.click(bouton());
    await userEvent.click(screen.getByRole("menuitem", { name: "Se déconnecter" }));
    expect(surDeconnexion).toHaveBeenCalled();
  });

  it("donne à chaque langue son attribut lang", async () => {
    afficher();
    await userEvent.click(bouton());
    expect(screen.getByRole("menuitemradio", { name: "English" })).toHaveAttribute("lang", "en");
  });
});
