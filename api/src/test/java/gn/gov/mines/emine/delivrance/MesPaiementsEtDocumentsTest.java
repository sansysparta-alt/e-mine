package gn.gov.mines.emine.delivrance;

import static gn.gov.mines.emine.support.Personas.INVESTISSEUR;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR_2;
import static gn.gov.mines.emine.support.Personas.MINISTRE;
import static gn.gov.mines.emine.support.Personas.jeton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gn.gov.mines.emine.support.DemandesDeTest;
import gn.gov.mines.emine.support.Personas;
import gn.gov.mines.emine.support.StockageEnMemoire;
import gn.gov.mines.emine.support.TestIntegration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * « Paiements & reçus » et « Documents délivrés » (espace de l'investisseur) : chaque usager ne
 * voit que ses paiements et ses documents (RLS de la demande), du plus récent au plus ancien.
 */
@AutoConfigureMockMvc
@Import(StockageEnMemoire.Config.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MesPaiementsEtDocumentsTest extends TestIntegration {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired MockMvc mvc;

  private long demande;

  @BeforeAll
  void donnees() throws Exception {
    Personas.charger();
    demande = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    DemandesDeTest.pretePourLaDelivrance(
        demande, MINISTRE, "FRAIS_DOSSIER", 5_000_000, 15_000_000L);
    mvc.perform(
            post("/api/v1/demandes/" + demande + "/delivrance").with(jeton(MINISTRE, "MINISTRE")))
        .andExpect(status().isOk());
  }

  @Test
  void lUsagerVoitSesPaiementsAvecLeNumeroDeSaDemande() throws Exception {
    JsonNode page = lire("/api/v1/paiements?taille=100", jeton(INVESTISSEUR, "INVESTISSEUR"));
    List<Long> demandes = new ArrayList<>();
    page.path("elements").forEach(l -> demandes.add(l.path("paiement").path("demandeId").asLong()));
    assertThat(demandes).contains(demande);
    JsonNode ligne = page.path("elements").get(0);
    assertThat(ligne.path("numeroDemande").asString()).startsWith("DEM-");
    assertThat(ligne.path("codeDocument").asString()).isNotBlank();
    assertThat(
            ids(lire("/api/v1/paiements?taille=100", jeton(INVESTISSEUR_2, "INVESTISSEUR")), true))
        .doesNotContain(demande);
  }

  @Test
  void lUsagerVoitSesDocumentsDelivresEtPasCeuxDesAutres() throws Exception {
    JsonNode page =
        lire("/api/v1/documents-delivres?taille=100", jeton(INVESTISSEUR, "INVESTISSEUR"));
    assertThat(ids(page, false)).contains(demande);
    JsonNode document = page.path("elements").get(0);
    assertThat(document.path("valide").asBoolean()).isTrue();
    assertThat(document.path("urlVerification").asString()).contains("/verifier/");
    assertThat(
            ids(
                lire(
                    "/api/v1/documents-delivres?taille=100", jeton(INVESTISSEUR_2, "INVESTISSEUR")),
                false))
        .doesNotContain(demande);
  }

  private static List<Long> ids(JsonNode page, boolean paiements) {
    List<Long> ids = new ArrayList<>();
    page.path("elements")
        .forEach(
            l ->
                ids.add(
                    paiements
                        ? l.path("paiement").path("demandeId").asLong()
                        : l.path("demandeId").asLong()));
    return ids;
  }

  private JsonNode lire(String adresse, RequestPostProcessor qui) throws Exception {
    return JSON.readTree(
        mvc.perform(get(adresse).with(qui))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }
}
