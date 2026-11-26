import { render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import type { ReactNode } from "react";
import fr from "../../messages/fr.json";
import type { DemandeDto, IndicateursDto } from "@emine/api-client";
import { langueDuNavigateur } from "@/i18n/langues";
import { adresseConnexion, supplementsConnexion } from "@/serveur/connexion";
import { actionAttendue, VueTableauDeBord } from "./vue-tableau-de-bord";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }) }));

function enFrancais(enfant: ReactNode) {
  return render(
    <NextIntlClientProvider locale="fr" messages={fr} timeZone="Africa/Conakry">
      {enfant}
    </NextIntlClientProvider>,
  );
}

function demande(partiel: Partial<DemandeDto>): DemandeDto {
  return {
    id: 1,
    numero: null,
    statut: "brouillon",
    typeDocumentCode: "PEX",
    demandeur: { prenom: "Fodé", nom: "Bangoura", email: "f@x.test", organisation: null },
    prefectureId: null,
    prefectureNom: null,
    substanceCode: null,
    substanceLibelle: null,
    superficieHa: null,
    creeLe: "2026-11-20T09:00:00Z",
    deposeLe: null,
    fraisPayes: false,
    paiementEnVerification: false,
    motifRejet: null,
    etapeDepuis: "2026-11-15T10:00:00Z",
    aTraiterParMoi: false,
    passages: 0,
    bloque: false,
    enRetard: false,
    joursRestants: null,
    procheDepassement: false,
    delaiSuspendu: false,
    motifSuspension: null,
    delaiDemarre: true,
    documentRevoque: false,
    avancement: null,
    ...partiel,
  };
}

const deposee = demande({
  id: 2,
  numero: "DEM-2026-000001",
  statut: "deposee",
  deposeLe: "2026-11-20T10:30:00Z",
});
const brouillon = demande({ id: 3, typeDocumentCode: "COR" });
const libelles = { PEX: "Permis d'exploitation", COR: "Carte d'orpailleur" };

const indicateurs: IndicateursDto = {
  periode: "mois",
  debut: "2026-11-01T00:00:00Z",
  fin: "2026-11-22T10:00:00Z",
  enRetard: 3,
  procheDepassement: 0,
  bloques: 0,
  complementsAttendus: 2,
  fraisAttendus: 4,
  traitesAujourdhui: 5,
  delivrees: 7,
  delivreesDansLeDelai: 5,
  tauxDansLeDelai: 71,
  rejetees: 1,
  delaiMoyenJours: 12.5,
  parametresDelai: {
    depart: "paiement_frais",
    pauseComplement: true,
    pauseDroits: true,
    seuilProchePourcent: 20,
    seuilProcheMinJours: 2,
  },
  encaissementsGnf: 45_000_000,
  parDocument: [
    {
      code: "PEX",
      enCours: 6,
      enRetard: 3,
      delivrees: 7,
      delaiJours: 60,
      delivreesDansLeDelai: 5,
      tauxDansLeDelai: 71,
      rejetees: 1,
      delaiMoyenJours: 12.5,
      encaissementsGnf: 45_000_000,
    },
  ],
};

const vuesDemandeur = {
  total: 9,
  vues: [
    { cle: "a-traiter", nombre: 2 },
    { cle: "en-cours", nombre: 3 },
    { cle: "brouillons", nombre: 1 },
    { cle: "terminees", nombre: 2 },
    { cle: "rejetees", nombre: 1 },
  ],
};

