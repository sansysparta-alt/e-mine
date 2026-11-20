package gn.gov.mines.emine.demande;

import static gn.gov.mines.emine.support.Personas.GUICHET;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR_2;
import static gn.gov.mines.emine.support.Personas.jeton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gn.gov.mines.emine.support.BaseDeDonneesDeTest;
import gn.gov.mines.emine.support.Personas;
import gn.gov.mines.emine.support.TestIntegration;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * US-03.5 : l'usager consulte la liste de ses demandes et leur statut, filtrable ; la RLS ne lui
 * montre que les siennes ; un agent interne les voit toutes.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MesDemandesTest extends TestIntegration {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired MockMvc mvc;

  private long brouillonA;
  private long deposeeA;
  private long brouillonB;

  @BeforeAll
  void donnees() throws Exception {
    Personas.charger();
    brouillonA = creer(INVESTISSEUR, "PEX");
    deposeeA = creer(INVESTISSEUR, "COR");
    brouillonB = creer(INVESTISSEUR_2, "PRE");
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        Statement st = c.createStatement()) {
      st.executeUpdate("UPDATE demande SET statut = 'deposee' WHERE id = " + deposeeA);
    }
  }

  @Test
  void lInvestisseurNeVoitQueSesDemandesLaPlusRecenteDAbord() throws Exception {
    List<Long> ids = ids(liste(INVESTISSEUR, "INVESTISSEUR", ""));
    assertThat(ids).contains(brouillonA, deposeeA).doesNotContain(brouillonB);
    // La demande déposée (date de dépôt la plus récente) passe avant le brouillon créé avant elle.
    assertThat(ids.indexOf(deposeeA)).isLessThan(ids.indexOf(brouillonA));
    assertThat(ids(liste(INVESTISSEUR_2, "INVESTISSEUR", "")))
        .contains(brouillonB)
        .doesNotContain(brouillonA, deposeeA);
  }

  @Test
  void laListeDonneNumeroEtStatut() throws Exception {
    JsonNode liste = liste(INVESTISSEUR, "INVESTISSEUR", "");
    for (JsonNode d : liste) {
      if (d.path("id").asLong() == deposeeA) {
        assertThat(d.path("statut").asString()).isEqualTo("deposee");
        assertThat(d.path("numero").asString()).startsWith("DEM-");
      }
      if (d.path("id").asLong() == brouillonA) {
        assertThat(d.path("statut").asString()).isEqualTo("brouillon");
        assertThat(d.path("numero").isNull()).isTrue();
      }
    }
  }

  @Test
  void laListeSeFiltreParStatutEtParDocument() throws Exception {
    assertThat(ids(liste(INVESTISSEUR, "INVESTISSEUR", "?statut=deposee")))
        .contains(deposeeA)
        .doesNotContain(brouillonA);
    assertThat(ids(liste(INVESTISSEUR, "INVESTISSEUR", "?typeDocumentCode=PEX")))
        .contains(brouillonA)
        .doesNotContain(deposeeA);
  }

  @Test
  void unAgentInterneVoitToutesLesDemandes() throws Exception {
    assertThat(ids(liste(GUICHET, "GUICHET", ""))).contains(brouillonA, deposeeA, brouillonB);
  }

  @Test
  void unFiltreInvalideEstRefuse() throws Exception {
    mvc.perform(get("/api/v1/demandes?statut=inconnu").with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VAL-001"));
    mvc.perform(
            get("/api/v1/demandes?statut=DROP%20TABLE").with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VAL-001"));
  }

  @Test
  void sansConnexionLaListeEstRefusee() throws Exception {
    mvc.perform(get("/api/v1/demandes"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH-001"));
  }

  private JsonNode liste(String qui, String role, String filtre) throws Exception {
    String corps =
        mvc.perform(get("/api/v1/demandes" + filtre).with(jeton(qui, role)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JSON.readTree(corps);
  }

  private static List<Long> ids(JsonNode liste) {
    List<Long> ids = new ArrayList<>();
    liste.forEach(d -> ids.add(d.path("id").asLong()));
    return ids;
  }

  private long creer(String qui, String type) throws Exception {
    String corps =
        mvc.perform(
                post("/api/v1/demandes")
                    .with(jeton(qui, "INVESTISSEUR"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"typeDocumentCode\": \"" + type + "\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JSON.readTree(corps).path("id").asLong();
  }
}
