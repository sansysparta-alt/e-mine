import type { PieceDto } from "@emine/api-client";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { StrictMode, useState } from "react";
import fr from "../../../messages/fr.json";
import { controlerFichier, SectionPieces, TAILLE_MAX_OCTETS } from "./section-pieces";

const eies: PieceDto = {
  id: 7,
  typePiece: "EIES",
  nomFichier: "eies.pdf",
  typeMime: "application/pdf",
  tailleOctets: 2048,
  empreinteSha256: "ab".repeat(32),
  deposeLe: "2026-11-20T10:00:00Z",
  remplaceeLe: null,
  remplaceeParId: null,
};

function reponse(statut: number, corps: unknown) {
  return new Response(JSON.stringify(corps), {
    status: statut,
    headers: { "content-type": "application/json" },
  });
}

function afficher(surChangement = vi.fn()) {
  render(
    <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
      <SectionPieces
        demandeId={41}
        piecesRequises={["Étude de faisabilité", "EIES", "Plan de restauration"]}
        surChangement={surChangement}
      />
    </NextIntlClientProvider>,
  );
  return surChangement;
}

/** Le bloc d'une pièce, retrouvé par son titre. */
const bloc = (nom: string) => screen.getByRole("region", { name: nom });

afterEach(() => vi.unstubAllGlobals());

describe("contrôles côté navigateur", () => {
  it("accepte PDF, JPG, PNG de 10 Mo au plus", () => {
    expect(controlerFichier({ name: "a.pdf", size: 10, type: "application/pdf" })).toBeNull();
    expect(controlerFichier({ name: "a.JPG", size: 10, type: "" })).toBeNull();
    expect(controlerFichier({ name: "a.exe", size: 10, type: "application/x-msdownload" })).toBe(
      "DOC-001",
    );
    expect(
      controlerFichier({ name: "a.pdf", size: TAILLE_MAX_OCTETS + 1, type: "application/pdf" }),
    ).toBe("DOC-002");
  });
});

describe("écran des pièces (correctifs J2 C5–C7)", () => {
  it("affiche un bloc par pièce attendue, plus « Autre pièce », avec son état", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => reponse(200, [eies])),
    );
    const surChangement = afficher();
    await waitFor(() => expect(surChangement).toHaveBeenCalledWith([eies]));
    for (const nom of ["Étude de faisabilité", "EIES", "Plan de restauration", "Autre pièce"]) {
      expect(bloc(nom)).toBeInTheDocument();
    }
    expect(within(bloc("EIES")).getByText("Fournie")).toBeInTheDocument();
    expect(within(bloc("EIES")).getAllByText("eies.pdf")[0]).toBeInTheDocument();
    expect(within(bloc("Plan de restauration")).getByText("Obligatoire")).toBeInTheDocument();
    expect(within(bloc("Autre pièce")).getByText("Facultative")).toBeInTheDocument();
  });

  it("envoie le fichier dès qu'il est choisi, sans bouton « Joindre »", async () => {
    const appels = vi
      .fn()
      .mockResolvedValueOnce(reponse(200, []))
      .mockResolvedValueOnce(reponse(201, eies));
    vi.stubGlobal("fetch", appels);
    const surChangement = afficher();
    await waitFor(() => expect(surChangement).toHaveBeenCalledWith([]));
    expect(screen.queryByRole("button", { name: /Joindre/ })).toBeNull();
    const fichier = new File(["%PDF-1.7"], "eies.pdf", { type: "application/pdf" });
    fireEvent.change(within(bloc("EIES")).getByLabelText("Fichier pour la pièce « EIES »"), {
      target: { files: [fichier] },
    });
    await waitFor(() => expect(surChangement).toHaveBeenLastCalledWith([eies]));
    const [url, init] = appels.mock.calls[1] as [string, RequestInit];
    expect(url).toBe("/api/demandes/41/pieces");
    expect((init.body as FormData).get("typePiece")).toBe("EIES");
    expect(within(bloc("EIES")).getByRole("status")).toHaveTextContent(
      "Pièce « eies.pdf » ajoutée.",
    );
  });

  it("accepte un fichier glissé-déposé sur la ligne de la pièce", async () => {
    const appels = vi
      .fn()
      .mockResolvedValueOnce(reponse(200, []))
      .mockResolvedValueOnce(reponse(201, { ...eies, typePiece: "Plan de restauration" }));
    vi.stubGlobal("fetch", appels);
    const surChangement = afficher();
    await waitFor(() => expect(surChangement).toHaveBeenCalledWith([]));
    const zone = bloc("Plan de restauration");
    fireEvent.dragOver(zone);
    expect(within(zone).getByText("Déposez le fichier pour cette pièce")).toBeInTheDocument();
    const fichier = new File(["%PDF-1.7"], "plan.pdf", { type: "application/pdf" });
    fireEvent.drop(zone, { dataTransfer: { files: [fichier] } });
    await waitFor(() => expect(appels).toHaveBeenCalledTimes(2));
    expect(
      ((appels.mock.calls[1] as [string, RequestInit])[1].body as FormData).get("typePiece"),
    ).toBe("Plan de restauration");
  });

  it("affiche l'erreur sur la pièce concernée seulement", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValueOnce(reponse(200, []))
        .mockResolvedValueOnce(reponse(415, { code: "DOC-001", message: "Format non accepté." })),
    );
    const surChangement = afficher();
    await waitFor(() => expect(surChangement).toHaveBeenCalledWith([]));
    const faux = new File(["texte"], "faux.pdf", { type: "application/pdf" });
    fireEvent.change(within(bloc("EIES")).getByLabelText(/EIES/), { target: { files: [faux] } });
    expect(await within(bloc("EIES")).findByRole("alert")).toHaveTextContent(
      "PDF, JPG ou PNG uniquement",
    );
    expect(within(bloc("Étude de faisabilité")).queryByRole("alert")).toBeNull();
  });

  it("refuse un fichier trop lourd sans l'envoyer, sur la pièce concernée", async () => {
    const appels = vi.fn(async () => reponse(200, []));
    vi.stubGlobal("fetch", appels);
    const surChangement = afficher();
    await waitFor(() => expect(surChangement).toHaveBeenCalledWith([]));
    const gros = new File(["x"], "gros.pdf", { type: "application/pdf" });
    Object.defineProperty(gros, "size", { value: TAILLE_MAX_OCTETS + 1 });
    fireEvent.change(within(bloc("Plan de restauration")).getByLabelText(/Plan de restauration/), {
      target: { files: [gros] },
    });
    expect(within(bloc("Plan de restauration")).getByRole("alert")).toHaveTextContent(
      "10 Mo au maximum",
    );
    expect(appels).toHaveBeenCalledTimes(1);
  });

  it("consulte une pièce dans le visualiseur et la retire", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValueOnce(reponse(200, [eies]))
        .mockResolvedValueOnce(new Response(null, { status: 204 })),
    );
    const surChangement = afficher();
    await waitFor(() => expect(surChangement).toHaveBeenCalledWith([eies]));
    fireEvent.click(within(bloc("EIES")).getByRole("button", { name: /Voir/ }));
    expect(document.querySelector("iframe")?.getAttribute("src")).toMatch(
      new RegExp(`^${echapper("/api/demandes/41/pieces/7/contenu?inline=1")}#toolbar=0`),
    );
    fireEvent.click(within(bloc("EIES")).getByRole("button", { name: /Retirer/ }));
    await waitFor(() => expect(surChangement).toHaveBeenLastCalledWith([]));
    expect(within(bloc("EIES")).getByText("Obligatoire")).toBeInTheDocument();
  });
});

