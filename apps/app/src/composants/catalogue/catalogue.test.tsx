import type { LigneFicheDto, VersionDto } from "@emine/api-client";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import en from "../../../messages/en.json";
import fr from "../../../messages/fr.json";
import { DecisionVersion } from "./decision-version";
import { baremeApprouve, FormulaireFiche, lignesPieces, schemaFiche } from "./formulaire-fiche";
import { lireParametresCatalogue, presentationLigne, presentationVersion } from "./presentation";
import { TableauFiches } from "./tableau-fiches";

const push = vi.fn();
const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push, refresh }) }));

function avecTraductions(enfant: React.ReactNode) {
  return render(
    <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
      {enfant}
    </NextIntlClientProvider>,
  );
}

beforeEach(() => {
  push.mockReset();
  refresh.mockReset();
});

describe("présentation des versions (US-02.2)", () => {
  it("orange pour le Ministre qui doit approuver, gris pour l'administrateur qui attend", () => {
    expect(presentationVersion("a_valider", "DECIDER")).toEqual({
      ton: "avertissement",
      cle: "aApprouver",
    });
    expect(presentationVersion("a_valider", "ATTENDRE")).toEqual({
      ton: "neutre",
      cle: "enAttenteMinistre",
    });
    expect(presentationVersion("publiee", "AUCUNE").ton).toBe("succes");
    expect(presentationVersion("refusee", "AUCUNE").ton).toBe("danger");
    expect(presentationVersion("retiree", "AUCUNE").ton).toBe("neutre");
    expect(presentationVersion("remplacee", "AUCUNE").ton).toBe("neutre");
  });

  it("un seul badge par ligne : la proposition prime, puis le retrait du catalogue", () => {
    expect(presentationLigne({ actif: false, numeroProposee: 3, action: "DECIDER" }).cle).toBe(
      "aApprouver",
    );
    expect(presentationLigne({ actif: false, numeroProposee: null, action: "AUCUNE" }).cle).toBe(
      "inactif",
    );
    expect(presentationLigne({ actif: true, action: "AUCUNE" }).cle).toBe("enVigueur");
  });

  it("chaque libellé de statut existe en français et en anglais", () => {
    for (const messages of [fr, en]) {
      for (const cle of [
        "aApprouver",
        "enAttenteMinistre",
        "enVigueur",
        "refusee",
        "retiree",
        "remplacee",
        "inactif",
      ]) {
        expect(messages.catalogue.statuts).toHaveProperty(cle);
      }
    }
  });

  it("les paramètres d'adresse inconnus sont ignorés", () => {
    expect(lireParametresCatalogue({ filtre: "PIRATE", page: "-2", taille: "7" })).toEqual({
      filtre: "TOUS",
      q: undefined,
      page: 1,
      taille: 20,
    });
    expect(lireParametresCatalogue({ filtre: "A_VALIDER", q: "  pex " }).q).toBe("pex");
  });
});

const ligne: LigneFicheDto = {
  code: "PEX",
  libelleFr: "Permis d'exploitation",
  famille: "Titres miniers",
  familleCode: "TITRES",
  fraisDossierGnf: 5_000_000,
  droitsGnf: 15_000_000,
  attestationGnf: null,
  delaiJours: 60,
  signataire: "Ministre",
  actif: true,
  numeroEnVigueur: 2,
  numeroProposee: 3,
  proposeeLe: "2026-11-20T10:00:00Z",
  action: "DECIDER",
};

