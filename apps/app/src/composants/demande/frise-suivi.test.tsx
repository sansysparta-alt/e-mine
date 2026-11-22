import type { EtapeSuiviDto } from "@emine/api-client";
import { render, screen, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import fr from "../../../messages/fr.json";
import { FriseSuivi } from "./frise-suivi";

const CLES = ["depot", "paiement-frais", "controle", "instruction", "paiement-droits", "signe"];

function etapes(etats: string[], extra: Partial<EtapeSuiviDto>[] = []): EtapeSuiviDto[] {
  return CLES.map((cle, i) => ({
    cle,
    etat: etats[i] ?? "a-venir",
    date: etats[i] === "faite" ? "2026-11-0" + (i + 1) + "T10:00:00Z" : null,
    actionDemandeur: false,
    ...extra[i],
  }));
}

function afficher(e: EtapeSuiviDto[], interne = false, paiementUnique = false) {
  render(
    <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
      <FriseSuivi etapes={e} interne={interne} paiementUnique={paiementUnique} />
    </NextIntlClientProvider>,
  );
  return screen.getByRole("list", { name: "Suivi de la demande" });
}

describe("frise de suivi (US-07.3)", () => {
  it("montre les 6 étapes dans l'ordre réel, avec la date des étapes faites", () => {
    const liste = afficher(etapes(["faite", "faite", "en-cours", "a-venir", "a-venir", "a-venir"]));
    const items = within(liste).getAllByRole("listitem");
    const libelles = [
      "Dépôt",
      "Paiement des frais",
      "Contrôle du dossier",
      "Instruction",
      "Paiement des droits",
      "Document signé",
    ];
    expect(items).toHaveLength(6);
    libelles.forEach((l, i) => expect(within(items[i]!).getByText(l)).toBeInTheDocument());
    expect(items[0]).toHaveTextContent("le 01/11/2026");
    expect(items[2]).toHaveAttribute("aria-current", "step");
    expect(items[2]).toHaveTextContent("en cours");
    expect(items[3]).toHaveTextContent("à venir");
    expect(items[3]).not.toHaveAttribute("aria-current");
  });

  it("dit au demandeur que c'est à lui d'agir, et à l'agent qu'on attend le demandeur", () => {
    const e = etapes(["faite", "en-cours"], [{}, { actionDemandeur: true }]);
    afficher(e);
    expect(screen.getByText("à vous d’agir")).toBeInTheDocument();
  });

  it("côté agent, l'action du demandeur se lit « en attente du demandeur »", () => {
    afficher(etapes(["faite", "en-cours"], [{}, { actionDemandeur: true }]), true);
    expect(screen.getByText("en attente du demandeur")).toBeInTheDocument();
    expect(screen.queryByText("à vous d’agir")).not.toBeInTheDocument();
  });

  it("un rejet est daté ; les étapes suivantes ne sont pas atteintes ; « sans objet » explicite", () => {
    const liste = afficher(
      etapes(
        ["faite", "faite", "rejetee", "non-atteinte", "sans-objet", "non-atteinte"],
        [{}, {}, { date: "2026-11-03T10:00:00Z" }],
      ),
    );
    const items = within(liste).getAllByRole("listitem");
    expect(items[2]).toHaveTextContent("rejetée le 03/11/2026");
    expect(items[3]).toHaveTextContent("non atteinte");
    expect(items[4]).toHaveTextContent("sans objet");
  });

  it("pour une attestation (paiement unique), l'étape s'appelle « Paiement »", () => {
    afficher(etapes(["faite", "en-cours"]), false, true);
    expect(screen.getByText("Paiement")).toBeInTheDocument();
    expect(screen.queryByText("Paiement des frais")).not.toBeInTheDocument();
  });
});
