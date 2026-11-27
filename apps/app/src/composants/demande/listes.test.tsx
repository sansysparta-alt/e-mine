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
    paiementEnVerification: false,
    motifRejet: null,
    etapeDepuis: "2026-11-20T10:00:00Z",
    aTraiterParMoi: false,
    passages: 0,
    bloque: false,
    enRetard: false,
    joursRestants: null,
    procheDepassement: false,
    delaiSuspendu: false,
    motifSuspension: null,
    delaiDemarre: true,
    documentRevoque: false,
    avancement: null,
  };
}

const libelles = { PEX: "Permis d'exploitation" };
const lignes = [demande(1, "brouillon", null), demande(2, "delivree", "DEM-2026-000002")];

beforeEach(() => {
  refresh.mockReset();
  supprimer.mockReset();
});

describe("liste des demandes selon le profil (correctifs ergonomie)", () => {
  it("donne au demandeur des actions directes : continuer, supprimer, télécharger", () => {
    afficher(<TableauDemandes demandes={lignes} libelles={libelles} legende="Mes demandes" />);
    expect(screen.queryByRole("columnheader", { name: "Demandeur" })).toBeNull();
    const [brouillon, delivree] = screen.getAllByRole("row").slice(1) as [HTMLElement, HTMLElement];
    expect(within(brouillon).getByText("Brouillon")).toBeVisible();
    expect(within(brouillon).getByRole("link", { name: /Continuer/ })).toHaveAttribute(
      "href",
      "/demandes/1/modifier",
    );
    expect(within(brouillon).getByRole("button", { name: /Supprimer/ })).toBeVisible();
    const telecharger = within(delivree).getByRole("link", { name: /Télécharger/ });
    expect(telecharger).toHaveAttribute("href", "/api/demandes/2/document/contenu");
    expect(telecharger).toHaveAttribute("download");
  });

  it("propose l'action attendue du demandeur, l'étape en cours, statut juste après le numéro", () => {
    const frais = {
      ...demande(5, "deposee", "DEM-2026-000005"),
      fraisPayes: false,
      avancement: { etape: 2, total: 6 },
    };
    const droits = { ...demande(6, "decision", "DEM-2026-000006"), fraisPayes: true };
    const complement = { ...demande(7, "complement_requis", "DEM-2026-000007"), fraisPayes: true };
    afficher(
      <TableauDemandes
        demandes={[frais, droits, complement]}
        libelles={libelles}
        legende="Mes demandes"
        recherche="0005"
      />,
    );
    const entetes = screen.getAllByRole("columnheader").map((c) => c.textContent);
    expect(entetes.indexOf("Statut")).toBe(1);
    const [l1, l2, l3] = screen.getAllByRole("row").slice(1) as [
      HTMLElement,
      HTMLElement,
      HTMLElement,
    ];
    expect(within(l1).getByRole("link", { name: /Payer les frais/ })).toHaveAttribute(
      "href",
      "/demandes/5/paiement",
    );
    expect(within(l1).getByText("Étape 2 sur 6")).toBeVisible();
    expect(l1.querySelector("mark")).toHaveTextContent("0005");
    expect(within(l2).getByRole("link", { name: /Payer les droits/ })).toBeVisible();
    expect(within(l3).getByRole("link", { name: /Compléter/ })).toHaveAttribute(
      "href",
      "/demandes/7",
    );
  });

  it("montre à l'agent la colonne Demandeur, sans actions de demandeur", () => {
    afficher(
      <TableauDemandes demandes={[lignes[1]!]} libelles={libelles} legende="Demandes" interne />,
    );
    expect(screen.getByRole("columnheader", { name: "Demandeur" })).toBeVisible();
    expect(screen.getByText("Horizon")).toBeVisible();
    expect(screen.queryByRole("columnheader", { name: "Actions" })).toBeNull();
  });

  it("signale à l'agent le dossier hors délai, calculé par l'API (revue B)", () => {
    const enRetard = { ...demande(3, "deposee", "DEM-2026-000003"), enRetard: true };
    const { unmount } = afficher(
      <TableauDemandes
        demandes={[enRetard, demande(4, "deposee", "DEM-2026-000004")]}
        libelles={libelles}
        legende="Demandes"
        interne
      />,
    );
    const [ligneRetard, ligneDansLeDelai] = screen.getAllByRole("row").slice(1) as [
      HTMLElement,
      HTMLElement,
    ];
    expect(within(ligneRetard).getByText("Délai du catalogue dépassé")).toBeVisible();
    expect(within(ligneDansLeDelai).queryByText("Délai du catalogue dépassé")).toBeNull();
    unmount();
    // Le demandeur garde une liste sobre : l'information est sur la fiche.
    afficher(<TableauDemandes demandes={[enRetard]} libelles={libelles} legende="Mes demandes" />);
    expect(screen.queryByText("Délai du catalogue dépassé")).toBeNull();
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
