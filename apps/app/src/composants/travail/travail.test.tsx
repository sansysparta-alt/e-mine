import type { DemandeDto } from "@emine/api-client";
import { render, screen, within } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import fr from "../../../messages/fr.json";
import { VueTableauDeBord } from "../vue-tableau-de-bord";
import { BarreSerie } from "./barre-serie";
import {
  actionPour,
  adresseApresAction,
  groupesDepuis,
  lireParametresTravail,
  parametresListe,
  positionDansFile,
} from "./file-travail";
import { TableauTravail } from "./tableau-travail";

function afficher(enfant: ReactNode) {
  return render(
    <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
      {enfant}
    </NextIntlClientProvider>,
  );
}

const ministre = { interne: true, permissions: ["SIGNER"], profil: { code: "MINISTRE" } };
const guichet = { interne: true, permissions: ["INSTRUIRE"], profil: { code: "GUICHET" } };

function dossier(id: number, statut: string, fraisPayes = true): DemandeDto {
  return {
    id,
    numero: `DEM-2026-00000${id}`,
    statut,
    typeDocumentCode: "PEX",
    demandeur: { prenom: "Fodé", nom: "Bangoura", email: "f@exemple.test", organisation: null },
    prefectureId: null,
    prefectureNom: null,
    substanceCode: null,
    substanceLibelle: null,
    superficieHa: null,
    creeLe: "2026-11-15T10:00:00Z",
    deposeLe: "2026-11-15T10:00:00Z",
    fraisPayes,
    motifRejet: null,
    aTraiterParMoi: false,
  };
}

describe("file de travail selon le profil (correctifs J5)", () => {
  it("construit les groupes à partir du compteur de l'API (règle unique)", () => {
    const signataire = {
      total: 3,
      groupes: [
        { cle: "a-decider", nombre: 1 },
        { cle: "a-signer", nombre: 2 },
      ],
    };
    const groupes = groupesDepuis(signataire, ministre);
    expect(groupes.map((g) => g.cle)).toEqual(["tout", "a-decider", "a-signer"]);
    expect(groupes.map((g) => g.nombre)).toEqual([3, 1, 2]);
    expect(parametresListe(groupes[0]!)).toEqual({ aTraiter: true, tri: "date", sens: "asc" });
    expect(parametresListe(groupes[1]!)).toMatchObject({ groupe: "a-decider" });
    const auGuichet = groupesDepuis(
      { total: 1, groupes: [{ cle: "a-examiner", nombre: 1 }] },
      guichet,
    );
    expect(auGuichet.map((g) => g.cle)).toEqual(["a-examiner", "frais-attendus"]);
    expect(auGuichet[1]).toMatchObject({ actionnable: false });
    expect(
      groupesDepuis(
        { total: 1, groupes: [{ cle: "payer-frais", nombre: 1 }] },
        { interne: false, permissions: ["DEPOSER"], profil: { code: "INVESTISSEUR" } },
      ),
    ).toEqual([]);
  });

  it("dit l'action attendue de chaque dossier", () => {
    expect(actionPour("deposee", true)).toBe("examiner");
    expect(actionPour("deposee", false)).toBeNull();
    expect(actionPour("recevable", true)).toBe("decider");
    expect(actionPour("droits_payes", true)).toBe("signer");
    expect(actionPour("delivree", true)).toBeNull();
  });

  it("ignore un groupe qui n'est pas celui du profil", () => {
    const groupes = groupesDepuis(
      { total: 1, groupes: [{ cle: "a-examiner", nombre: 1 }] },
      guichet,
    );
    expect(lireParametresTravail({ groupe: "a-signer" }, groupes).groupe.cle).toBe("a-examiner");
    expect(
      lireParametresTravail(
        { traitee: "DEM-2026-000001", resultat: "delivree", document: "<x>" },
        groupes,
      ).faite,
    ).toEqual({ numero: "DEM-2026-000001", resultat: "delivree" });
  });
});

describe("traitement en série", () => {
  it("situe le dossier dans la file et donne ses voisins", () => {
    expect(positionDansFile([4, 7, 9], 7)).toEqual({
      dansLaFile: true,
      total: 3,
      position: 2,
      precedent: 4,
      suivant: 9,
    });
    expect(positionDansFile([4, 7, 9], 12)).toEqual({ dansLaFile: false, total: 3, premier: 4 });
  });

  it("ouvre le dossier suivant après l'action, ou revient à la file quand c'était le dernier", () => {
    const faite = { numero: "DEM-2026-000007", resultat: "decision" as const };
    expect(adresseApresAction({ groupe: "tout", suivant: 9 }, "a-decider", faite)).toBe(
      "/demandes/9?file=tout&fait=DEM-2026-000007&resultat=decision",
    );
    expect(adresseApresAction({ groupe: "tout", suivant: null }, "a-decider", faite)).toBe(
      "/a-traiter?groupe=tout&traitee=DEM-2026-000007&resultat=decision",
    );
    expect(adresseApresAction(null, "a-decider", faite)).toBe(
      "/a-traiter?groupe=a-decider&traitee=DEM-2026-000007&resultat=decision",
    );
  });

  it("affiche la position, précédent, passer et quitter", () => {
    afficher(<BarreSerie groupe="tout" position={2} total={3} precedent={4} suivant={9} />);
    const barre = screen.getByRole("navigation", { name: "Traitement en série" });
    expect(within(barre).getByText(/dossier 2 sur 3/)).toBeVisible();
    expect(within(barre).getByRole("link", { name: /Précédent/ })).toHaveAttribute(
      "href",
      "/demandes/4?file=tout",
    );
    expect(within(barre).getByRole("link", { name: /Passer ce dossier/ })).toHaveAttribute(
      "href",
      "/demandes/9?file=tout",
    );
    expect(within(barre).getByRole("link", { name: /Quitter le traitement/ })).toHaveAttribute(
      "href",
      "/a-traiter?groupe=tout",
    );
  });

  it("montre l'action attendue de chaque dossier et ouvre la fiche en série", () => {
    afficher(
      <TableauTravail
        demandes={[dossier(4, "recevable"), dossier(9, "droits_payes")]}
        libelles={{ PEX: "Permis d'exploitation" }}
        legende="Tout"
        groupe="tout"
        maintenant={new Date("2026-11-22T10:00:00Z")}
      />,
    );
    expect(screen.getByText("À décider")).toBeVisible();
    expect(screen.getByText("À signer")).toBeVisible();
    expect(screen.getByRole("link", { name: "DEM-2026-000004" })).toHaveAttribute(
      "href",
      "/demandes/4?file=tout",
    );
  });
});

describe("tableau de bord de l'agent", () => {
  it("ne montre que ses dossiers à traiter et propose de commencer le traitement", () => {
    afficher(
      <VueTableauDeBord
        prenom="Ibrahima"
        nom="Soumah"
        profilLibelle="Ministre"
        interne
        taches={[{ cle: "decisions", valeur: 1 }]}
        travail={{ groupe: "tout", dossiers: [dossier(4, "recevable")], total: 1 }}
      />,
    );
    expect(screen.getByRole("heading", { name: "Dossiers à traiter (1)" })).toBeVisible();
    expect(screen.getByRole("link", { name: /Commencer le traitement \(1\)/ })).toHaveAttribute(
      "href",
      "/demandes/4?file=tout",
    );
    expect(screen.queryByText(/Brouillon/)).not.toBeInTheDocument();
  });
});
