package gn.gov.mines.emine.demande;

import static gn.gov.mines.emine.support.Personas.GUICHET;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR;
import static gn.gov.mines.emine.support.Personas.MINISTRE;
import static gn.gov.mines.emine.support.Personas.jeton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gn.gov.mines.emine.support.DemandesDeTest;
import gn.gov.mines.emine.support.Personas;
import gn.gov.mines.emine.support.TestIntegration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * « Passer ce dossier » (correctifs ergonomie, V11) : passage tracé avec un motif, visible de tous
 * les agents, « Bloqué » au 3e passage à la même étape, remis à zéro quand le dossier avance.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PassageTest extends TestIntegration {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired MockMvc mvc;

  @BeforeAll
  void personas() throws Exception {
    Personas.charger();
  }

  @Test
  void lePassageEstTraceAvecSonMotifEtVisibleDeTousLesAgents() throws Exception {
    long id = aExaminer();
    passer(id, jeton(GUICHET, "GUICHET"), "Pièce illisible, rappel du demandeur prévu")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.aLEtape").value(1))
        .andExpect(jsonPath("$.bloque").value(false));
    JsonNode vu = lire("/api/v1/demandes/" + id + "/passages", jeton(MINISTRE, "MINISTRE"));
    assertThat(vu.path("passages")).hasSize(1);
    JsonNode passage = vu.path("passages").get(0);
    assertThat(passage.path("motif").asString())
        .isEqualTo("Pièce illisible, rappel du demandeur prévu");
    assertThat(passage.path("statut").asString()).isEqualTo("deposee");
    assertThat(passage.path("auteur").asString()).isNotBlank();
    assertThat(lire("/api/v1/demandes/" + id, jeton(MINISTRE, "MINISTRE")).path("passages").asInt())
        .isEqualTo(1);
  }

  @Test
  void auTroisiemePassageALaMemeEtapeLeDossierEstBloque() throws Exception {
    RequestPostProcessor guichet = jeton(GUICHET, "GUICHET");
    RequestPostProcessor ministre = jeton(MINISTRE, "MINISTRE");
    long bloquesAvant = lire("/api/v1/demandes/indicateurs", ministre).path("bloques").asLong();
    long id = aExaminer();
    passer(id, guichet, "Attente d'un avis").andExpect(jsonPath("$.bloque").value(false));
    passer(id, guichet, "Attente d'un avis").andExpect(jsonPath("$.bloque").value(false));
    passer(id, guichet, "Toujours en attente")
        .andExpect(jsonPath("$.aLEtape").value(3))
        .andExpect(jsonPath("$.bloque").value(true));

    assertThat(lire("/api/v1/demandes/" + id, ministre).path("bloque").asBoolean()).isTrue();
    JsonNode indicateurs = lire("/api/v1/demandes/indicateurs", ministre);
    assertThat(indicateurs.path("bloques").asLong() - bloquesAvant).isEqualTo(1);
    JsonNode liste = lire("/api/v1/demandes?bloque=true&taille=100", ministre);
    assertThat(liste.path("total").asLong()).isEqualTo(indicateurs.path("bloques").asLong());
    assertThat(liste.path("elements").findValuesAsString("numero"))
        .contains(lire("/api/v1/demandes/" + id, ministre).path("numero").asString());

    // Le dossier avance : nouvelle étape, compteur remis à zéro (l'historique reste).
    DemandesDeTest.changerStatut(id, "recevable");
    JsonNode apres = lire("/api/v1/demandes/" + id + "/passages", ministre);
    assertThat(apres.path("aLEtape").asInt()).isZero();
    assertThat(apres.path("bloque").asBoolean()).isFalse();
    assertThat(apres.path("passages")).hasSize(3);
  }

  @Test
  void unMotifEstObligatoire() throws Exception {
    passer(aExaminer(), jeton(GUICHET, "GUICHET"), " ").andExpect(status().isBadRequest());
  }

  @Test
  void unUsagerNePassePasNiNeLitLesPassages() throws Exception {
    long id = aExaminer();
    passer(id, jeton(INVESTISSEUR, "INVESTISSEUR"), "Je veux passer")
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/v1/demandes/" + id + "/passages").with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(status().isForbidden());
    assertThat(
            lire("/api/v1/demandes/" + id, jeton(INVESTISSEUR, "INVESTISSEUR"))
                .path("passages")
                .asInt())
        .isZero();
  }

  @Test
  void unDossierTermineNeSePassePas() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    DemandesDeTest.pretePourLaDelivrance(id, MINISTRE, "FRAIS_DOSSIER", 5_000_000, 15_000_000L);
    DemandesDeTest.changerStatut(id, "delivree");
    passer(id, jeton(GUICHET, "GUICHET"), "Trop tard").andExpect(status().isConflict());
  }

  private static long aExaminer() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    DemandesDeTest.payer(id, "FRAIS_DOSSIER", 5_000_000);
    return id;
  }

  private ResultActions passer(long id, RequestPostProcessor qui, String motif) throws Exception {
    return mvc.perform(
        post("/api/v1/demandes/" + id + "/passages")
            .with(qui)
            .contentType(MediaType.APPLICATION_JSON)
            .content(JSON.writeValueAsString(java.util.Map.of("motif", motif))));
  }

  private JsonNode lire(String adresse, RequestPostProcessor qui) throws Exception {
    String corps =
        mvc.perform(get(adresse).with(qui))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JSON.readTree(corps);
  }
}
