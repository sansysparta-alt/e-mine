import type { DocumentDelivreDto } from "@emine/api-client";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import fr from "../../../messages/fr.json";
import { DocumentDelivre } from "./document-delivre";
import { SignerDelivrer } from "./signer-delivrer";

const push = vi.fn();
const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push, refresh }) }));

function afficher(enfant: React.ReactNode) {
  return render(
    <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
      {enfant}
    </NextIntlClientProvider>,
  );
}

const document: DocumentDelivreDto = {
  numero: "PEX-2026-0001",
  codeType: "PEX",
  valide: true,
  motifInvalidation: null,
  revoqueLe: null,
  delivreLe: "2026-11-22T09:30:00Z",
  empreinteSha256: "ab".repeat(32),
  urlVerification: "http://localhost:3000/verifier/0123456789abcdef0123456789abcdef",
  signataire: "Ibrahima Soumah",
};

beforeEach(() => {
  push.mockReset();
  refresh.mockReset();
});

describe("signer et délivrer (US-06.3)", () => {
  it("explique les étapes, confirme puis délivre et revient à l'onglet « À signer »", async () => {
    const signer = vi.fn().mockResolvedValue({ ok: true, valeur: document });
    afficher(
      <SignerDelivrer
        demandeId={41}
        numero="DEM-2026-000041"
        document="Permis d'exploitation"
        signataire="Ministre"
        signerEtDelivrer={signer}
      />,
    );
    expect(screen.getByText(/signé électroniquement \(PAdES\)/)).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Signer et délivrer…" }));
    expect(signer).not.toHaveBeenCalled();
    expect(screen.getByText(/ne peut plus être modifié/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Signer et délivrer" }));
    await waitFor(() => expect(signer).toHaveBeenCalledWith(41));
    await waitFor(() =>
      expect(push).toHaveBeenCalledWith(
        "/a-traiter?groupe=a-signer&traitee=DEM-2026-000041&resultat=delivree&document=PEX-2026-0001",
      ),
    );
  });

  it("affiche le refus (signature indisponible) sans quitter le dossier", async () => {
    afficher(
      <SignerDelivrer
        demandeId={41}
        numero="DEM-2026-000041"
        document="Permis d'exploitation"
        signataire="Ministre"
        signerEtDelivrer={vi.fn().mockResolvedValue({
          ok: false,
          erreur: {
            code: "DOC-004",
            message: "Le service de signature est indisponible.",
            champs: [],
          },
        })}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: "Signer et délivrer…" }));
    fireEvent.click(screen.getByRole("button", { name: "Signer et délivrer" }));
    expect(await screen.findByText("Le service de signature est indisponible.")).toBeVisible();
    expect(push).not.toHaveBeenCalled();
  });
});

describe("document délivré (US-06.4)", () => {
  it("s'ouvre dans le visualiseur, se télécharge depuis le panneau, se vérifie publiquement", () => {
    afficher(<DocumentDelivre demandeId={41} document={document} />);
    expect(screen.getByText("Valide")).toBeVisible();
    expect(screen.getByText("Ibrahima Soumah")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Voir le document" }));
    expect(globalThis.document.querySelector("iframe")).toHaveAttribute(
      "src",
      "/api/demandes/41/document/contenu?inline=1",
    );
    expect(screen.getByRole("link", { name: /Télécharger/ })).toHaveAttribute(
      "href",
      "/api/demandes/41/document/contenu",
    );
    const verification = screen.getByRole("link", { name: /Page de vérification/ });
    expect(verification).toHaveAttribute("href", document.urlVerification);
    expect(verification).toHaveAttribute("target", "_blank");
    expect(verification).toHaveAttribute("rel", "noopener noreferrer");
  });

  it("montre « Révoqué », la date et le motif d'un document révoqué, et avertit à l'ouverture", () => {
    afficher(
      <DocumentDelivre
        demandeId={41}
        document={{
          ...document,
          valide: false,
          motifInvalidation: "Fraude constatée.",
          revoqueLe: "2026-11-20T10:00:00Z",
        }}
      />,
    );
    expect(screen.getByRole("alert")).toHaveTextContent("Ce document a été révoqué");
    expect(screen.getByRole("alert")).toHaveTextContent("le 20/11/2026");
    expect(screen.getByText("Révoqué")).toBeVisible();
    expect(screen.getByText("Fraude constatée.")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: /Voir le document/ }));
    expect(
      screen.getByText("Document révoqué le 20/11/2026 : il n’est plus valide."),
    ).toBeVisible();
  });
});

describe("révocation (US-06.6)", () => {
  it("exige un motif puis révoque et annonce le résultat", async () => {
    const { RevoquerDocument } = await import("./revoquer-document");
    const revoquer = vi.fn().mockResolvedValue({
      ok: true,
      valeur: { ...document, valide: false, motifInvalidation: "Fraude constatée." },
    });
    afficher(<RevoquerDocument demandeId={41} numero="PEX-2026-0001" revoquer={revoquer} />);
    fireEvent.click(screen.getByRole("button", { name: "Révoquer…" }));
    fireEvent.click(screen.getByRole("button", { name: "Révoquer définitivement" }));
    expect(revoquer).not.toHaveBeenCalled();
    const motif = screen.getByLabelText(/Motif de la révocation/);
    expect(motif).toHaveAttribute("aria-invalid", "true");
    fireEvent.change(motif, { target: { value: "  Fraude constatée.  " } });
    fireEvent.click(screen.getByRole("button", { name: "Révoquer définitivement" }));
    await waitFor(() => expect(revoquer).toHaveBeenCalledWith(41, "  Fraude constatée.  "));
    expect(await screen.findByRole("status")).toHaveTextContent("NON VALIDE");
    expect(refresh).toHaveBeenCalled();
  });
});

describe("profils de révocation (module sans « use client »)", () => {
  it("réserve la révocation au Directeur et à la Brigade internes", async () => {
    const { peutRevoquer } = await import("./revocation");
    expect(peutRevoquer({ interne: true, profil: { code: "DIRECTEUR" } })).toBe(true);
    expect(peutRevoquer({ interne: true, profil: { code: "BRIGADE" } })).toBe(true);
    expect(peutRevoquer({ interne: true, profil: { code: "MINISTRE" } })).toBe(false);
    expect(peutRevoquer({ interne: false, profil: { code: "DIRECTEUR" } })).toBe(false);
  });
});
