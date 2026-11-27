import type { LigneReleveDto } from "@emine/api-client";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import fr from "../../../messages/fr.json";
import { controlerReleve, ImportReleve } from "./import-releve";
import { TableauLignesReleve, tonLigneReleve } from "./tableaux-releve";

const push = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push, refresh: vi.fn() }) }));

function afficher(enfant: ReactNode) {
  return render(
    <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
      {enfant}
    </NextIntlClientProvider>,
  );
}

function ligne(partiel: Partial<LigneReleveDto>): LigneReleveDto {
  return {
    id: 1,
    numero: 2,
    dateOperation: "2026-11-26",
    libelle: "VIR HORIZON PAY-2026-000123",
    montantGnf: 5_000_000,
    sens: "C",
    statut: "rapprochee",
    nature: "conforme",
    referenceLue: "PAY-2026-000123",
    ecartGnf: 0,
    referencePaiement: "PAY-2026-000123",
    demandeId: 42,
    numeroDemande: "DEM-2026-000042",
    ...partiel,
  };
}

describe("import d'un relevé bancaire (US-04.8)", () => {
  beforeEach(() => {
    push.mockReset();
    vi.restoreAllMocks();
  });

  it("n'accepte qu'un CSV de 2 Mo au plus", () => {
    expect(controlerReleve({ name: "releve.csv", size: 100 })).toBeNull();
    expect(controlerReleve({ name: "releve.xlsx", size: 100 })).toBe("format");
    expect(controlerReleve({ name: "releve.csv", size: 2 * 1024 * 1024 + 1 })).toBe("taille");
  });

  it("exige le fichier, puis importe et ouvre le relevé", async () => {
    const appel = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response(JSON.stringify({ id: 7 }), { status: 201 }));
    afficher(<ImportReleve exempleDisponible={false} />);
    fireEvent.click(screen.getByRole("button", { name: "Importer et rapprocher" }));
    expect(screen.getByText("Choisissez le fichier du relevé.")).toBeVisible();
    expect(appel).not.toHaveBeenCalled();
    const csv = new File(["date_operation;libelle;montant_gnf;sens\n"], "releve.csv", {
      type: "text/csv",
    });
    fireEvent.change(screen.getByLabelText("Relevé bancaire (CSV)"), { target: { files: [csv] } });
    fireEvent.change(screen.getByLabelText("Banque"), { target: { value: "Banque de test" } });
    fireEvent.click(screen.getByRole("button", { name: "Importer et rapprocher" }));
    await waitFor(() => expect(push).toHaveBeenCalledWith("/releves/7?importe=1"));
    const corps = (appel.mock.calls[0] as [string, RequestInit])[1].body as FormData;
    expect(corps.get("fichier")).toBe(csv);
    expect(corps.get("banque")).toBe("Banque de test");
    expect(corps.get("dateReleve")).toBeNull();
    expect(screen.queryByText("Télécharger un relevé d’exemple")).toBeNull();
  });

  it("cite les lignes refusées par le contrôle de format", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify({
          code: "VAL-001",
          message: "Données invalides.",
          details: { champs: [{ champ: "fichier", message: "Ligne 3 : Date invalide." }] },
        }),
        { status: 400 },
      ),
    );
    afficher(<ImportReleve exempleDisponible />);
    fireEvent.change(screen.getByLabelText("Relevé bancaire (CSV)"), {
      target: { files: [new File(["x"], "faux.csv", { type: "text/csv" })] },
    });
    fireEvent.click(screen.getByRole("button", { name: "Importer et rapprocher" }));
    expect(await screen.findByText("Ligne 3 : Date invalide.")).toBeVisible();
    expect(push).not.toHaveBeenCalled();
    expect(screen.getByRole("link", { name: /relevé d’exemple/ })).toHaveAttribute(
      "href",
      "/api/releves/exemple",
    );
  });
});

describe("lignes d'un relevé (US-04.8)", () => {
  it("donne un seul badge et le résultat en clair", () => {
    afficher(
      <TableauLignesReleve
        lignes={[
          ligne({}),
          ligne({ id: 2, numero: 3, statut: "ecart", nature: "inferieur", ecartGnf: -1_000_000 }),
          ligne({
            id: 3,
            numero: 4,
            statut: "inconnue",
            nature: "inconnue",
            referencePaiement: null,
            demandeId: null,
            numeroDemande: null,
          }),
        ]}
      />,
    );
    const rangees = screen.getAllByRole("row").slice(1);
    expect(within(rangees[0]!).getByText("Rapprochée")).toBeVisible();
    expect(within(rangees[0]!).getByText("Virement confirmé")).toBeVisible();
    expect(within(rangees[0]!).getByRole("link", { name: "DEM-2026-000042" })).toHaveAttribute(
      "href",
      "/demandes/42",
    );
    expect(within(rangees[1]!).getByText("À vérifier")).toBeVisible();
    expect(within(rangees[1]!).getByText(/Montant inférieur de 1\s000\s000\sGNF/)).toBeVisible();
    expect(within(rangees[2]!).getByText("Aucun virement ne correspond")).toBeVisible();
  });

  it("colore en orange ce qui attend la décision du Responsable fiscal", () => {
    expect(tonLigneReleve("rapprochee")).toBe("succes");
    expect(tonLigneReleve("ecart")).toBe("avertissement");
    expect(tonLigneReleve("inconnue")).toBe("avertissement");
  });
});
