package gn.gov.mines.emine.demande;

import static gn.gov.mines.emine.support.Personas.DIRECTEUR;
import static gn.gov.mines.emine.support.Personas.FISCAL;
import static gn.gov.mines.emine.support.Personas.GUICHET;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR_2;
import static gn.gov.mines.emine.support.Personas.MINISTRE;
import static gn.gov.mines.emine.support.Personas.jeton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gn.gov.mines.emine.support.DemandesDeTest;
import gn.gov.mines.emine.support.Personas;
import gn.gov.mines.emine.support.TestIntegration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * Règle « À traiter » unique (correctifs J5) : pour chaque profil, la liste filtrée ({@code
 * /demandes?aTraiter=true}), chaque groupe et le compteur ({@code /demandes/a-traiter}) donnent
 * exactement les mêmes nombres, et chaque dossier tombe chez le bon profil.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ATraiterTest extends TestIntegration {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired MockMvc mvc;

  private long fraisDus;
  private long droitsDus;
  private long aExaminer;
  private long pexADecider;
  private long carADecider;
  private long pexASigner;

  @BeforeAll
  void dossiers() throws Exception {
    Personas.charger();
    fraisDus = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    aExaminer = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    DemandesDeTest.payer(aExaminer, "FRAIS_DOSSIER", 5_000_000);
    pexADecider = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    DemandesDeTest.payer(pexADecider, "FRAIS_DOSSIER", 5_000_000);
    DemandesDeTest.changerStatut(pexADecider, "recevable");
    carADecider = DemandesDeTest.deposee(INVESTISSEUR, "CAR");
    DemandesDeTest.payer(carADecider, "FRAIS_DOSSIER", 1_000_000);
    DemandesDeTest.changerStatut(carADecider, "recevable");
    droitsDus = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    DemandesDeTest.payer(droitsDus, "FRAIS_DOSSIER", 5_000_000);
    DemandesDeTest.changerStatut(droitsDus, "recevable", "decision");
    pexASigner = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    DemandesDeTest.pretePourLaDelivrance(
        pexASigner, MINISTRE, "FRAIS_DOSSIER", 5_000_000, 15_000_000L);
  }

  @Test
  void lInvestisseurAPayerLesFraisEtLesDroitsDeSesSeulesDemandes() throws Exception {
    Map<String, List<Long>> groupes = verifierCoherence(jeton(INVESTISSEUR, "INVESTISSEUR"));
    assertThat(groupes.keySet()).containsExactly("payer-frais", "payer-droits", "completer");
    assertThat(groupes.get("payer-frais")).contains(fraisDus).doesNotContain(aExaminer);
    assertThat(groupes.get("payer-droits")).contains(droitsDus);
    Map<String, List<Long>> autre = verifierCoherence(jeton(INVESTISSEUR_2, "INVESTISSEUR"));
    assertThat(autre.get("payer-frais")).doesNotContain(fraisDus);
  }

  @Test
  void leGuichetExamineLesDemandesDontLesFraisSontPayes() throws Exception {
    Map<String, List<Long>> groupes = verifierCoherence(jeton(GUICHET, "GUICHET"));
    assertThat(groupes.keySet()).containsExactly("a-examiner");
    assertThat(groupes.get("a-examiner")).contains(aExaminer).doesNotContain(fraisDus, pexADecider);
  }

  @Test
  void leMinistreDecideEtSigneSesDocumentsEtPasCeuxDuGuichet() throws Exception {
    Map<String, List<Long>> groupes = verifierCoherence(jeton(MINISTRE, "MINISTRE"));
    assertThat(groupes.keySet()).containsExactly("a-decider", "a-signer");
    assertThat(groupes.get("a-decider"))
        .contains(pexADecider)
        .doesNotContain(carADecider, aExaminer);
    assertThat(groupes.get("a-signer")).contains(pexASigner);
  }

  @Test
  void leDirecteurNeDecideQueLesDocumentsQuIlSigne() throws Exception {
    Map<String, List<Long>> groupes = verifierCoherence(jeton(DIRECTEUR, "DIRECTEUR"));
    assertThat(groupes.get("a-decider")).contains(carADecider).doesNotContain(pexADecider);
    assertThat(groupes.get("a-signer")).doesNotContain(pexASigner);
  }

  @Test
  void lesVuesSontExclusivesEtExhaustivesPourChaqueProfil() throws Exception {
    for (RequestPostProcessor qui :
        List.of(
            jeton(INVESTISSEUR, "INVESTISSEUR"),
            jeton(GUICHET, "GUICHET"),
            jeton(MINISTRE, "MINISTRE"),
            jeton(DIRECTEUR, "DIRECTEUR"),
            jeton(FISCAL, "FISCAL"))) {
      JsonNode vues = lire(get("/api/v1/demandes/vues"), qui);
      long toutes = lire(get("/api/v1/demandes").param("taille", "10"), qui).path("total").asLong();
      assertThat(vues.path("total").asLong()).as("somme des vues = toutes").isEqualTo(toutes);
      java.util.Set<Long> vus = new java.util.HashSet<>();
      long somme = 0;
      for (JsonNode v : vues.path("vues")) {
        JsonNode page =
            lire(
                get("/api/v1/demandes")
                    .param("vue", v.path("cle").asString())
                    .param("taille", "100"),
                qui);
        assertThat(page.path("total").asLong()).isEqualTo(v.path("nombre").asLong());
        for (JsonNode e : page.path("elements")) {
          assertThat(vus.add(e.path("id").asLong())).as("une seule vue par demande").isTrue();
        }
        somme += v.path("nombre").asLong();
      }
      assertThat(somme).isEqualTo(toutes);
    }
  }

  @Test
  void enCoursNeRepetePasCeQueLeMinistreDoitTraiter() throws Exception {
    List<Long> enCours = new ArrayList<>();
    lire(
            get("/api/v1/demandes").param("vue", "en-cours").param("taille", "100"),
            jeton(MINISTRE, "MINISTRE"))
        .path("elements")
        .forEach(e -> enCours.add(e.path("id").asLong()));
    assertThat(enCours).contains(carADecider, aExaminer).doesNotContain(pexADecider, pexASigner);
  }

  @Test
  void unAgentNeVoitJamaisLesBrouillons() throws Exception {
    long brouillon = DemandesDeTest.brouillon(INVESTISSEUR, "PRE");
    mvc.perform(
            get("/api/v1/demandes").param("statut", "brouillon").with(jeton(GUICHET, "GUICHET")))
        .andExpect(jsonPath("$.total").value(0));
    mvc.perform(get("/api/v1/demandes/compteurs").with(jeton(GUICHET, "GUICHET")))
        .andExpect(jsonPath("$.brouillon").doesNotExist());
    List<Long> siens = new ArrayList<>();
    lire(
            get("/api/v1/demandes").param("vue", "brouillons").param("taille", "100"),
            jeton(INVESTISSEUR, "INVESTISSEUR"))
        .path("elements")
        .forEach(e -> siens.add(e.path("id").asLong()));
    assertThat(siens).contains(brouillon);
  }

  @Test
  void chaqueDemandeDitSiElleEstATraiterParLeProfilConnecte() throws Exception {
    mvc.perform(get("/api/v1/demandes/" + pexADecider).with(jeton(MINISTRE, "MINISTRE")))
        .andExpect(jsonPath("$.aTraiterParMoi").value(true));
    mvc.perform(get("/api/v1/demandes/" + pexADecider).with(jeton(DIRECTEUR, "DIRECTEUR")))
        .andExpect(jsonPath("$.aTraiterParMoi").value(false));
    mvc.perform(get("/api/v1/demandes/" + fraisDus).with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(jsonPath("$.aTraiterParMoi").value(true));
    mvc.perform(get("/api/v1/demandes/" + fraisDus).with(jeton(GUICHET, "GUICHET")))
        .andExpect(jsonPath("$.aTraiterParMoi").value(false));
    JsonNode liste =
        lire(
            get("/api/v1/demandes").param("aTraiter", "true").param("taille", "100"),
            jeton(GUICHET, "GUICHET"));
    liste.path("elements").forEach(e -> assertThat(e.path("aTraiterParMoi").asBoolean()).isTrue());
  }

  @Test
  void unProfilSansActionNARienATraiter() throws Exception {
    mvc.perform(get("/api/v1/demandes/a-traiter").with(jeton(FISCAL, "FISCAL")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0))
        .andExpect(jsonPath("$.groupes.length()").value(0));
    mvc.perform(get("/api/v1/demandes").param("aTraiter", "true").with(jeton(FISCAL, "FISCAL")))
        .andExpect(jsonPath("$.total").value(0));
  }

  @Test
  void unGroupeQuiNestPasCeluiDuProfilEstRefuseVal001() throws Exception {
    mvc.perform(get("/api/v1/demandes").param("groupe", "a-signer").with(jeton(GUICHET, "GUICHET")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VAL-001"));
  }

  /**
   * Vérifie, pour le profil, que compteur, liste « à traiter » et listes par groupe donnent les
   * mêmes nombres ; renvoie les identifiants de chaque groupe.
   */
  private Map<String, List<Long>> verifierCoherence(RequestPostProcessor qui) throws Exception {
    JsonNode compteur = lire(get("/api/v1/demandes/a-traiter"), qui);
    JsonNode liste =
        lire(get("/api/v1/demandes").param("aTraiter", "true").param("taille", "100"), qui);
    assertThat(liste.path("total").asLong())
        .as("liste « à traiter » = compteur")
        .isEqualTo(compteur.path("total").asLong());
    Map<String, List<Long>> groupes = new java.util.LinkedHashMap<>();
    long somme = 0;
    for (JsonNode g : compteur.path("groupes")) {
      String cle = g.path("cle").asString();
      JsonNode page =
          lire(get("/api/v1/demandes").param("groupe", cle).param("taille", "100"), qui);
      assertThat(page.path("total").asLong())
          .as("groupe %s", cle)
          .isEqualTo(g.path("nombre").asLong());
      List<Long> ids = new ArrayList<>();
      page.path("elements").forEach(e -> ids.add(e.path("id").asLong()));
      groupes.put(cle, ids);
      somme += g.path("nombre").asLong();
    }
    assertThat(somme).as("somme des groupes = total").isEqualTo(compteur.path("total").asLong());
    return groupes;
  }

  private JsonNode lire(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requete,
      RequestPostProcessor qui)
      throws Exception {
    return JSON.readTree(
        mvc.perform(requete.with(qui))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }
}
