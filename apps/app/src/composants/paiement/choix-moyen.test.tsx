import type { MoyenDto, PaiementDto } from "@emine/api-client";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import fr from "../../../messages/fr.json";
import { ChoixMoyen } from "./choix-moyen";

const moyens: MoyenDto[] = [
  { moyen: "TRESORPAY", disponible: true },
  { moyen: "VIREMENT", disponible: false },
];

const paiement = { reference: "PAY-2026-000001", statut: "initie" } as PaiementDto;

function afficher(choisir = vi.fn(), tentativesRestantes = 3) {
  render(
    <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
      <ChoixMoyen
        demandeId={42}
        moyens={moyens}
        montant="5 000 000 GNF"
        tentativesRestantes={tentativesRestantes}
        choisir={choisir}
      />
    </NextIntlClientProvider>,
  );
  return choisir;
}

describe("ChoixMoyen (US-04.2)", () => {
  it("n'a aucun moyen présélectionné et garde un moyen désactivé visible, avec sa raison", () => {
    afficher();
    const tresorpay = screen.getByRole("radio", { name: /TrésorPay/ });
    const virement = screen.getByRole("radio", { name: /Virement bancaire/ });
    expect(tresorpay).not.toBeChecked();
    expect(virement).not.toBeChecked();
    expect(tresorpay).toBeEnabled();
    expect(virement).toBeDisabled();
    expect(screen.getByText("Non disponible")).toBeInTheDocument();
  });

  it("propose le virement, même quand les paiements en ligne du jour sont épuisés (US-04.7)", () => {
    const choisir = vi.fn().mockResolvedValue({ ok: true, valeur: {} });
    render(
      <NextIntlClientProvider locale="fr" messages={fr}>
        <ChoixMoyen
          demandeId={7}
          moyens={[
            { moyen: "TRESORPAY", disponible: true },
            { moyen: "VIREMENT", disponible: true },
          ]}
          montant="5 000 000 GNF"
          tentativesRestantes={0}
          choisir={choisir}
        />
      </NextIntlClientProvider>,
    );
    fireEvent.click(screen.getByRole("radio", { name: /Virement bancaire/ }));
    const bouton = screen.getByRole("button", { name: "Payer 5 000 000 GNF par virement" });
    expect(bouton).toBeEnabled();
    expect(
      screen.getByText(
        "Vous obtiendrez les coordonnées du compte et une référence valable 7 jours.",
      ),
    ).toBeVisible();
    fireEvent.click(bouton);
    expect(choisir).toHaveBeenCalledWith(7, "VIREMENT");
  });

  it("demande de choisir un moyen avant de continuer, sans appeler l'API", () => {
    const choisir = afficher();
    fireEvent.click(screen.getByRole("button", { name: "Payer 5 000 000 GNF" }));
    expect(screen.getByText("Choisissez un moyen de paiement pour continuer.")).toBeInTheDocument();
    expect(screen.getByRole("group")).toHaveAttribute("aria-invalid", "true");
    expect(choisir).not.toHaveBeenCalled();
  });

  it("reprend le moyen choisi sur le bouton et crée le paiement", async () => {
    const choisir = afficher(vi.fn().mockResolvedValue({ ok: true, valeur: paiement }));
    fireEvent.click(screen.getByRole("radio", { name: /TrésorPay/ }));
    const bouton = screen.getByRole("button", { name: "Payer 5 000 000 GNF avec TrésorPay" });
    fireEvent.click(bouton);
    await waitFor(() => expect(choisir).toHaveBeenCalledWith(42, "TRESORPAY"));
    expect(await screen.findByText("Paiement PAY-2026-000001 créé")).toBeInTheDocument();
  });

  it("annonce les tentatives restantes et la redirection vers TrésorPay", () => {
    afficher(vi.fn(), 2);
    expect(screen.getByText(/dirigé vers la page sécurisée de TrésorPay/)).toBeInTheDocument();
    expect(screen.getByText(/Il vous reste 2 tentatives aujourd’hui/)).toBeInTheDocument();
  });

  it("bloque le paiement après 3 tentatives dans la journée", () => {
    afficher(vi.fn(), 0);
    expect(screen.getByText("Nombre de tentatives atteint pour aujourd’hui")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Payer/ })).toBeDisabled();
  });

  it("rassure quand TrésorPay est indisponible (PAY-003)", async () => {
    afficher(
      vi.fn().mockResolvedValue({
        ok: false,
        erreur: { code: "PAY-003", message: "", champs: [] },
      }),
    );
    fireEvent.click(screen.getByRole("radio", { name: /TrésorPay/ }));
    fireEvent.click(screen.getByRole("button", { name: /avec TrésorPay/ }));
    expect(await screen.findByText("TrésorPay est momentanément indisponible")).toBeInTheDocument();
    expect(screen.getByText(/aucune tentative n’a été décomptée/)).toBeInTheDocument();
  });

  it("explique un refus de l'API (objet déjà payé)", async () => {
    afficher(
      vi.fn().mockResolvedValue({
        ok: false,
        erreur: { code: "PAY-001", message: "Ce paiement a déjà été effectué.", champs: [] },
      }),
    );
    fireEvent.click(screen.getByRole("radio", { name: /TrésorPay/ }));
    fireEvent.click(screen.getByRole("button", { name: /avec TrésorPay/ }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Ce paiement a déjà été effectué");
  });
});
