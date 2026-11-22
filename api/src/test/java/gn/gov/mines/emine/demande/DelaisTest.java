package gn.gov.mines.emine.demande;

import static gn.gov.mines.emine.support.Personas.GUICHET;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR;
import static gn.gov.mines.emine.support.Personas.jeton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gn.gov.mines.emine.demande.domaine.RegleDelais;
import gn.gov.mines.emine.support.DemandesDeTest;
import gn.gov.mines.emine.support.Personas;
import gn.gov.mines.emine.support.TestIntegration;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
 * US-05.6 : chaque dossier en cours indique les jours restants sur le délai du catalogue (hors
 * complément) ; les dossiers proches du dépassement (20 % du délai, 2 jours au moins) se filtrent
 * et se comptent avec les mêmes chiffres partout.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DelaisTest extends TestIntegration {

  @Autowired MockMvc mvc;
  final JsonMapper json = JsonMapper.builder().build();

  long pexProche;
  long pexLarge;
  long pexEnRetard;
  long corProche;
  long corLarge;
  long pexSuspendu;

  @BeforeAll
  void donnees() throws Exception {
    Personas.charger();
    pexProche = deposeeIlYA("PEX", 50); // 60 j : reste 10 ≤ 12
    pexLarge = deposeeIlYA("PEX", 40); // reste 20
    pexEnRetard = deposeeIlYA("PEX", 61); // reste -1
    corProche = deposeeIlYA("COR", 3); // 5 j : reste 2 ≤ 2
    corLarge = deposeeIlYA("COR", 2); // reste 3
    pexSuspendu = deposeeIlYA("PEX", 50);
    DemandesDeTest.complementOuvert(pexSuspendu, GUICHET, "deposee", 10); // reste 60-50+10 = 20
  }

  @Test
  void leSeuilVautVingtPourCentDuDelaiEtDeuxJoursAuMoins() {
    assertThat(RegleDelais.seuilProcheJours(60)).isEqualTo(12);
    assertThat(RegleDelais.seuilProcheJours(45)).isEqualTo(9);
    assertThat(RegleDelais.seuilProcheJours(5)).isEqualTo(2);
    assertThat(RegleDelais.seuilProcheJours(2)).isEqualTo(2);
  }

  @Test
  void chaqueDossierIndiqueSesJoursRestantsEtSonEtat() throws Exception {
    verifier(pexProche, 10, true, false, false);
    verifier(pexLarge, 20, false, false, false);
    verifier(pexEnRetard, -1, false, true, false);
    verifier(corProche, 2, true, false, false);
    verifier(corLarge, 3, false, false, false);
  }

  @Test
  void pendantUnComplementLeDelaiEstSuspenduEtNestPasSignale() throws Exception {
    verifier(pexSuspendu, 20, false, false, true);
  }

  @Test
  void unDossierTermineOuUnBrouillonNAPasDeJoursRestants() throws Exception {
    long brouillon = DemandesDeTest.brouillon(INVESTISSEUR, "PEX");
    mvc.perform(get("/api/v1/demandes/" + brouillon).with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(jsonPath("$.joursRestants").isEmpty())
        .andExpect(jsonPath("$.procheDepassement").value(false));
  }

  @Test
  void leFiltreEtLIndicateurDonnentLeMemeNombre() throws Exception {
    JsonNode page =
        lire(
            get("/api/v1/demandes").param("procheDepassement", "true").param("taille", "100"),
            guichet());
    List<Long> ids = new ArrayList<>();
    page.path("elements").forEach(e -> ids.add(e.path("id").asLong()));
    assertThat(ids)
        .contains(pexProche, corProche)
        .doesNotContain(pexLarge, pexEnRetard, corLarge, pexSuspendu);
    page.path("elements")
        .forEach(e -> assertThat(e.path("procheDepassement").asBoolean()).isTrue());
    JsonNode indicateurs = lire(get("/api/v1/demandes/indicateurs"), guichet());
    assertThat(indicateurs.path("procheDepassement").asLong())
        .as("indicateur = liste filtrée")
        .isEqualTo(page.path("total").asLong());
  }

  private void verifier(long id, int restants, boolean proche, boolean enRetard, boolean suspendu)
      throws Exception {
    mvc.perform(get("/api/v1/demandes/" + id).with(guichet()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.joursRestants").value(restants))
        .andExpect(jsonPath("$.procheDepassement").value(proche))
        .andExpect(jsonPath("$.enRetard").value(enRetard))
        .andExpect(jsonPath("$.delaiSuspendu").value(suspendu));
  }

  private long deposeeIlYA(String type, int jours) throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, type);
    DemandesDeTest.deposeeIlYA(id, jours);
    return id;
  }

  private JsonNode lire(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requete,
      RequestPostProcessor qui)
      throws Exception {
    String corps =
        mvc.perform(requete.with(qui))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    return json.readTree(corps);
  }

  private static RequestPostProcessor guichet() {
    return jeton(GUICHET, "GUICHET");
  }
}