describe("tableau de bord du demandeur (correctifs ergonomie)", () => {
  it("met « Ce que je dois faire » en tête, avec le montant et un bouton direct", () => {
    const decidee = demande({ id: 5, numero: "DEM-2026-000005", statut: "decision" });
    enFrancais(
      <VueTableauDeBord
        prenom="Fodé"
        nom="Bangoura"
        profilLibelle="Investisseur"
        profil="demandeur"
        demandes={[deposee, decidee]}
        libelles={libelles}
        vues={vuesDemandeur}
        aFaire={[
          { demande: decidee, action: "DROITS", montantGnf: 15_000_000 },
          { demande: deposee, action: "FRAIS", montantGnf: 5_000_000 },
        ]}
      />,
    );
    const titres = screen.getAllByRole("heading", { level: 2 }).map((h) => h.textContent);
    expect(titres[0]).toBe("Ce que je dois faire (2)");
    expect(screen.getByText(/droits à payer, 15\s000\s000\sGNF/)).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: /Payer les droits · DEM-2026-000005/ }),
    ).toHaveAttribute("href", "/demandes/5/paiement");
    expect(screen.getByRole("link", { name: /Payer les frais · DEM-2026-000001/ })).toHaveAttribute(
      "href",
      "/demandes/2/paiement",
    );
  });

  it("donne des chiffres qui s'additionnent, rejetées comprises, chacun avec sa période", () => {
    enFrancais(
      <VueTableauDeBord
        prenom="Fodé"
        nom="Bangoura"
        profilLibelle="Investisseur extérieur"
        profil="demandeur"
        demandes={[deposee, brouillon]}
        libelles={libelles}
        vues={vuesDemandeur}
      />,
    );
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("Bonjour, Fodé Bangoura");
    const cartes: [RegExp, string, number][] = [
      [/Toutes mes demandes/, "/demandes", 9],
      [/À faire/, "/demandes?groupe=a-traiter", 2],
      [/En cours de traitement/, "/demandes?groupe=en-cours", 3],
      [/Brouillons/, "/demandes?groupe=brouillons", 1],
      [/Documents délivrés/, "/demandes?groupe=terminees", 2],
      [/Demandes rejetées/, "/demandes?groupe=rejetees", 1],
    ];
    let somme = 0;
    for (const [nom, href, valeur] of cartes) {
      const lien = screen.getByRole("link", { name: nom });
      expect(lien).toHaveAttribute("href", href);
      expect(lien).toHaveTextContent("À ce jour");
      expect(lien).toHaveTextContent(String(valeur));
      if (href !== "/demandes") somme += valeur;
    }
    expect(somme).toBe(9);
    expect(screen.getByText("Rien à faire de votre côté pour le moment")).toBeVisible();
    expect(screen.getByRole("link", { name: "DEM-2026-000001" })).toHaveAttribute(
      "href",
      "/demandes/2",
    );
    expect(screen.getByText("Déposée le 20/11/2026")).toBeInTheDocument();
  });

  it("affiche un état vide explicite sans demande", () => {
    enFrancais(
      <VueTableauDeBord
        prenom="Fodé"
        nom="Bangoura"
        profilLibelle="Investisseur"
        profil="demandeur"
      />,
    );
    expect(screen.getByText("Vous n'avez encore aucune demande.")).toBeInTheDocument();
  });

  it("déduit l'action attendue du statut et des frais", () => {
    expect(actionAttendue({ ...deposee, fraisPayes: false })).toBe("FRAIS");
    expect(actionAttendue({ ...deposee, fraisPayes: true })).toBeNull();
    expect(actionAttendue({ ...deposee, statut: "decision" })).toBe("DROITS");
    expect(actionAttendue({ ...deposee, statut: "recevable" })).toBeNull();
  });
});

describe("tableau de bord du guichet (correctifs ergonomie)", () => {
  it("montre les indicateurs de son rôle, chacun ouvrant sa liste filtrée", () => {
    enFrancais(
      <VueTableauDeBord
        prenom="Aïcha"
        nom="Camara"
        profilLibelle="Agent Guichet Unique"
        profil="guichet"
        indicateurs={indicateurs}
      />,
    );
    const attendues: [RegExp, string, string][] = [
      [/Compléments attendus/, "/demandes?statut=complement_requis", "À ce jour"],
      [/Frais attendus/, "/demandes?statut=deposee&fraisPayes=false", "À ce jour"],
      [/Traités aujourd’hui/, "/demandes?traiteParMoi=1", "Aujourd'hui"],
      [/En retard/, "/demandes?enRetard=1", "À ce jour"],
    ];
    for (const [nom, href, periode] of attendues) {
      const lien = screen.getByRole("link", { name: nom });
      expect(lien).toHaveAttribute("href", href);
      expect(lien).toHaveTextContent(periode);
    }
    expect(screen.queryByText("Documents délivrés")).not.toBeInTheDocument();
  });
});