describe("liste du catalogue", () => {
  it("la ligne entière ouvre la fiche, avec un seul badge", () => {
    avecTraductions(<TableauFiches lignes={[ligne]} legende="Catalogue" />);
    expect(screen.getByRole("link", { name: /PEX · Permis d'exploitation/ })).toHaveAttribute(
      "href",
      "/catalogue/PEX",
    );
    expect(screen.getAllByText("À valider")).toHaveLength(1);
    expect(screen.getAllByText("60 jours").length).toBeGreaterThan(0);
  });
});

describe("décision sur une proposition (US-02.4)", () => {
  it("le refus exige un motif, puis est envoyé avec lui", async () => {
    const decider = vi.fn().mockResolvedValue({ ok: true, valeur: null });
    avecTraductions(
      <DecisionVersion
        code="PEX"
        numero={3}
        action="DECIDER"
        changeSignataire={false}
        desactive={false}
        decider={decider}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: "Refuser" }));
    fireEvent.click(screen.getByRole("button", { name: "Confirmer le refus" }));
    expect(decider).not.toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText(/Motif du refus/), {
      target: { value: "Délai incompatible avec l'arrêté" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Confirmer le refus" }));
    await waitFor(() =>
      expect(decider).toHaveBeenCalledWith("PEX", 3, "refuser", "Délai incompatible avec l'arrêté"),
    );
    await waitFor(() => expect(push).toHaveBeenCalledWith("/catalogue/PEX?resultat=refuser"));
  });

  it("un changement de signataire est signalé avant l'approbation", () => {
    avecTraductions(
      <DecisionVersion
        code="PEX"
        numero={3}
        action="DECIDER"
        changeSignataire
        desactive={false}
        decider={vi.fn()}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: "Valider" }));
    expect(screen.getByText("Le signataire change")).toBeInTheDocument();
  });

  it("l'administrateur ne voit que le retrait de sa proposition", () => {
    avecTraductions(
      <DecisionVersion
        code="PEX"
        numero={3}
        action="ATTENDRE"
        changeSignataire={false}
        desactive={false}
        decider={vi.fn()}
      />,
    );
    expect(screen.queryByRole("button", { name: "Valider" })).toBeNull();
    expect(screen.getByRole("button", { name: "Retirer la proposition" })).toBeInTheDocument();
  });
});

const enVigueur: VersionDto = {
  numero: 2,
  statut: "publiee",
  libelleFr: "Permis d'exploitation",
  libelleEn: null,
  pieces: ["Étude de faisabilité", "EIES"],
  delaiJours: 60,
  instruitPar: "DNM + Inspection",
  signataire: "Ministre",
  avis: [{ code: "DNM", libelle: "Agent DNM" }],
  actif: true,
  fraisDossierGnf: 5_000_000,
  droitsGnf: 15_000_000,
  attestationGnf: null,
  tarifsDateEffet: null,
  motif: "Version initiale",
  proposeePar: null,
  proposeeLe: "2026-01-01T00:00:00Z",
  decideePar: null,
  decideeLe: "2026-01-01T00:00:00Z",
  motifRefus: null,
  changements: [],
};