describe("liste compacte (correctifs J4)", () => {
  it("annonce l'avancement des pièces obligatoires", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => reponse(200, [eies])),
    );
    const surChangement = afficher();
    await waitFor(() => expect(surChangement).toHaveBeenCalledWith([eies]));
    const barre = screen.getByRole("progressbar", { name: "Pièces obligatoires fournies" });
    expect(barre).toHaveAttribute("aria-valuenow", "1");
    expect(barre).toHaveAttribute("aria-valuemax", "3");
    expect(screen.getByText("1 sur 3 pièces obligatoires fournies")).toBeInTheDocument();
    expect(within(bloc("EIES")).getByText("Remplacer")).toBeInTheDocument();
    expect(within(bloc("Plan de restauration")).getByText("Ajouter")).toBeInTheDocument();
  });

  it("remplace le fichier d'une pièce obligatoire : le nouveau d'abord, puis l'ancien retiré", async () => {
    const nouveau = { ...eies, id: 8, nomFichier: "eies-v2.pdf" };
    const appels = vi
      .fn()
      .mockResolvedValueOnce(reponse(200, [eies]))
      .mockResolvedValueOnce(reponse(201, nouveau))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", appels);
    const surChangement = afficher();
    await waitFor(() => expect(surChangement).toHaveBeenCalledWith([eies]));
    const fichier = new File(["%PDF-1.7"], "eies-v2.pdf", { type: "application/pdf" });
    fireEvent.change(within(bloc("EIES")).getByLabelText("Fichier pour la pièce « EIES »"), {
      target: { files: [fichier] },
    });
    await waitFor(() => expect(surChangement).toHaveBeenLastCalledWith([nouveau]));
    expect((appels.mock.calls[1] as [string, RequestInit])[1].method).toBe("POST");
    expect(appels.mock.calls[2]).toEqual(["/api/demandes/41/pieces/7", { method: "DELETE" }]);
    expect(within(bloc("EIES")).getByRole("status")).toHaveTextContent(
      "Pièce remplacée par « eies-v2.pdf ».",
    );
  });
});

describe("intégration dans le formulaire (correctif de rendu)", () => {
  it("prévient le parent sans le mettre à jour pendant son propre rendu", async () => {
    const erreurs = vi.spyOn(console, "error").mockImplementation(() => undefined);
    vi.stubGlobal(
      "fetch",
      vi.fn(async (_url: string, init?: RequestInit) =>
        init?.method === "POST" ? reponse(201, eies) : reponse(200, []),
      ),
    );
    function Parent() {
      const [nombre, setNombre] = useState(0);
      return (
        <>
          <p>Pièces dans le parent : {nombre}</p>
          <SectionPieces
            demandeId={41}
            piecesRequises={["EIES"]}
            surChangement={(l) => setNombre(l.length)}
          />
        </>
      );
    }
    render(
      <StrictMode>
        <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
          <Parent />
        </NextIntlClientProvider>
      </StrictMode>,
    );
    await screen.findByText("Pièces dans le parent : 0");
    const fichier = new File(["%PDF-1.7"], "eies.pdf", { type: "application/pdf" });
    fireEvent.change(screen.getByLabelText("Fichier pour la pièce « EIES »"), {
      target: { files: [fichier] },
    });
    expect(await screen.findByText("Pièces dans le parent : 1")).toBeInTheDocument();
    const messages = erreurs.mock.calls.map((c) => String(c[0]));
    expect(messages.filter((m) => m.includes("Cannot update a component"))).toEqual([]);
    erreurs.mockRestore();
  });
});

/** Texte pris littéralement dans une expression régulière. */
function echapper(texte: string): string {
  return texte.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
