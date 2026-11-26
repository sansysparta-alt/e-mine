import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import fr from "../../../messages/fr.json";
import { actionAttendue as actionListe } from "../demande/actions-demande";
import { prochaineEtape } from "../demande/prochaine-etape";
import { cleAttente, presentationStatut } from "../demande/statut";
import { actionAttendue as actionTableauDeBord } from "../vue-tableau-de-bord";
import { CoordonneesVirement } from "./coordonnees-virement";
import { controlerJustificatif, DeclarationVirement } from "./declaration-virement";
import { presentationStatutPaiement, profilPaiement, referenceVirementExpiree } from "./statut";

const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh, push: vi.fn() }) }));

function afficher(enfant: ReactNode) {
  return render(
    <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
      {enfant}
    </NextIntlClientProvider>,
  );
}

describe("statut d'un paiement selon le profil (lot 5)", () => {
  it("dit à l'usager d'effectuer son virement, puis qu'il est vérifié", () => {
    expect(presentationStatutPaiement({ statut: "initie", moyen: "VIREMENT" }, "usager")).toEqual({
      cle: "virementAEffectuer",
      ton: "avertissement",
    });
    expect(
      presentationStatutPaiement({ statut: "en_attente", moyen: "VIREMENT" }, "usager"),
    ).toEqual({ cle: "enVerification", ton: "neutre" });
    // « À vérifier » n'est une action que pour le service financier.
    expect(
      presentationStatutPaiement({ statut: "a_verifier", moyen: "VIREMENT" }, "usager"),
    ).toEqual({ cle: "enVerification", ton: "neutre" });
    expect(
      presentationStatutPaiement({ statut: "a_verifier", moyen: "VIREMENT" }, "fiscal"),
    ).toEqual({ cle: "aVerifier", ton: "avertissement" });
  });

  it("montre au fiscal un virement déclaré comme attendu sur son relevé", () => {
    expect(
      presentationStatutPaiement({ statut: "en_attente", moyen: "VIREMENT" }, "fiscal").cle,
    ).toBe("attenduSurReleve");
    expect(presentationStatutPaiement({ statut: "initie", moyen: "VIREMENT" }, "agent").cle).toBe(
      "virementAttendu",
    );
    expect(
      presentationStatutPaiement({ statut: "en_attente", moyen: "TRESORPAY" }, "usager").cle,
    ).toBe("attenteConfirmation");
  });

  it("tient une référence de virement pour expirée dès la date limite passée", () => {
    const maintenant = new Date("2026-11-30T12:00:00Z");
    const virement = { statut: "initie", moyen: "VIREMENT" };
    expect(
      referenceVirementExpiree({ ...virement, dateExpiration: "2026-11-30T11:59:00Z" }, maintenant),
    ).toBe(true);
    expect(
      referenceVirementExpiree({ ...virement, dateExpiration: "2026-12-01T12:00:00Z" }, maintenant),
    ).toBe(false);
    // Un virement déclaré n'expire pas : il attend le relevé bancaire.
    expect(
      referenceVirementExpiree(
        { statut: "en_attente", moyen: "VIREMENT", dateExpiration: "2026-11-30T11:59:00Z" },
        maintenant,
      ),
    ).toBe(false);
  });

  it("déduit le profil de consultation", () => {
    expect(profilPaiement({ interne: false, permissions: ["PAYER"] })).toBe("usager");
    expect(profilPaiement({ interne: true, permissions: ["ENCAISSER"] })).toBe("fiscal");
    expect(profilPaiement({ interne: true, permissions: ["INSTRUIRE"] })).toBe("agent");
  });

  it("a un libellé pour chaque présentation, en français et en anglais", async () => {
    const en = (await import("../../../messages/en.json")).default;
    for (const statut of ["initie", "en_attente", "a_verifier", "paye", "remboursement_demande"]) {
      for (const moyen of ["VIREMENT", "TRESORPAY"]) {
        for (const profil of ["usager", "fiscal", "agent"] as const) {
          const { cle } = presentationStatutPaiement({ statut, moyen }, profil);
          expect(fr.statutsPaiementAffiches).toHaveProperty(cle);
          expect(en.statutsPaiementAffiches).toHaveProperty(cle);
        }
      }
    }
  });
});