describe("tableau de bord des décideurs (correctifs ergonomie)", () => {
  it("signale les retards, donne les chiffres de la période et la répartition par document", () => {
    enFrancais(
      <VueTableauDeBord
        prenom="Ibrahima"
        nom="Soumah"
        profilLibelle="Ministre"
        profil="decideur"
        aTraiterTotal={4}
        periode="30-jours"
        indicateurs={indicateurs}
        libelles={libelles}
      />,
    );
    const alerte = screen.getByRole("status", { name: "" });
    expect(alerte).toHaveTextContent("3 dossiers en retard sur les délais du catalogue");
    expect(screen.getByRole("link", { name: "Voir les dossiers en retard" })).toHaveAttribute(
      "href",
      "/demandes?enRetard=1",
    );
    expect(screen.getByRole("link", { name: "30 derniers jours" })).toHaveAttribute("aria-current");
    const delivres = screen.getByRole("link", { name: /^Documents délivrés/ });
    expect(delivres).toHaveAttribute("href", "/demandes?statut=delivree&periode=30-jours");
    expect(delivres).toHaveTextContent("30 derniers jours");
    expect(screen.getByRole("link", { name: /Délai moyen de traitement/ })).toHaveTextContent(
      "12,5 j",
    );
    // US-05.6 : part délivrée dans le délai du catalogue (calculée par l'API), justifiée par son
    // nombre ; les rejets à part, vers leur liste de la période.
    const dansLeDelai = screen.getByRole("link", { name: /Délivrés dans le délai/ });
    expect(dansLeDelai).toHaveTextContent("71 %");
    expect(dansLeDelai).toHaveTextContent("5 sur 7 documents délivrés");
    expect(dansLeDelai).toHaveTextContent("30 derniers jours");
    expect(dansLeDelai).toHaveAttribute("href", "#repartition");
    expect(screen.getByRole("link", { name: /Demandes rejetées/ })).toHaveAttribute(
      "href",
      "/demandes?statut=rejetee&periode=30-jours",
    );
    expect(screen.getByText("Délai : 60 j")).toBeInTheDocument();
    expect(screen.getByText("71 % (5 sur 7)")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Encaissements/ })).toHaveTextContent(
      /45\s000\s000\sGNF/,
    );
    expect(screen.getByRole("link", { name: /À décider ou à signer/ })).toHaveAttribute(
      "href",
      "/a-traiter",
    );
    expect(screen.getByRole("heading", { name: "Répartition par document" })).toBeVisible();
    expect(screen.getByRole("link", { name: /^Permis d'exploitation/ })).toHaveAttribute(
      "href",
      "/demandes?document=PEX",
    );
  });

  it("dit clairement qu'aucun dossier n'est en retard", () => {
    enFrancais(
      <VueTableauDeBord
        prenom="Ibrahima"
        nom="Soumah"
        profilLibelle="Ministre"
        profil="decideur"
        indicateurs={{ ...indicateurs, enRetard: 0, parDocument: [] }}
      />,
    );
    expect(screen.getByText("Aucun dossier en retard à ce jour")).toBeVisible();
    expect(screen.getByText("Aucune activité sur la période")).toBeVisible();
    expect(screen.queryByRole("link", { name: /dossiers bloqués/i })).not.toBeInTheDocument();
  });

  it("alerte sur les dossiers bloqués et ouvre leur liste (correctifs ergonomie · 7)", () => {
    enFrancais(
      <VueTableauDeBord
        prenom="Ibrahima"
        nom="Soumah"
        profilLibelle="Ministre"
        profil="decideur"
        indicateurs={{ ...indicateurs, bloques: 2 }}
      />,
    );
    const lien = screen.getByRole("link", { name: "Voir les dossiers bloqués" });
    expect(lien).toHaveAttribute("href", "/demandes?bloque=1");
    expect(lien.closest("[role=status]")).toHaveTextContent("2 dossiers bloqués");
  });
});

describe("tableau de bord indisponible (revue B)", () => {
  it("le dit clairement, sans état vide ni chiffres trompeurs", () => {
    enFrancais(
      <VueTableauDeBord
        prenom="Ibrahima"
        nom="Soumah"
        profilLibelle="Ministre"
        profil="autre"
        indisponible
      />,
    );
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Tableau de bord momentanément indisponible",
    );
    expect(screen.getByRole("link", { name: "Réessayer" })).toHaveAttribute(
      "href",
      "/tableau-de-bord",
    );
    expect(screen.queryByText(/Aucun dossier/)).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /À ce jour/ })).not.toBeInTheDocument();
  });
});

describe("droit de déposer (US-01.6)", () => {
  it("propose une nouvelle demande seulement si le profil peut déposer", () => {
    const { unmount } = enFrancais(
      <VueTableauDeBord
        prenom="Fodé"
        nom="Bangoura"
        profilLibelle="Investisseur"
        profil="demandeur"
        peutDeposer
      />,
    );
    expect(screen.getAllByRole("link", { name: "Nouvelle demande" })[0]).toHaveAttribute(
      "href",
      "/demandes/nouvelle",
    );
    unmount();
    enFrancais(
      <VueTableauDeBord prenom="Aïcha" nom="Camara" profilLibelle="Guichet" profil="guichet" />,
    );
    expect(screen.queryByRole("link", { name: "Nouvelle demande" })).not.toBeInTheDocument();
  });
});

describe("porte d'entrée : paramètres transmis à Keycloak", () => {
  it("ne laisse passer que les motifs connus, la langue et l'action de mot de passe", () => {
    expect(supplementsConnexion({ motif: "deconnecte", langue: "en" })).toEqual({
      motif: "deconnecte",
      ui_locales: "en",
      kc_locale: "en",
    });
    expect(supplementsConnexion({ motif: "<script>", action: "supprimer", langue: "de" })).toEqual({
      ui_locales: "fr",
      kc_locale: "fr",
    });
  });

  it("reprend la langue du navigateur au premier accès", () => {
    expect(langueDuNavigateur("en-GB,en;q=0.9,fr;q=0.8")).toBe("en");
    expect(langueDuNavigateur("de-DE,fr;q=0.7,en;q=0.5")).toBe("fr");
    expect(langueDuNavigateur("es-ES,de;q=0.8")).toBe("fr");
    expect(langueDuNavigateur(null)).toBe("fr");
    expect(supplementsConnexion({ action: "mot-de-passe" })).toEqual({
      ui_locales: "fr",
      kc_locale: "fr",
      kc_action: "UPDATE_PASSWORD",
    });
  });

  it("construit l'adresse de connexion avec le motif et la page de retour", () => {
    expect(adresseConnexion("deconnecte")).toBe("/api/auth/login?motif=deconnecte");
    expect(adresseConnexion("expiree", "/demandes/12?vue=a")).toBe(
      "/api/auth/login?motif=expiree&retour=%2Fdemandes%2F12%3Fvue%3Da",
    );
  });
});