describe("proposition d'une modification (US-02.2)", () => {
  it("les pièces se saisissent une par ligne, sans doublon ni ligne vide", () => {
    expect(lignesPieces(" EIES \n\nEIES\nPlan  de restauration\n")).toEqual([
      "EIES",
      "Plan de restauration",
    ]);
    const base = {
      libelleFr: "Permis d'exploitation",
      libelleEn: "",
      delaiJours: "60",
      instruitPar: "DNM",
      signataire: "Ministre",
      avis: [],
      actif: true,
      motif: "Motif suffisamment long",
      fraisDossierGnf: "5000000",
      droitsGnf: "",
      attestationGnf: "",
      tarifsDateEffet: "",
    };
    expect(schemaFiche.safeParse({ ...base, pieces: "EIES" }).success).toBe(true);
    expect(schemaFiche.safeParse({ ...base, pieces: "EIES, statuts" }).success).toBe(false);
    expect(schemaFiche.safeParse({ ...base, pieces: "\n \n" }).success).toBe(false);
    expect(schemaFiche.safeParse({ ...base, pieces: "EIES", delaiJours: "0" }).success).toBe(false);
  });

  it("le formulaire est pré-rempli, exige un motif et envoie la proposition", async () => {
    const proposer = vi.fn().mockResolvedValue({ ok: true, valeur: { numero: 3 } });
    avecTraductions(
      <FormulaireFiche
        code="PEX"
        enVigueur={enVigueur}
        aujourdhui="2026-11-23"
        signataires={["Ministre", "Directeur général"]}
        profilsAvis={[
          { code: "DNM", libelle: "Agent DNM" },
          { code: "GEOLOGUE", libelle: "Géologue (DNG)" },
        ]}
        proposer={proposer}
      />,
    );
    expect(screen.getByLabelText(/Délai cible/)).toHaveValue(60);
    fireEvent.click(screen.getByRole("button", { name: "Soumettre au Ministre" }));
    expect(
      await screen.findByText("Expliquez la modification en 10 à 2 000 caractères."),
    ).toBeInTheDocument();
    expect(proposer).not.toHaveBeenCalled();

    fireEvent.change(screen.getByLabelText(/Délai cible/), { target: { value: "45" } });
    fireEvent.change(screen.getByLabelText(/Signataire/), {
      target: { value: "Directeur général" },
    });
    expect(screen.getByText("Changement de signataire")).toBeInTheDocument();
    fireEvent.click(screen.getByLabelText("Géologue (DNG)"));
    fireEvent.change(screen.getByLabelText(/Objet de la modification/), {
      target: { value: "Arrêté du 20/11/2026 : délai réduit" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Soumettre au Ministre" }));
    await waitFor(() =>
      expect(proposer).toHaveBeenCalledWith(
        "PEX",
        expect.objectContaining({
          delaiJours: 45,
          signataire: "Directeur général",
          avis: ["DNM", "GEOLOGUE"],
          pieces: ["Étude de faisabilité", "EIES"],
          actif: true,
          libelleEn: null,
          fraisDossierGnf: 5_000_000,
          droitsGnf: 15_000_000,
          attestationGnf: null,
          tarifsDateEffet: null,
        }),
      ),
    );
    expect(push).toHaveBeenCalledWith("/catalogue/PEX?proposee=3");
  });
});

describe("tableau de bord du Ministre (US-02.4)", () => {
  it("annonce les modifications du catalogue à approuver et ouvre la liste filtrée", async () => {
    const { VueTableauDeBord } = await import("../vue-tableau-de-bord");
    avecTraductions(
      <VueTableauDeBord
        prenom="Awa"
        nom="Test"
        profilLibelle="Ministre"
        profil="autre"
        catalogueAApprouver={2}
      />,
    );
    expect(screen.getByText("2 modifications du catalogue à valider")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Voir les modifications" })).toHaveAttribute(
      "href",
      "/catalogue?filtre=A_VALIDER",
    );
  });
});

describe("tarifs versionnés (US-02.3)", () => {
  function formulaire(proposer = vi.fn().mockResolvedValue({ ok: true, valeur: { numero: 4 } })) {
    avecTraductions(
      <FormulaireFiche
        code="PEX"
        enVigueur={enVigueur}
        tarifsAVenir={[{ objet: "DROITS", montantGnf: 16_000_000, dateEffet: "2026-12-01" }]}
        aujourdhui="2026-11-23"
        signataires={["Ministre"]}
        profilsAvis={[{ code: "DNM", libelle: "Agent DNM" }]}
        proposer={proposer}
      />,
    );
    return proposer;
  }

  it("le barème de référence reprend un tarif approuvé à venir", () => {
    expect(
      baremeApprouve(enVigueur, [
        { objet: "DROITS", montantGnf: 16_000_000, dateEffet: "2026-12-01" },
      ]),
    ).toEqual({ fraisDossierGnf: 5_000_000, droitsGnf: 16_000_000, attestationGnf: null });
    formulaire();
    expect(screen.getByLabelText(/^Droits/)).toHaveValue("16000000");
    expect(screen.queryByLabelText(/Paiement unique/)).toBeNull();
    expect(screen.queryByLabelText(/Entrée en vigueur des tarifs/)).toBeNull();
  });

  it("un montant changé exige une date d'entrée en vigueur, envoyée avec la proposition", async () => {
    const proposer = formulaire();
    fireEvent.change(screen.getByLabelText(/^Frais de dossier/), { target: { value: "6000000" } });
    fireEvent.change(screen.getByLabelText(/Objet de la modification/), {
      target: { value: "Arrêté de test : frais revus" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Soumettre au Ministre" }));
    expect(
      await screen.findByText("Choisissez une date d’entrée en vigueur à partir d’aujourd’hui."),
    ).toBeInTheDocument();
    expect(proposer).not.toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText(/Entrée en vigueur des tarifs/), {
      target: { value: "2026-12-15" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Soumettre au Ministre" }));
    await waitFor(() =>
      expect(proposer).toHaveBeenCalledWith(
        "PEX",
        expect.objectContaining({
          fraisDossierGnf: 6_000_000,
          droitsGnf: 16_000_000,
          tarifsDateEffet: "2026-12-15",
        }),
      ),
    );
  });
});
