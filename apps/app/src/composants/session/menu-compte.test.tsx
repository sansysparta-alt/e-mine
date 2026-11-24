import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import fr from "../../../messages/fr.json";
import { MenuCompte } from "./menu-compte";

const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), refresh }) }));
const changerLangue = vi.fn(async (_langue: string) => {});
vi.mock("@/serveur/actions-langue", () => ({ changerLangue: (l: string) => changerLangue(l) }));
const effacer = vi.fn();
vi.mock("../demande/sauvegarde-locale", () => ({ effacerToutesLesSaisies: () => effacer() }));

function afficher() {
  return render(
    <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
      <MenuCompte
        utilisateur={{ nomComplet: "Ousmane Bah", profil: "Administrateur Technique" }}
        Lien={({ href, children, ...p }) => (
          <a href={href} {...p}>
            {children}
          </a>
        )}
      />
    </NextIntlClientProvider>,
  );
}

const ouvrir = async () =>
  fireEvent.click(screen.getByRole("button", { name: /Menu de l’utilisateur/ }));

describe("menu du compte (porte d'entrée)", () => {
  beforeEach(() => {
    refresh.mockReset();
    changerLangue.mockClear();
    effacer.mockReset();
  });

  it("propose Mon profil, la langue puis Se déconnecter, sans bouton rouge", async () => {
    afficher();
    await ouvrir();
    expect(screen.getByRole("menuitem", { name: "Mon profil" })).toHaveAttribute("href", "/profil");
    expect(screen.getByRole("menuitemradio", { name: "Français" })).toHaveAttribute(
      "aria-checked",
      "true",
    );
    const sortie = screen.getByRole("menuitem", { name: "Se déconnecter" });
    expect(sortie.className).not.toMatch(/danger|rouge|red/);
  });

  it("change la langue puis rafraîchit la page", async () => {
    afficher();
    await ouvrir();
    fireEvent.click(screen.getByRole("menuitemradio", { name: "English" }));
    await waitFor(() => expect(changerLangue).toHaveBeenCalledWith("en"));
    await waitFor(() => expect(refresh).toHaveBeenCalled());
  });

  it("déconnecte par un envoi POST après avoir effacé les saisies du navigateur", async () => {
    const { container } = afficher();
    const formulaire = container.querySelector("form") as HTMLFormElement;
    expect(formulaire).toHaveAttribute("method", "post");
    expect(formulaire).toHaveAttribute("action", "/api/auth/logout");
    const envoi = vi.fn((e: Event) => e.preventDefault());
    formulaire.addEventListener("submit", envoi);
    await ouvrir();
    fireEvent.click(screen.getByRole("menuitem", { name: "Se déconnecter" }));
    expect(effacer).toHaveBeenCalled();
    expect(envoi).toHaveBeenCalled();
  });
});
