import { fireEvent, render, screen, within } from "@testing-library/react";
import { FileText, LayoutDashboard, ScrollText } from "@emine/ui/icones";
import { NextIntlClientProvider } from "next-intl";
import en from "../../../messages/en.json";
import fr from "../../../messages/fr.json";
import { lireRecentesGlobales, RechercheGlobale } from "./recherche-globale";
import { memoriserDossierRecent, memoriserRecente as memoriser } from "./recentes";

const COMPTE = 12;
const memoriserRecente = (texte: string) => memoriser(`emine.recherches-recentes.${COMPTE}`, texte);
const lireRecentes = () => lireRecentesGlobales(COMPTE);

const pousser = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: pousser }) }));

const pages = [
  {
    cle: "tableauDeBord",
    libelle: "Tableau de bord",
    href: "/tableau-de-bord",
    icone: LayoutDashboard,
  },
  { cle: "demandes", libelle: "Demandes", href: "/demandes", icone: FileText },
  { cle: "journalAudit", libelle: "Journal d’audit", href: "/journal-audit", icone: ScrollText },
];

function afficher() {
  return render(
    <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
      <RechercheGlobale compte={COMPTE} pages={pages} lienDemandes="/demandes" />
    </NextIntlClientProvider>,
  );
}

function reponse(demandes: unknown[], total = demandes.length) {
  return vi.fn(async () => new Response(JSON.stringify({ demandes, total }), { status: 200 }));
}

describe("recherche globale (Ctrl+K)", () => {
  beforeEach(() => {
    localStorage.clear();
    pousser.mockReset();
  });
  afterEach(() => vi.unstubAllGlobals());

  it("s'ouvre avec Ctrl+K ; champ vide : une invitation, pas la liste des pages qui double le menu", () => {
    afficher();
    fireEvent.keyDown(document, { key: "k", ctrlKey: true });
    const champ = screen.getByRole("combobox", { name: "Rechercher dans e-mine" });
    expect(champ).toHaveFocus();
    expect(screen.queryAllByRole("option")).toHaveLength(0);
    expect(screen.getByText(/Saisissez un numéro de dossier/)).toBeVisible();
  });

  it("champ vide : propose les derniers dossiers ouverts par ce compte seulement", () => {
    memoriserDossierRecent(COMPTE, { id: 9, numero: "DEM-2026-000009", document: "Permis" });
    memoriserDossierRecent(99, { id: 4, numero: "DEM-2026-000004", document: "Autre compte" });
    afficher();
    fireEvent.keyDown(document, { key: "k", ctrlKey: true });
    const groupe = screen.getByRole("group", { name: "Derniers dossiers ouverts" });
    expect(within(groupe).getAllByRole("option")).toHaveLength(1);
    fireEvent.click(within(groupe).getByRole("option", { name: /DEM-2026-000009/ }));
    expect(pousser).toHaveBeenCalledWith("/demandes/9");
  });

  it("filtre les pages pendant la saisie, sans tenir compte des accents", () => {
    afficher();
    fireEvent.keyDown(document, { key: "k", ctrlKey: true });
    const champ = screen.getByRole("combobox", { name: "Rechercher dans e-mine" });
    fireEvent.change(champ, { target: { value: "j" } });
    const options = screen.getAllByRole("option");
    expect(options).toHaveLength(1);
    expect(options[0]).toHaveTextContent("Journal d’audit");
    fireEvent.change(champ, { target: { value: "TABLEAU" } });
    expect(within(screen.getByRole("option")).getByText("Tableau")).toContainHTML("Tableau");
    expect(screen.getByRole("option").querySelector("mark")).toHaveTextContent("Tableau");
  });

  it("trouve les dossiers par une partie du numéro, les ouvre et mémorise la recherche", async () => {
    vi.stubGlobal(
      "fetch",
      reponse([
        { id: 9, numero: "DEM-2026-000009", document: "Permis de recherche", demandeur: null },
      ]),
    );
    afficher();
    fireEvent.click(screen.getByRole("button", { name: "Rechercher (Ctrl+K)" }));
    const champ = screen.getByRole("combobox");
    fireEvent.change(champ, { target: { value: "0009" } });
    const option = await screen.findByRole("option", { name: /DEM-2026-000009/ });
    expect(option.querySelector("mark")).toHaveTextContent("0009");
    fireEvent.keyDown(champ, { key: "Enter" });
    expect(pousser).toHaveBeenCalledWith("/demandes/9");
    expect(lireRecentes()).toEqual(["0009"]);
  });

  it("aucun résultat : dit quoi essayer ; la croix vide le champ", async () => {
    vi.stubGlobal("fetch", reponse([]));
    afficher();
    fireEvent.click(screen.getByRole("button", { name: "Rechercher (Ctrl+K)" }));
    const champ = screen.getByRole("combobox");
    fireEvent.change(champ, { target: { value: "zzzz" } });
    expect(await screen.findByText("Aucun résultat pour « zzzz »")).toBeInTheDocument();
    expect(screen.getByText(/partie du numéro/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Effacer la recherche" }));
    expect(champ).toHaveValue("");
    expect(champ).toHaveFocus();
  });

  it("propose les recherches récentes, sans doublon et 5 au plus", () => {
    for (const r of ["a1", "DEM", "dem", "b2", "c3", "d4", "e5"]) memoriserRecente(r);
    expect(lireRecentes()).toEqual(["e5", "d4", "c3", "b2", "dem"]);
    afficher();
    fireEvent.keyDown(document, { key: "k", metaKey: true });
    expect(screen.getByRole("group", { name: "Recherches récentes" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("option", { name: "e5" }));
    expect(screen.getByRole("combobox")).toHaveValue("e5");
  });

  it("les libellés existent en français et en anglais", () => {
    expect(Object.keys(en.rechercheGlobale).sort()).toEqual(
      Object.keys(fr.rechercheGlobale).sort(),
    );
    expect(Object.keys(en.aide.rubriques).sort()).toEqual(Object.keys(fr.aide.rubriques).sort());
    expect(Object.keys(en.mentionsLegales).sort()).toEqual(Object.keys(fr.mentionsLegales).sort());
  });
});
