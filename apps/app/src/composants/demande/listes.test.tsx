import type { DemandeDto } from "@emine/api-client";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import fr from "../../../messages/fr.json";
import { FiltresActifs } from "../liste/filtres-actifs";
import { PlusDeFiltres } from "../liste/plus-de-filtres";
import { lireParametres } from "./parametres-liste";
import { TableauDemandes } from "./tableau-demandes";

const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), refresh }) }));
const supprimer = vi.fn();
vi.mock("@/serveur/actions-demande", () => ({
  supprimerBrouillon: (id: number) => supprimer(id),
}));

function afficher(enfant: ReactNode) {
  return render(
    <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
      {enfant}
    </NextIntlClientProvider>,
  );
}

function demande(id: number, statut: string, numero: string | null): DemandeDto {
  return {
    id,
    numero,
    statut,
    typeDocumentCode: "PEX",
    demandeur: {
      prenom: "Fodé",
      nom: "Bangoura",
      email: "f@exemple.test",
      organisation: "Horizon",
    },
    prefectureId: null,
    prefectureNom: null,
    substanceCode: null,
    substanceLibelle: null,
    superficieHa: null,
    creeLe: "2026-11-20T10:00:00Z",
    deposeLe: numero ? "2026-11-20T10:00:00Z" : null,
    fraisPayes: true,
    motifRejet: null,
    etapeDepuis: "2026-11-20T10:00:00Z",
    aTraiterParMoi: false,
  };
}

const libelles = { PEX: "Permis d'exploitation" };
const lignes = [demande(1, "brouillon", null), demande(2, "delivree", "DEM-2026-000002")];

beforeEach(() => {
  refresh.mockReset();
  supprimer.mockReset();
});

describe("liste des demandes selon le profil (correctifs ergonomie)", () => {
  it("donne au demandeur des actions directes : reprendre, supprimer, télécharger", () => {
    afficher(<TableauDemandes demandes={lignes} libelles={libelles} legende="Mes demandes" />);
    expect(screen.queryByRole("columnheader", { name: "Demandeur" })).toBeNull();
    const [brouillon, delivree] = screen.getAllByRole("row").slice(1) as [HTMLElement, HTMLElement];
    expect(within(brouillon).getByText("Brouillon")).toBeVisible();
    expect(within(brouillon).getByRole("link", { name: /Reprendre/ })).toHaveAttribute(
      "href",
      "/demandes/1/modifier",
    );
    expect(within(brouillon).getByRole("button", { name: /Supprimer/ })).toBeVisible();
    const telecharger = within(delivree).getByRole("link", { name: /Télécharger/ });
    expect(telecharger).toHaveAttribute("href", "/api/demandes/2/document/contenu");
    expect(telecharger).toHaveAttribute("download");
  });

  it("montre à l'agent la colonne Demandeur, sans actions de demandeur", () => {
    afficher(
      <TableauDemandes demandes={[lignes[1]!]} libelles={libelles} legende="Demandes" interne />,
    );
    expect(screen.getByRole("columnheader", { name: "Demandeur" })).toBeVisible();
    expect(screen.getByText("Horizon")).toBeVisible();
    expect(screen.queryByRole("columnheader", { name: "Actions" })).toBeNull();
  });

  it("demande confirmation avant de supprimer un brouillon", async () => {
    supprimer.mockResolvedValue({ ok: true, valeur: null });
    afficher(<TableauDemandes demandes={lignes} libelles={libelles} legende="Mes demandes" />);
    fireEvent.click(screen.getByRole("button", { name: /Supprimer/ }));
    expect(supprimer).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Supprimer le brouillon" }));
    await waitFor(() => expect(supprimer).toHaveBeenCalledWith(1));
    await waitFor(() => expect(refresh).toHaveBeenCalled());
  });
});

describe("« Plus de filtres » dans la rangée des pastilles", () => {
  const options = [
    {
      valeur: "deposee",
      libelle: "Déposée",
      href: "/demandes?statut=deposee",
      actif: false,
      nombre: 2,
    },
  ];

  it("ouvre un panneau des filtres par statut et le ferme par Échap", () => {
    afficher(
      <PlusDeFiltres libelle="Plus de filtres" titre="Filtrer par statut" options={options} />,
    );
    const bouton = screen.getByRole("button", { name: /Plus de filtres/ });
    expect(bouton).toHaveAttribute("aria-expanded", "false");
    fireEvent.click(bouton);
    expect(screen.getByRole("region", { name: "Filtrer par statut" })).toBeVisible();
    expect(screen.getByRole("link", { name: /Déposée/ })).toHaveAttribute(
      "href",
      "/demandes?statut=deposee",
    );
    fireEvent.keyDown(document, { key: "Escape" });
    expect(screen.queryByRole("region", { name: "Filtrer par statut" })).toBeNull();
  });

  it("affiche le filtre actif sur la pastille, avec de quoi l'effacer", () => {
    afficher(
      <PlusDeFiltres
        libelle="Plus de filtres"
        titre="Filtrer par statut"
        options={options}
        actif={{ libelle: "Déposée", effacer: "/demandes", libelleEffacer: "Effacer le filtre" }}
      />,
    );
    expect(screen.getByText("Déposée")).toBeVisible();
    expect(screen.getByRole("link", { name: "Effacer le filtre" })).toHaveAttribute(
      "href",
      "/demandes",
    );
  });
});

describe("filtres ouverts depuis le tableau de bord (correctifs ergonomie)", () => {
  it("garde seulement les filtres connus de l'adresse", () => {
    const p = lireParametres({
      enRetard: "1",
      traiteParMoi: "true",
      periode: "30-jours",
      fraisPayes: "false",
      document: "PEX",
    });
    expect(p).toMatchObject({
      enRetard: "1",
      traiteParMoi: "1",
      periode: "30-jours",
      fraisPayes: "false",
      document: "PEX",
    });
    const inconnus = lireParametres({ enRetard: "oui", periode: "siecle", document: "pex" });
    expect(inconnus.enRetard).toBeUndefined();
    expect(inconnus.periode).toBeUndefined();
    expect(inconnus.document).toBeUndefined();
  });

  it("affiche chaque filtre actif avec de quoi l'effacer", () => {
    afficher(
      <FiltresActifs
        titre="Filtres actifs"
        libelleEffacer="Effacer ce filtre"
        filtres={[
          { cle: "enRetard", libelle: "En retard (à ce jour)", effacer: "/demandes" },
          { cle: "document", libelle: "Document : PEX", effacer: "/demandes?enRetard=1" },
        ]}
      />,
    );
    expect(screen.getByText("En retard (à ce jour)")).toBeVisible();
    expect(
      screen.getByRole("link", { name: "Effacer ce filtre : Document : PEX" }),
    ).toHaveAttribute("href", "/demandes?enRetard=1");
  });
});
