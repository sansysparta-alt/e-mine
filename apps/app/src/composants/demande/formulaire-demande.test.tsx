import type { DemandeDto, TypeDocumentDto } from "@emine/api-client";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import fr from "../../../messages/fr.json";
import { DELAI_ENREGISTREMENT_MS, FormulaireDemande } from "./formulaire-demande";
import { depuisDemande, schemaBrouillon, versBrouillonDto } from "./schema";

const pex: TypeDocumentDto = {
  code: "PEX",
  libelleFr: "Permis d'exploitation",
  libelleEn: null,
  famille: { code: "TITRES", libelle: "Titres miniers" },
  delaiJours: 60,
  renouvelable: true,
  piecesRequises: ["Étude de faisabilité", "EIES", "Plan de restauration"],
  instruitPar: "DNM + Inspection",
  signataire: "Ministre",
  tarifs: {
    fraisDossierGnf: 5_000_000,
    droitsGnf: 15_000_000,
    attestationGnf: null,
    aPayerAuDepotGnf: 5_000_000,
  },
};

function demande(partiel: Partial<DemandeDto> = {}): DemandeDto {
  return {
    id: 41,
    numero: null,
    statut: "brouillon",
    typeDocumentCode: "PEX",
    demandeur: { prenom: "Fodé", nom: "Bangoura", email: "f@x.test", organisation: "Horizon" },
    prefectureId: null,
    prefectureNom: null,
    substanceCode: null,
    substanceLibelle: null,
    superficieHa: null,
    creeLe: "2026-11-20T10:00:00Z",
    deposeLe: null,
    ...partiel,
  };
}

function afficher(props: Partial<Parameters<typeof FormulaireDemande>[0]> = {}) {
  const enregistrer = vi.fn(async () => ({ ok: true as const, valeur: demande() }));
  render(
    <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
      <FormulaireDemande
        demande={null}
        demandeur={{ prenom: "Fodé", nom: "Bangoura", email: "f@x.test", organisation: "Horizon" }}
        documents={[pex]}
        prefectures={[{ id: 1, region: "Conakry", nom: "Conakry" }]}
        substances={[{ code: "OR", libelleFr: "Or", libelleEn: "Gold" }]}
        enregistrer={enregistrer}
        {...props}
      />
    </NextIntlClientProvider>,
  );
  return { enregistrer: (props.enregistrer as typeof enregistrer | undefined) ?? enregistrer };
}

describe("schéma du brouillon", () => {
  it("convertit la saisie en DTO de l'API", () => {
    expect(
      versBrouillonDto({
        typeDocumentCode: "PEX",
        prefectureId: "3",
        substanceCode: "",
        superficieHa: "125,5",
      }),
    ).toEqual({
      typeDocumentCode: "PEX",
      prefectureId: 3,
      substanceCode: null,
      superficieHa: 125.5,
    });
  });

  it("refuse une superficie négative ou à trois décimales", () => {
    const base = { typeDocumentCode: "PEX", prefectureId: "", substanceCode: "" };
    expect(schemaBrouillon.safeParse({ ...base, superficieHa: "-1" }).success).toBe(false);
    expect(schemaBrouillon.safeParse({ ...base, superficieHa: "1,234" }).success).toBe(false);
    expect(schemaBrouillon.safeParse({ ...base, superficieHa: "" }).success).toBe(true);
  });

  it("reprend un brouillon existant", () => {
    expect(depuisDemande(demande({ prefectureId: 2, superficieHa: 12.5 }))).toEqual({
      typeDocumentCode: "PEX",
      prefectureId: "2",
      substanceCode: "",
      superficieHa: "12,5",
    });
  });
});

describe("formulaire guidé", () => {
  it("affiche d'abord le demandeur, puis le document avec délai, frais et pièces", async () => {
    afficher();
    expect(screen.getByRole("heading", { level: 2, name: "Demandeur" })).toBeInTheDocument();
    expect(screen.getByText("Horizon")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Enregistrer et continuer" }));
    fireEvent.change(await screen.findByLabelText("Document demandé"), {
      target: { value: "PEX" },
    });
    expect(screen.getByText(/Délai de traitement : 60 jours/)).toBeInTheDocument();
    expect(screen.getByText(/5\s000\s000\sGNF/)).toBeInTheDocument();
    expect(screen.getByText("EIES")).toBeInTheDocument();
  });

  it("exige un document avant d'enregistrer", async () => {
    const { enregistrer } = afficher();
    fireEvent.click(screen.getByRole("button", { name: "Enregistrer et continuer" }));
    await screen.findByLabelText("Document demandé");
    fireEvent.click(screen.getByRole("button", { name: "Enregistrer et continuer" }));
    expect(await screen.findByText("Choisissez le document demandé.")).toBeInTheDocument();
    expect(enregistrer).not.toHaveBeenCalled();
  });

  it("enregistre le brouillon en quittant la section Document", async () => {
    const { enregistrer } = afficher({ typeInitial: "PEX" });
    fireEvent.click(screen.getByRole("button", { name: "Enregistrer et continuer" }));
    fireEvent.change(await screen.findByLabelText(/Superficie/), { target: { value: "125,5" } });
    fireEvent.click(screen.getByRole("button", { name: "Enregistrer et continuer" }));
    await waitFor(() =>
      expect(enregistrer).toHaveBeenCalledWith(null, {
        typeDocumentCode: "PEX",
        prefectureId: "",
        substanceCode: "",
        superficieHa: "125,5",
      }),
    );
    expect(await screen.findByRole("heading", { level: 2, name: "Pièces" })).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(/Brouillon enregistré à/);
  });

  it("affiche l'erreur de l'API sans changer de section", async () => {
    const enregistrer = vi.fn(async () => ({
      ok: false as const,
      erreur: { code: "DEM-003", message: "Cette demande a déjà été déposée.", champs: [] },
    }));
    afficher({ demande: demande(), enregistrer });
    fireEvent.change(screen.getByLabelText(/Superficie/), { target: { value: "3" } });
    fireEvent.click(screen.getByRole("button", { name: "Enregistrer et continuer" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Cette demande a déjà été déposée.");
    expect(screen.getByRole("heading", { level: 2, name: "Document" })).toBeInTheDocument();
  });

  it("enregistre automatiquement un brouillon modifié toutes les 30 secondes", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    try {
      const { enregistrer } = afficher({ demande: demande() });
      fireEvent.change(screen.getByLabelText(/Superficie/), { target: { value: "42" } });
      await act(async () => {
        await vi.advanceTimersByTimeAsync(DELAI_ENREGISTREMENT_MS + 100);
      });
      await waitFor(() =>
        expect(enregistrer).toHaveBeenCalledWith(
          41,
          expect.objectContaining({ superficieHa: "42" }),
        ),
      );
    } finally {
      vi.useRealTimers();
    }
  });

  it("n'enregistre rien automatiquement si rien n'a changé", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    try {
      const { enregistrer } = afficher({ demande: demande() });
      await act(async () => {
        await vi.advanceTimersByTimeAsync(DELAI_ENREGISTREMENT_MS * 2);
      });
      expect(enregistrer).not.toHaveBeenCalled();
    } finally {
      vi.useRealTimers();
    }
  });
});
