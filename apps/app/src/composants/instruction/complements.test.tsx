import type { ComplementDto } from "@emine/api-client";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import fr from "../../../messages/fr.json";
import { presentationStatut } from "../demande/statut";
import { lireActionFaite } from "../travail/file-travail";
import {
  ComplementSansReponse,
  DemandeComplement,
  HistoriqueComplements,
  ReponseComplement,
  ResumeComplement,
} from "./complements";

const push = vi.fn();
const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push, refresh }) }));

const complement: ComplementDto = {
  id: 3,
  ouvert: true,
  statutOrigine: "deposee",
  motif: "L'EIES n'est pas signée par le bureau d'études.",
  piecesDemandees: [],
  demandeLe: "2026-11-20T09:00:00Z",
  dateLimite: "2026-11-25T09:00:00Z",
  relanceLe: null,
  reponduLe: null,
  reponse: null,
  closLe: null,
  auteur: "Aïcha Camara",
  auteurProfil: "Agent Guichet Unique (CPDM)",
};

function avecIntl(enfant: ReactNode) {
  return render(
    <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
      {enfant}
    </NextIntlClientProvider>,
  );
}

const ok = <T,>(valeur: T) => vi.fn().mockResolvedValue({ ok: true, valeur });

beforeEach(() => {
  push.mockReset();
  refresh.mockReset();
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({ ok: true, json: async () => [] } as unknown as Response),
  );
});

