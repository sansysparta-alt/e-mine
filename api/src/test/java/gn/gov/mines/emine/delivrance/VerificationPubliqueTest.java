package gn.gov.mines.emine.delivrance;

import static gn.gov.mines.emine.support.Personas.INVESTISSEUR;
import static gn.gov.mines.emine.support.Personas.MINISTRE;
import static gn.gov.mines.emine.support.Personas.jeton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gn.gov.mines.emine.support.DemandesDeTest;
import gn.gov.mines.emine.support.Personas;
import gn.gov.mines.emine.support.StockageEnMemoire;
import gn.gov.mines.emine.support.TestIntegration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * US-06.5 : n'importe qui vérifie un document par le code de son QR, sans connexion et sans donnée
 * personnelle : « AUTHENTIQUE », puis « NON_VALIDE » dès sa révocation ; un code inconnu ou mal
 * formé donne la même réponse (DOC-007).
 */
@AutoConfigureMockMvc
@Import(StockageEnMemoire.Config.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VerificationPubliqueTest extends TestIntegration {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired MockMvc mvc;

  @BeforeAll
  void personas() throws Exception {
    Personas.charger();
  }

  @Test
  void unDocumentDelivreEstAuthentiquePuisNonValideApresRevocation() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    DemandesDeTest.pretePourLaDelivrance(id, MINISTRE, "FRAIS_DOSSIER", 5_000_000, 15_000_000L);
    JsonNode document =
        JSON.readTree(
            mvc.perform(
                    post("/api/v1/demandes/" + id + "/delivrance")
                        .with(jeton(MINISTRE, "MINISTRE")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    String url = document.path("urlVerification").asString();
    String code = url.substring(url.lastIndexOf('/') + 1);

    String corps =
        mvc.perform(get("/api/v1/verification/" + code))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.statut").value("AUTHENTIQUE"))
            .andExpect(jsonPath("$.numero").value(document.path("numero").asString()))
            .andExpect(jsonPath("$.document").value("Permis d'exploitation"))
            .andExpect(jsonPath("$.signataire").value("Ministre"))
            .andExpect(
                jsonPath("$.empreinteSha256").value(document.path("empreinteSha256").asString()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(corps)
        .as("aucune donnée personnelle")
        .doesNotContain("Bangoura")
        .doesNotContain("Horizon")
        .doesNotContain("Soumah")
        .doesNotContain("DEM-")
        .doesNotContain("@");

    mvc.perform(
            post("/api/v1/demandes/" + id + "/document/revocation")
                .with(jeton(MINISTRE, "MINISTRE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motif\":\"Fraude constatée par la brigade de Siguiri.\"}"))
        .andExpect(status().isOk());
    String apres =
        mvc.perform(get("/api/v1/verification/" + code.toUpperCase()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statut").value("NON_VALIDE"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(apres).as("le motif n'est pas public").doesNotContain("Siguiri");
  }

  @Test
  void unCodeInconnuOuMalFormeDonneLaMemeReponseDoc007() throws Exception {
    mvc.perform(get("/api/v1/verification/0123456789abcdef0123456789abcdef"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("DOC-007"));
    mvc.perform(get("/api/v1/verification/pas-un-code"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("DOC-007"));
  }
}