describe("demande dont le paiement est en vérification (US-04.7)", () => {
  const deposee = {
    id: 3,
    statut: "deposee",
    fraisPayes: false,
    paiementEnVerification: true,
    aTraiterParMoi: false,
  };

  it("n'est plus « Frais à payer » : gris, au Ministère", () => {
    expect(presentationStatut(deposee, false)).toEqual({
      profil: "demandeur",
      cle: "paiementEnVerification",
      ton: "neutre",
    });
    expect(presentationStatut({ ...deposee, statut: "decision" }, false).cle).toBe(
      "paiementEnVerification",
    );
    expect(presentationStatut(deposee, true).cle).toBe("paiementEnVerification");
    expect(cleAttente(deposee, false)).toBe("attenteMinistere");
    expect(cleAttente(deposee, true)).toBe("attente");
  });

  it("ne propose plus de payer, ni dans la liste ni au tableau de bord", () => {
    expect(actionListe(deposee)).toBeNull();
    expect(actionListe({ ...deposee, paiementEnVerification: false })?.cle).toBe("payerFrais");
    expect(actionTableauDeBord(deposee as never)).toBeNull();
  });

  it("dit à chacun où en est le paiement", () => {
    const usager = { interne: false, permissions: ["DEPOSER", "PAYER"] };
    const guichet = { interne: true, permissions: ["INSTRUIRE"] };
    const etat = { fraisPayes: false, paiementEnVerification: true };
    expect(prochaineEtape("deposee", usager, etat)?.cle).toBe("demandeurPaiementVerification");
    expect(prochaineEtape("deposee", guichet, etat)?.cle).toBe("agentPaiementVerification");
    expect(prochaineEtape("decision", guichet, etat)?.cle).toBe("agentDroitsVerification");
    expect(prochaineEtape("deposee", usager, { fraisPayes: false })?.cle).toBe(
      "demandeurPayerFrais",
    );
  });
});

describe("virement : coordonnées et déclaration (US-04.7)", () => {
  beforeEach(() => {
    refresh.mockReset();
    vi.restoreAllMocks();
  });

  it("donne les coordonnées, le montant exact et la référence à indiquer", () => {
    afficher(
      <CoordonneesVirement
        coordonnees={{
          beneficiaire: "Trésor public (démonstration)",
          banque: "Banque de démonstration",
          iban: "GN00 0000 0000 0000 0000 0000 000",
          bic: "DEMOGNGNXXX",
        }}
        montantGnf={5_000_000}
        reference="PAY-2026-000123"
      />,
    );
    expect(screen.getByText("GN00 0000 0000 0000 0000 0000 000")).toBeVisible();
    expect(screen.getByText("DEMOGNGNXXX")).toBeVisible();
    expect(screen.getByText("PAY-2026-000123")).toBeVisible();
    expect(screen.getByText(/5\s000\s000\sGNF/)).toBeVisible();
    expect(screen.getByText("Libellé à indiquer")).toBeVisible();
  });

  it("contrôle le justificatif avant l'envoi", () => {
    expect(controlerJustificatif({ name: "ordre.pdf", size: 1000, type: "application/pdf" })).toBe(
      null,
    );
    expect(
      controlerJustificatif({ name: "ordre.docx", size: 1000, type: "application/msword" }),
    ).toBe("format");
    expect(
      controlerJustificatif({
        name: "ordre.pdf",
        size: 5 * 1024 * 1024 + 1,
        type: "application/pdf",
      }),
    ).toBe("taille");
  });

  it("exige le justificatif avant « J'ai effectué le virement »", () => {
    const appel = vi.spyOn(globalThis, "fetch");
    afficher(<DeclarationVirement reference="PAY-2026-000123" />);
    fireEvent.click(screen.getByRole("button", { name: "J’ai effectué le virement" }));
    expect(screen.getByText("Joignez votre justificatif pour déclarer le virement.")).toBeVisible();
    expect(screen.getByLabelText("Justificatif du virement")).toHaveFocus();
    expect(appel).not.toHaveBeenCalled();
  });

  it("refuse un fichier trop volumineux sans l'envoyer", () => {
    const appel = vi.spyOn(globalThis, "fetch");
    afficher(<DeclarationVirement reference="PAY-2026-000123" />);
    const lourd = new File([new Uint8Array(5 * 1024 * 1024 + 1)], "ordre.pdf", {
      type: "application/pdf",
    });
    fireEvent.change(screen.getByLabelText("Justificatif du virement"), {
      target: { files: [lourd] },
    });
    expect(screen.getByText("Fichier trop volumineux : 5 Mo au maximum.")).toBeVisible();
    expect(appel).not.toHaveBeenCalled();
  });

  it("déclare le virement avec le justificatif, puis recharge la page", async () => {
    const appel = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response("{}", { status: 200 }));
    afficher(<DeclarationVirement reference="PAY-2026-000123" />);
    const pdf = new File(["%PDF-1.7"], "ordre.pdf", { type: "application/pdf" });
    fireEvent.change(screen.getByLabelText("Justificatif du virement"), {
      target: { files: [pdf] },
    });
    expect(screen.getByText("Fichier choisi : ordre.pdf")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "J’ai effectué le virement" }));
    await waitFor(() => expect(refresh).toHaveBeenCalled());
    const [url, options] = appel.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("/api/paiements/PAY-2026-000123/virement");
    expect(options.method).toBe("POST");
    expect((options.body as FormData).get("fichier")).toBe(pdf);
  });

  it("affiche le refus de l'API sans recharger", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ code: "PAY-010", message: "Référence expirée." }), {
        status: 409,
      }),
    );
    afficher(<DeclarationVirement reference="PAY-2026-000123" />);
    fireEvent.change(screen.getByLabelText("Justificatif du virement"), {
      target: { files: [new File(["%PDF"], "ordre.pdf", { type: "application/pdf" })] },
    });
    fireEvent.click(screen.getByRole("button", { name: "J’ai effectué le virement" }));
    expect(await screen.findByText("Référence expirée.")).toBeVisible();
    expect(refresh).not.toHaveBeenCalled();
  });
});
