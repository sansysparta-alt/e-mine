package gn.gov.mines.emine.paiement;

import static gn.gov.mines.emine.support.Personas.DIRECTEUR;
import static gn.gov.mines.emine.support.Personas.FISCAL;
import static gn.gov.mines.emine.support.Personas.GUICHET;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR;
import static gn.gov.mines.emine.support.Personas.jeton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gn.gov.mines.emine.support.BaseDeDonneesDeTest;
import gn.gov.mines.emine.support.DemandesDeTest;
import gn.gov.mines.emine.support.Personas;
import gn.gov.mines.emine.support.TestIntegration;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * US-04.11 : le Responsable fiscal consulte et exporte le journal des encaissements — recettes
 * (montant dû des paiements acceptés) et frais remboursés sur rejet, totaux calculés par l'API,
 * export CSV identique à l'écran ; ses indicateurs suivent les mêmes règles.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JournalEncaissementsTest extends TestIntegration {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired MockMvc mvc;

  private final String aujourdhui = LocalDate.now(ZoneId.of("Africa/Conakry")).toString();

  @BeforeAll
  void personas() throws Exception {
    Personas.charger();
  }

  @Test
  void lesRecettesDuJourEtLesFraisRemboursesFontLeSolde() throws Exception {
    JsonNode avant = totaux();
    long demandeA = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    DemandesDeTest.payer(demandeA, "FRAIS_DOSSIER", 5_000_000);
    long demandeB = DemandesDeTest.deposee(INVESTISSEUR, "EXC");
    DemandesDeTest.payer(demandeB, "ATTESTATION", 200_000);
    // Frais de la demande B remboursés sur rejet (ordre du Trésor enregistré).
    rembourserSurRejet(demandeB);

    JsonNode apres = totaux();
    assertThat(apres.path("encaisseGnf").asLong() - avant.path("encaisseGnf").asLong())
        .isEqualTo(5_200_000);
    assertThat(apres.path("rembourseGnf").asLong() - avant.path("rembourseGnf").asLong())
        .isEqualTo(200_000);
    assertThat(apres.path("soldeGnf").asLong() - avant.path("soldeGnf").asLong())
        .isEqualTo(5_000_000);
    assertThat(apres.path("nombre").asLong() - avant.path("nombre").asLong()).isEqualTo(3);

    // Le remboursement figure en négatif, le paiement avec son reçu.
    JsonNode page =
        lire(
            "/api/v1/encaissements/journal?du=" + aujourdhui + "&au=" + aujourdhui + "&taille=100");
    boolean remboursementNegatif = false;
    boolean recu = false;
    for (JsonNode l : page.path("elements")) {
      if ("remboursement".equals(l.path("type").asString())
          && l.path("montantGnf").asLong() == -200_000) {
        remboursementNegatif = true;
        assertThat(l.path("reference").asString()).startsWith("RMB-");
      }
      if ("encaissement".equals(l.path("type").asString())
          && l.path("piece").asString().startsWith("REC-")) {
        recu = true;
      }
    }
    assertThat(remboursementNegatif).isTrue();
    assertThat(recu).isTrue();

    // Filtre par objet : seules les attestations.
    JsonNode attestations =
        lire(
            "/api/v1/encaissements/journal?du="
                + aujourdhui
                + "&au="
                + aujourdhui
                + "&objet=ATTESTATION&taille=100");
    for (JsonNode l : attestations.path("elements")) {
      assertThat(l.path("objet").asString()).isEqualTo("ATTESTATION");
    }
  }

  @Test
  void lExportCsvReprendLesLignesEtLeTotalDeLEcran() throws Exception {
    long demande = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    DemandesDeTest.payer(demande, "FRAIS_DOSSIER", 5_000_000);
    JsonNode t = totaux();
    byte[] octets =
        mvc.perform(
                get("/api/v1/encaissements/journal.csv?du=" + aujourdhui + "&au=" + aujourdhui)
                    .with(fiscal()))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/csv"))
            .andExpect(
                header()
                    .string(
                        "Content-Disposition",
                        org.hamcrest.Matchers.containsString("journal-encaissements-du-")))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    String csv = new String(octets, StandardCharsets.UTF_8);
    assertThat(csv).startsWith("﻿date;type;reference;");
    String[] lignes = csv.strip().split("\n");
    // En-tête + une ligne par élément + total.
    assertThat(lignes).hasSize((int) t.path("nombre").asLong() + 2);
    assertThat(lignes[lignes.length - 1]).isEqualTo(";total;;;;;;;;" + t.path("soldeGnf").asLong());
  }

  @Test
  void lesIndicateursSuiventLesMemesReglesQueLesListes() throws Exception {
    JsonNode i = lire("/api/v1/encaissements/indicateurs");
    JsonNode aVerifier = lire("/api/v1/encaissements/a-verifier?taille=10");
    assertThat(i.path("aVerifier").asLong()).isEqualTo(aVerifier.path("total").asLong());
    JsonNode remboursements = lire("/api/v1/remboursements?statut=demande");
    assertThat(i.path("remboursementsAValider").asLong())
        .isEqualTo(remboursements.path("total").asLong());
    String du = i.path("debutMois").asString();
    String au = i.path("finMois").asString();
    JsonNode mois = lire("/api/v1/encaissements/journal?du=" + du + "&au=" + au);
    assertThat(i.path("mois").path("soldeGnf").asLong())
        .isEqualTo(mois.path("totaux").path("soldeGnf").asLong());
  }

  @Test
  void seulLeResponsableFiscalConsulteLeJournal() throws Exception {
    mvc.perform(get("/api/v1/encaissements/journal").with(jeton(GUICHET, "GUICHET")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/encaissements/journal?du=2026-11-30&au=2026-11-01").with(fiscal()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VAL-001"));
  }

  private JsonNode totaux() throws Exception {
    return lire("/api/v1/encaissements/journal?du=" + aujourdhui + "&au=" + aujourdhui)
        .path("totaux");
  }

  private JsonNode lire(String url) throws Exception {
    return JSON.readTree(
        mvc.perform(get(url).with(fiscal()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  /** Remboursement des frais sur rejet, validé puis exécuté (ordre du Trésor), hors RLS. */
  private static void rembourserSurRejet(long demande) throws Exception {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner()) {
      long auteur;
      String paiement;
      try (PreparedStatement ps =
          c.prepareStatement(
              "SELECT d.demandeur_id, p.id FROM demande d JOIN paiement p ON p.demande_id = d.id WHERE d.id = ?")) {
        ps.setLong(1, demande);
        ResultSet rs = ps.executeQuery();
        rs.next();
        auteur = rs.getLong(1);
        paiement = rs.getString(2);
      }
      try (PreparedStatement ps =
          c.prepareStatement(
              "INSERT INTO remboursement (paiement_id, motif, montant_gnf, demande_par)"
                  + " VALUES (?::uuid, 'rejet_demande', 200000, ?) RETURNING id")) {
        ps.setString(1, paiement);
        ps.setLong(2, auteur);
        ResultSet rs = ps.executeQuery();
        rs.next();
        long id = rs.getLong(1);
        // Validation par le Directeur, puis ordre enregistré par le Responsable fiscal (V34 : la
        // base contrôle le droit de chacun, sous sa session).
        long directeur = agir(c, DIRECTEUR);
        try (PreparedStatement maj =
            c.prepareStatement(
                "UPDATE remboursement SET statut = 'valide', valide_par = ?, date_validation = now()"
                    + " WHERE id = ?")) {
          maj.setLong(1, directeur);
          maj.setLong(2, id);
          maj.executeUpdate();
        }
        long fiscal = agir(c, FISCAL);
        try (PreparedStatement maj =
            c.prepareStatement(
                "UPDATE remboursement SET statut = 'execute', reference_ordre = 'OR-TEST-1',"
                    + " execute_par = ?, execute_le = now() WHERE id = ?")) {
          maj.setLong(1, fiscal);
          maj.setLong(2, id);
          maj.executeUpdate();
        }
      }
    }
  }

  /** Agit sous la session de cette persona (`emine.user_id`) ; renvoie son identifiant. */
  private static long agir(Connection c, String keycloakId) throws Exception {
    try (PreparedStatement ps =
        c.prepareStatement(
            "SELECT set_config('emine.user_id', id::text, false), id FROM utilisateur"
                + " WHERE keycloak_id = ?::uuid")) {
      ps.setString(1, keycloakId);
      ResultSet rs = ps.executeQuery();
      rs.next();
      return rs.getLong(2);
    }
  }

  private static RequestPostProcessor fiscal() {
    return jeton(FISCAL, "FISCAL");
  }
}
