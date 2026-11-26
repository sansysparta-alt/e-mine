import { act, render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import fr from "../../../messages/fr.json";
import { ATTENTE_MAX_MS, AttenteConfirmation, INTERVALLE_MS } from "./attente-confirmation";
import { enAttenteDeConfirmation, peutReessayer, tonStatutPaiement } from "./statut";

const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh }) }));

function afficher() {
  render(
    <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
      <AttenteConfirmation lienDemande="/demandes/42" />
    </NextIntlClientProvider>,
  );
}

describe("AttenteConfirmation (US-04.3)", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    refresh.mockClear();
  });
  afterEach(() => vi.useRealTimers());

  it("relit le statut toutes les 2 s pendant 10 s, puis explique l'attente sans inviter à repayer", () => {
    afficher();
    expect(screen.getByRole("status")).toHaveTextContent("Confirmation de votre paiement");
    act(() => vi.advanceTimersByTime(INTERVALLE_MS * 2));
    expect(refresh).toHaveBeenCalledTimes(2);
    act(() => vi.advanceTimersByTime(ATTENTE_MAX_MS));
    expect(screen.getByText("Confirmation en attente")).toBeInTheDocument();
    expect(screen.getByText(/Ne payez pas une seconde fois/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Retour à la demande" })).toHaveAttribute(
      "href",
      "/demandes/42",
    );
    const appels = refresh.mock.calls.length;
    act(() => vi.advanceTimersByTime(INTERVALLE_MS * 5));
    expect(refresh).toHaveBeenCalledTimes(appels);
  });

  it("relance l'attente sur « Actualiser »", () => {
    afficher();
    act(() => vi.advanceTimersByTime(ATTENTE_MAX_MS + INTERVALLE_MS));
    act(() => screen.getByRole("button", { name: "Actualiser" }).click());
    expect(screen.getByRole("status")).toHaveTextContent("Confirmation de votre paiement");
  });
});

describe("statuts de paiement", () => {
  it("associe un ton du code couleur commun à chaque statut", () => {
    expect(tonStatutPaiement("paye")).toBe("succes");
    expect(tonStatutPaiement("en_attente")).toBe("info");
    expect(tonStatutPaiement("a_verifier")).toBe("avertissement");
    expect(tonStatutPaiement("echoue")).toBe("danger");
    expect(tonStatutPaiement("annule")).toBe("neutre");
    expect(tonStatutPaiement("inconnu")).toBe("neutre");
  });

  it("distingue l'attente de confirmation et les issues qui permettent de réessayer", () => {
    expect(enAttenteDeConfirmation({ statut: "en_attente", moyen: "TRESORPAY" })).toBe(true);
    expect(enAttenteDeConfirmation({ statut: "paye", moyen: "TRESORPAY" })).toBe(false);
    // Un virement déclaré n'attend pas TrésorPay : il attend le relevé bancaire (US-04.7).
    expect(enAttenteDeConfirmation({ statut: "en_attente", moyen: "VIREMENT" })).toBe(false);
    expect(peutReessayer("echoue")).toBe(true);
    expect(peutReessayer("annule")).toBe(true);
    expect(peutReessayer("paye")).toBe(false);
    expect(peutReessayer("a_verifier")).toBe(false);
  });

  it("a un libellé français pour chaque statut", () => {
    for (const statut of Object.keys(fr.statutsPaiement)) {
      expect(fr.statutsPaiement[statut as keyof typeof fr.statutsPaiement]).toBeTruthy();
    }
  });
});
