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
    List<Long> ids = ids(liste(INVESTISSEUR, "INVESTISSEUR", "?taille=100"));
    assertThat(ids).contains(brouillonA, deposeeA).doesNotContain(brouillonB);
    // La demande déposée (date de dépôt la plus récente) passe avant le brouillon créé avant elle.
    assertThat(ids.indexOf(deposeeA)).isLessThan(ids.indexOf(brouillonA));
    assertThat(ids(liste(INVESTISSEUR_2, "INVESTISSEUR", "?taille=100")))
        .contains(brouillonB)
        .doesNotContain(brouillonA, deposeeA);
  }

  @Test
  void laListeDonneNumeroEtStatut() throws Exception {
    JsonNode liste = liste(INVESTISSEUR, "INVESTISSEUR", "?taille=100");
    for (JsonNode d : liste.path("elements")) {
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
    assertThat(ids(liste(INVESTISSEUR, "INVESTISSEUR", "?statut=deposee&taille=100")))
        .contains(deposeeA)
        .doesNotContain(brouillonA);
    assertThat(ids(liste(INVESTISSEUR, "INVESTISSEUR", "?typeDocumentCode=PEX&taille=100")))
        .contains(brouillonA)
        .doesNotContain(deposeeA);
  }

  @Test
  void plusieursStatutsSeFiltrentEnUneFois() throws Exception {
    assertThat(ids(liste(INVESTISSEUR, "INVESTISSEUR", "?statut=brouillon,deposee&taille=100")))
        .contains(brouillonA, deposeeA);
    assertThat(ids(liste(INVESTISSEUR, "INVESTISSEUR", "?statut=recevable,delivree&taille=100")))
        .doesNotContain(brouillonA, deposeeA);
  }

  @Test
  void unAgentInterneVoitLesDemandesDeposeesMaisJamaisLesBrouillons() throws Exception {
    // Règle du socle (correctifs J2 bis), appliquée par l'API depuis les correctifs J5.
    assertThat(ids(liste(GUICHET, "GUICHET", "?taille=100")))
        .contains(deposeeA)
        .doesNotContain(brouillonA, brouillonB);
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
  void laListeEstPagineeSansLimiteFixe() throws Exception {
    for (int i = 0; i < 12; i++) {
      creer(INVESTISSEUR_2, "COR");
    }
    JsonNode p1 = liste(INVESTISSEUR_2, "INVESTISSEUR", "?taille=10&page=1");
    long total = p1.path("total").asLong();
    assertThat(total).isGreaterThanOrEqualTo(13);
    assertThat(p1.path("elements")).hasSize(10);
    assertThat(p1.path("page").asInt()).isEqualTo(1);
    assertThat(p1.path("taille").asInt()).isEqualTo(10);
    assertThat(p1.path("totalPages").asInt()).isEqualTo((int) ((total + 9) / 10));
    JsonNode p2 = liste(INVESTISSEUR_2, "INVESTISSEUR", "?taille=10&page=2");
    assertThat(ids(p2)).doesNotContainAnyElementsOf(ids(p1));
  }

  @Test
  void laTailleEtLaPageSontValidees() throws Exception {
    mvc.perform(get("/api/v1/demandes?taille=7").with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VAL-001"))
        .andExpect(jsonPath("$.details.champs[0].champ").value("taille"));
    mvc.perform(get("/api/v1/demandes?page=0").with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(jsonPath("$.details.champs[0].champ").value("page"));
  }

  @Test
  void laListeSeTrieEtSeCherche() throws Exception {
    JsonNode parStatut = liste(INVESTISSEUR, "INVESTISSEUR", "?tri=statut&sens=asc&taille=100");
    List<String> statuts = new ArrayList<>();
    parStatut.path("elements").forEach(d -> statuts.add(d.path("statut").asString()));
    assertThat(statuts.indexOf("brouillon")).isLessThan(statuts.indexOf("deposee"));
    String numero = "";
    for (JsonNode d : liste(INVESTISSEUR, "INVESTISSEUR", "?taille=100").path("elements")) {
      if (d.path("id").asLong() == deposeeA) numero = d.path("numero").asString();
    }
    assertThat(ids(liste(INVESTISSEUR, "INVESTISSEUR", "?q=" + numero.toLowerCase())))
        .containsExactly(deposeeA);
    assertThat(ids(liste(INVESTISSEUR, "INVESTISSEUR", "?q=exploitation&taille=100")))
        .contains(brouillonA)
        .doesNotContain(deposeeA);
    // Recherche tolérante : partie du numéro, accents et majuscules ignorés, mots dans le désordre,
    // libellé anglais, faute de frappe sur un mot ; un numéro voisin n'est pas retenu.
    String fin = numero.substring(numero.length() - 4);
    assertThat(ids(liste(INVESTISSEUR, "INVESTISSEUR", "?q=" + fin + "&taille=100")))
        .contains(deposeeA);
    for (String q :
        new String[] {"EXPLOITATION", "exploîtation permis", "Mining licence", "exploitaton"}) {
      assertThat(ids(liste(INVESTISSEUR, "INVESTISSEUR", "?q=" + q + "&taille=100")))
          .as(q)
          .contains(brouillonA)
          .doesNotContain(deposeeA);
    }
    String voisin = numero.substring(0, numero.length() - 1) + (numero.endsWith("9") ? "8" : "9");
    assertThat(ids(liste(INVESTISSEUR, "INVESTISSEUR", "?q=" + voisin + "&taille=100")))
        .doesNotContain(deposeeA);
    // Les jokers LIKE (% et _) sont pris littéralement : aucun numéro ni document n'en contient.
    for (String joker : new String[] {"%", "_"}) {
      String corps =
          mvc.perform(
                  get("/api/v1/demandes")
                      .param("q", joker)
                      .with(jeton(INVESTISSEUR, "INVESTISSEUR")))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThat(JSON.readTree(corps).path("total").asLong()).as("q=" + joker).isZero();
    }
  }

  @Test
  void lesCompteursParStatutSuiventLaRls() throws Exception {
    String corps =
        mvc.perform(get("/api/v1/demandes/compteurs").with(jeton(INVESTISSEUR, "INVESTISSEUR")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode c = JSON.readTree(corps);
    assertThat(c.path("deposee").asLong()).isGreaterThanOrEqualTo(1);
    assertThat(c.path("brouillon").asLong()).isGreaterThanOrEqualTo(1);
    long totalVisible = liste(INVESTISSEUR, "INVESTISSEUR", "").path("total").asLong();
    long somme = 0;
    for (var e : c.properties()) somme += e.getValue().asLong();
    assertThat(somme).isEqualTo(totalVisible);
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

  private static List<Long> ids(JsonNode page) {
    List<Long> ids = new ArrayList<>();
    page.path("elements").forEach(d -> ids.add(d.path("id").asLong()));
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
