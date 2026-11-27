import type { EtapeSuiviDto } from "@emine/api-client";
import { render, screen, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import fr from "../../../messages/fr.json";
import { FriseSuivi } from "./frise-suivi";

const CLES = [
  "depot",
  "paiement-frais",
  "controle",
  "instruction",
  "decision",
  "paiement-droits",
  "signe",
];

function etapes(etats: string[], extra: Partial<EtapeSuiviDto>[] = []): EtapeSuiviDto[] {
  return CLES.map((cle, i) => ({
    cle,
    etat: etats[i] ?? "a-venir",
    date: etats[i] === "faite" ? "2026-11-0" + (i + 1) + "T10:00:00Z" : null,
    actionDemandeur: false,
    enVerification: false,
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

describe("frise de suivi (US-07.3, US-07.6)", () => {
  it("montre les 7 étapes dans l'ordre réel, dont la décision, avec la date des étapes faites", () => {
    const liste = afficher(etapes(["faite", "faite", "en-cours"]));
    const items = within(liste).getAllByRole("listitem");
    const libelles = [
      "Dépôt",
      "Paiement des frais",
      "Contrôle du dossier",
      "Instruction",
      "Décision",
      "Paiement des droits",
      "Document signé",
    ];
    expect(items).toHaveLength(7);
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
        ["faite", "faite", "rejetee", "non-atteinte", "non-atteinte", "sans-objet", "non-atteinte"],
        [{}, {}, { date: "2026-11-03T10:00:00Z" }],
      ),
    );
    const items = within(liste).getAllByRole("listitem");
    expect(items[2]).toHaveTextContent("rejetée le 03/11/2026");
    expect(items[3]).toHaveTextContent("non atteinte");
    expect(items[5]).toHaveTextContent("sans objet");
  });

  it("pour une attestation (paiement unique), l'étape s'appelle « Paiement »", () => {
    afficher(etapes(["faite", "en-cours"]), false, true);
    expect(screen.getByText("Paiement")).toBeInTheDocument();
    expect(screen.queryByText("Paiement des frais")).not.toBeInTheDocument();
  });

  it("sous l'étape en cours, dit ce qui la débloque (avis attendus, décision)", () => {
    render(
      <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
        <FriseSuivi
          etapes={etapes(["faite", "faite", "faite", "en-cours"])}
          interne
          precisions={{ instruction: "Avis attendus : Inspecteur des Mines" }}
        />
      </NextIntlClientProvider>,
    );
    const items = within(screen.getByRole("list", { name: "Suivi de la demande" })).getAllByRole(
      "listitem",
    );
    expect(items[3]).toHaveTextContent("Avis attendus : Inspecteur des Mines");
  });

  it("la décision du signataire est une étape à part, avec ce qu'elle attend (US-07.6)", () => {
    render(
      <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
        <FriseSuivi
          etapes={etapes(["faite", "faite", "faite", "sans-objet", "en-cours"])}
          interne={false}
          precisions={{ decision: "Décision attendue : Ministre" }}
        />
      </NextIntlClientProvider>,
    );
    const items = within(screen.getByRole("list", { name: "Suivi de la demande" })).getAllByRole(
      "listitem",
    );
    expect(items[3]).toHaveTextContent("Instruction");
    expect(items[3]).toHaveTextContent("sans objet");
    expect(items[4]).toHaveAttribute("aria-current", "step");
    expect(items[4]).toHaveTextContent("Décision");
    expect(items[4]).toHaveTextContent("Décision attendue : Ministre");
  });

  it("dit « en vérification » quand le paiement est au ministère, pas « à vous d'agir » (R1)", () => {
    const liste = afficher(etapes(["faite", "en-cours"], [{}, { enVerification: true }]));
    const frais = within(liste).getAllByRole("listitem")[1];
    expect(frais).toHaveTextContent("en vérification");
    expect(frais).not.toHaveTextContent("à vous d’agir");
  });
});
