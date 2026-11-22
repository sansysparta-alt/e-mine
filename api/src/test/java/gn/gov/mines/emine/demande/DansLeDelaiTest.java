package gn.gov.mines.emine.demande;

import static gn.gov.mines.emine.support.Personas.INVESTISSEUR;
import static gn.gov.mines.emine.support.Personas.MINISTRE;
import static gn.gov.mines.emine.support.Personas.jeton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gn.gov.mines.emine.support.BaseDeDonneesDeTest;
import gn.gov.mines.emine.support.DemandesDeTest;
import gn.gov.mines.emine.support.Personas;
import gn.gov.mines.emine.support.TestIntegration;
import java.sql.Connection;
import java.sql.PreparedStatement;
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
 * US-05.6 : part des documents délivrés dans le délai du catalogue sur la période (règle unique des
 * délais), au total et par document ; les rejets sont exclus du pourcentage et comptés à part.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DansLeDelaiTest extends TestIntegration {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired MockMvc mvc;

  @BeforeAll
  void personas() throws Exception {
    Personas.charger();
  }

  @Test
  void laPartDesDocumentsDelivresDansLeDelaiExclutLesRejetsAffichesAPart() throws Exception {
    RequestPostProcessor ministre = jeton(MINISTRE, "MINISTRE");
    JsonNode avant = indicateurs(ministre, "mois");
    // Délivré dans le délai (1 jour) ; délivré hors délai (frais payés il y a 70 jours : 71 jours
    // pour un délai de 60) ; rejeté (hors du pourcentage, compté à part).
    long aTemps = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    DemandesDeTest.pretePourLaDelivrance(aTemps, MINISTRE, "FRAIS_DOSSIER", 5_000_000, 15_000_000L);
    DemandesDeTest.changerStatut(aTemps, "delivree");
    long enRetardDelivre = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    DemandesDeTest.pretePourLaDelivrance(
        enRetardDelivre, MINISTRE, "FRAIS_DOSSIER", 5_000_000, 15_000_000L);
    DemandesDeTest.fraisPayesIlYA(enRetardDelivre, 70);
    DemandesDeTest.changerStatut(enRetardDelivre, "delivree");
    long rejetee = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "UPDATE demande SET statut = 'rejetee', motif_rejet = 'Dossier incomplet.'"
                    + " WHERE id = ?")) {
      ps.setLong(1, rejetee);
      ps.executeUpdate();
    }

    JsonNode apres = indicateurs(ministre, "mois");
    assertThat(apres.path("delivrees").asLong() - avant.path("delivrees").asLong()).isEqualTo(2);
    assertThat(
            apres.path("delivreesDansLeDelai").asLong()
                - avant.path("delivreesDansLeDelai").asLong())
        .isEqualTo(1);
    assertThat(apres.path("rejetees").asLong() - avant.path("rejetees").asLong()).isEqualTo(1);
    // Le pourcentage est calculé par l'API, sur les seuls documents délivrés.
    assertThat(apres.path("tauxDansLeDelai").asInt())
        .isEqualTo(
            Math.round(
                100f
                    * apres.path("delivreesDansLeDelai").asLong()
                    / apres.path("delivrees").asLong()));
    long dansLeDelaiParDocument = 0;
    long rejeteesParDocument = 0;
    JsonNode pex = null;
    for (JsonNode d : apres.path("parDocument")) {
      dansLeDelaiParDocument += d.path("delivreesDansLeDelai").asLong();
      rejeteesParDocument += d.path("rejetees").asLong();
      if ("PEX".equals(d.path("code").asString())) {
        pex = d;
      }
    }
    assertThat(dansLeDelaiParDocument).isEqualTo(apres.path("delivreesDansLeDelai").asLong());
    assertThat(rejeteesParDocument).isEqualTo(apres.path("rejetees").asLong());
    assertThat(pex).isNotNull();
    assertThat(pex.path("delaiJours").asInt()).isEqualTo(60);
    assertThat(pex.path("tauxDansLeDelai").asInt())
        .isEqualTo(
            Math.round(
                100f * pex.path("delivreesDansLeDelai").asLong() / pex.path("delivrees").asLong()));
  }

  private JsonNode indicateurs(RequestPostProcessor qui, String periode) throws Exception {
    String corps =
        mvc.perform(get("/api/v1/demandes/indicateurs").param("periode", periode).with(qui))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JSON.readTree(corps);
  }
}
