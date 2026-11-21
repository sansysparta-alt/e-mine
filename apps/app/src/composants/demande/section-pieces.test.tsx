import type { PieceDto } from "@emine/api-client";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
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
        piecesRequises={["Étude de faisabilité", "EIES"]}
        surChangement={surChangement}
      />
    </NextIntlClientProvider>,
  );
  return surChangement;
}

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

describe("section Pièces", () => {
  it("liste les pièces et indique celles qui manquent", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => reponse(200, [eies])),
    );
    const surChangement = afficher();
    expect(await screen.findByRole("link", { name: "eies.pdf" })).toHaveAttribute(
      "href",
      "/api/demandes/41/pieces/7/contenu",
    );
    expect(screen.getByText("✓ EIES : fournie")).toBeInTheDocument();
    expect(screen.getByText("○ Étude de faisabilité : à joindre")).toBeInTheDocument();
    expect(surChangement).toHaveBeenCalledWith([eies]);
  });

  it("refuse un fichier trop lourd sans l'envoyer", async () => {
    const appels = vi.fn(async () => reponse(200, []));
    vi.stubGlobal("fetch", appels);
    afficher();
    await screen.findByText("Aucune pièce jointe pour le moment.");
    const gros = new File(["x"], "gros.pdf", { type: "application/pdf" });
    Object.defineProperty(gros, "size", { value: TAILLE_MAX_OCTETS + 1 });
    fireEvent.change(screen.getByLabelText("Fichier"), { target: { files: [gros] } });
    fireEvent.click(screen.getByRole("button", { name: "Joindre le fichier" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("10 Mo au maximum");
    expect(appels).toHaveBeenCalledTimes(1); // seulement le chargement de la liste
  });

  it("envoie le fichier par le relais BFF et l'ajoute à la liste", async () => {
    const appels = vi
      .fn()
      .mockResolvedValueOnce(reponse(200, []))
      .mockResolvedValueOnce(reponse(201, eies));
    vi.stubGlobal("fetch", appels);
    const surChangement = afficher();
    await screen.findByText("Aucune pièce jointe pour le moment.");
    fireEvent.change(screen.getByLabelText("Cette pièce correspond à"), {
      target: { value: "EIES" },
    });
    const fichier = new File(["%PDF-1.7"], "eies.pdf", { type: "application/pdf" });
    fireEvent.change(screen.getByLabelText("Fichier"), { target: { files: [fichier] } });
    fireEvent.click(screen.getByRole("button", { name: "Joindre le fichier" }));
    await waitFor(() => expect(surChangement).toHaveBeenLastCalledWith([eies]));
    const [url, init] = appels.mock.calls[1] as [string, RequestInit];
    expect(url).toBe("/api/demandes/41/pieces");
    expect(init.method).toBe("POST");
    expect((init.body as FormData).get("typePiece")).toBe("EIES");
    expect(screen.getByRole("status")).toHaveTextContent("Pièce « eies.pdf » ajoutée.");
  });

  it("affiche le refus de l'API", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValueOnce(reponse(200, []))
        .mockResolvedValueOnce(reponse(415, { code: "DOC-001", message: "Format non accepté." })),
    );
    afficher();
    await screen.findByText("Aucune pièce jointe pour le moment.");
    const fichier = new File(["MZ"], "faux.pdf", { type: "application/pdf" });
    fireEvent.change(screen.getByLabelText("Fichier"), { target: { files: [fichier] } });
    fireEvent.click(screen.getByRole("button", { name: "Joindre le fichier" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("PDF, JPG ou PNG uniquement");
  });

  it("retire une pièce", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValueOnce(reponse(200, [eies]))
        .mockResolvedValueOnce(new Response(null, { status: 204 })),
    );
    afficher();
    fireEvent.click(await screen.findByRole("button", { name: "Retirer la pièce eies.pdf" }));
    expect(await screen.findByText("Aucune pièce jointe pour le moment.")).toBeInTheDocument();
  });
});