describe("compléments (US-05.2)", () => {
  it("l'agent demande un complément : pièces manquantes cochées, motif exigé, file suivante", async () => {
    const demander = ok(complement);
    avecIntl(
      <DemandeComplement
        demandeId={41}
        numero="DEM-2026-000041"
        piecesProposees={["EIES", "Plan de restauration"]}
        piecesCochees={["EIES"]}
        demander={demander}
        groupe="a-examiner"
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: "Demander un complément…" }));
    expect(screen.getByRole("checkbox", { name: "EIES" })).toBeChecked();
    expect(screen.getByRole("checkbox", { name: "Plan de restauration" })).not.toBeChecked();
    fireEvent.click(screen.getByRole("button", { name: "Envoyer la demande de complément" }));
    expect(await screen.findByText(/le demandeur le lira/)).toBeInTheDocument();
    expect(demander).not.toHaveBeenCalled();

    fireEvent.change(screen.getByLabelText("Autre pièce attendue (facultatif)"), {
      target: { value: "Attestation du bureau d'études" },
    });
    fireEvent.change(screen.getByRole("textbox", { name: /Ce que le demandeur doit faire/ }), {
      target: { value: "Joignez l'EIES signée par le bureau d'études agréé." },
    });
    fireEvent.click(screen.getByRole("button", { name: "Envoyer la demande de complément" }));
    await waitFor(() =>
      expect(demander).toHaveBeenCalledWith(
        41,
        "Joignez l'EIES signée par le bureau d'études agréé.",
        ["EIES", "Attestation du bureau d'études"],
      ),
    );
    await waitFor(() =>
      expect(push).toHaveBeenCalledWith(
        "/a-traiter?groupe=a-examiner&traitee=DEM-2026-000041&resultat=complement_requis",
      ),
    );
  });

  it("le demandeur voit le service (pas le nom de l'agent) et la date limite", () => {
    avecIntl(<ResumeComplement complement={complement} interne={false} />);
    expect(screen.getByText(/Agent Guichet Unique \(CPDM\)/)).toBeInTheDocument();
    expect(screen.queryByText(/Aïcha Camara/)).not.toBeInTheDocument();
    expect(screen.getByText("25/11/2026")).toBeInTheDocument();
  });

  it("l'agent voit qui a demandé le complément", () => {
    avecIntl(<ResumeComplement complement={complement} interne />);
    expect(screen.getByText(/Aïcha Camara \(Agent Guichet Unique \(CPDM\)\)/)).toBeInTheDocument();
  });

  it("sans pièce demandée, la réponse écrite est exigée puis envoyée", async () => {
    const repondre = ok({ demandeId: 41, statut: "deposee" });
    avecIntl(<ReponseComplement demandeId={41} complement={complement} repondre={repondre} />);
    fireEvent.click(screen.getByRole("button", { name: "Envoyer ma réponse" }));
    expect(
      await screen.findByText("Écrivez votre réponse en 10 caractères au moins."),
    ).toBeInTheDocument();
    expect(screen.queryByText(/le demandeur le lira/)).not.toBeInTheDocument();
    expect(repondre).not.toHaveBeenCalled();
    fireEvent.change(screen.getByRole("textbox", { name: /Votre réponse/ }), {
      target: { value: "EIES signée jointe au dossier." },
    });
    fireEvent.click(screen.getByRole("button", { name: "Envoyer ma réponse" }));
    await waitFor(() =>
      expect(repondre).toHaveBeenCalledWith(41, "EIES signée jointe au dossier."),
    );
    expect(push).toHaveBeenCalledWith("/demandes/41?complement=envoye");
  });

  it("avec des pièces demandées, la réponse attend chaque pièce jointe", async () => {
    const repondre = ok({ demandeId: 41, statut: "deposee" });
    avecIntl(
      <ReponseComplement
        demandeId={41}
        complement={{ ...complement, piecesDemandees: ["EIES"] }}
        repondre={repondre}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: "Envoyer ma réponse" }));
    expect(await screen.findByText("Joignez d’abord : EIES.")).toBeInTheDocument();
    expect(repondre).not.toHaveBeenCalled();
  });

  it("après le délai, l'agent relance ou clôt avec un motif ; rien d'automatique", async () => {
    const relancer = ok(complement);
    const clore = ok({ demandeId: 41, statut: "rejetee" });
    avecIntl(
      <ComplementSansReponse
        demandeId={41}
        numero="DEM-2026-000041"
        dateLimite="2026-11-25T09:00:00Z"
        relancer={relancer}
        clore={clore}
      />,
    );
    expect(screen.getByText(/n’a pas répondu avant le 25\/11\/2026/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Clôturer le dossier…" }));
    fireEvent.click(screen.getByRole("button", { name: "Clôturer le dossier" }));
    expect(await screen.findByText(/le demandeur le lira/)).toBeInTheDocument();
    expect(clore).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Annuler" }));
    fireEvent.click(screen.getByRole("button", { name: "Relancer le demandeur" }));
    await waitFor(() => expect(relancer).toHaveBeenCalledWith(41));
    expect(push).toHaveBeenCalledWith(
      "/a-traiter?groupe=sans-reponse&traitee=DEM-2026-000041&resultat=relance",
    );
  });

  it("l'historique dit comment chaque complément s'est terminé", () => {
    avecIntl(
      <HistoriqueComplements
        interne
        complements={[
          {
            ...complement,
            ouvert: false,
            reponduLe: "2026-11-22T10:00:00Z",
            reponse: "Pièce jointe.",
          },
          { ...complement, id: 2, ouvert: false, closLe: "2026-11-15T10:00:00Z" },
        ]}
      />,
    );
    expect(screen.getByText("Réponse reçue le 22/11/2026")).toBeInTheDocument();
    expect(screen.getByText("Pièce jointe.")).toBeInTheDocument();
    expect(screen.getByText("Clôturé sans réponse le 15/11/2026")).toBeInTheDocument();
  });

  it("statut de l'agent : « Sans réponse » en orange seulement si c'est à lui d'agir", () => {
    const base = { statut: "complement_requis", fraisPayes: true };
    expect(presentationStatut({ ...base, aTraiterParMoi: true }, true)).toMatchObject({
      cle: "sansReponse",
      ton: "avertissement",
    });
    expect(presentationStatut({ ...base, aTraiterParMoi: false }, true)).toMatchObject({
      cle: "complementAttendu",
      ton: "neutre",
    });
    expect(presentationStatut({ ...base, aTraiterParMoi: true }, false)).toMatchObject({
      cle: "complementAFournir",
      ton: "avertissement",
    });
  });

  it("les confirmations de la file reconnaissent le complément et la relance", () => {
    expect(
      lireActionFaite({ numero: "DEM-2026-000041", resultat: "complement_requis" }),
    ).toMatchObject({ resultat: "complement_requis" });
    expect(lireActionFaite({ numero: "DEM-2026-000041", resultat: "relance" })).toMatchObject({
      resultat: "relance",
    });
  });
});
