package gn.gov.mines.emine.paiement;

import static gn.gov.mines.emine.support.Personas.INVESTISSEUR;
import static gn.gov.mines.emine.support.Personas.jeton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gn.gov.mines.emine.paiement.domaine.PaiementGateway.DemandeSession;
import gn.gov.mines.emine.support.DemandesDeTest;
import gn.gov.mines.emine.support.PasserelleDeTest;
import gn.gov.mines.emine.support.Personas;
import gn.gov.mines.emine.support.TestIntegration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/**
 * US-04.3 : le demandeur paie par TrésorPay. La session est ouverte côté serveur (montant du
 * barème, adresse de retour fixée par e-mine), le paiement passe en attente de la notification ; 3
 * tentatives par objet et par jour ; TrésorPay indisponible → PAY-003 sans consommer de tentative.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PaiementEnLigneTest extends TestIntegration {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired MockMvc mvc;
  @Autowired PasserelleDeTest passerelle;

  @BeforeAll
  void personas() throws Exception {
    Personas.charger();
  }

  @AfterEach
  void retablir() {
    passerelle.definirIndisponible(false);
  }

  @Test
  void laSessionEstOuverteAvecLeMontantDuBaremeEtLAdresseDeRetourDEmine() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    String reponse =
        payer(id)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.paiement.statut").value("en_attente"))
            .andExpect(jsonPath("$.urlPaiement").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String reference = JSON.readTree(reponse).path("paiement").path("reference").asString();
    DemandeSession envoyee =
        passerelle.recues().stream()
            .filter(s -> s.reference().equals(reference))
            .findFirst()
            .orElseThrow();
    assertThat(envoyee.montantGnf()).isEqualTo(5_000_000L);
    assertThat(envoyee.libelle()).startsWith("Frais de dossier");
    assertThat(envoyee.urlRetour()).endsWith("/demandes/" + id + "/paiement/retour");
    assertThat(JSON.readTree(reponse).path("urlPaiement").asString())
        .isEqualTo("http://tresorpay.test/payer/session-" + reference);
    assertThat(DemandesDeTest.statutPaiement(reference)).isEqualTo("en_attente");
  }

  @Test
  void tresorPayIndisponibleDonnePay003SansConsommerDeTentative() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    passerelle.definirIndisponible(true);
    payer(id)
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("PAY-003"));
    mvc.perform(get("/api/v1/demandes/" + id + "/paiements").with(investisseur()))
        .andExpect(jsonPath("$[0].statut").value("annule"));
    mvc.perform(get("/api/v1/demandes/" + id + "/paiements/a-payer").with(investisseur()))
        .andExpect(jsonPath("$.tentativesRestantes").value(3))
        .andExpect(jsonPath("$.referenceOuverte").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  void troisTentativesParJourPuisPay002() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    for (int i = 0; i < 3; i++) {
      payer(id).andExpect(status().isCreated());
    }
    mvc.perform(get("/api/v1/demandes/" + id + "/paiements/a-payer").with(investisseur()))
        .andExpect(jsonPath("$.tentativesRestantes").value(0));
    payer(id)
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("PAY-002"))
        .andExpect(jsonPath("$.details.prochaineTentative").isNotEmpty());
  }

  @Test
  void lesTentativesSeComptentParDemande() throws Exception {
    long premiere = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    for (int i = 0; i < 3; i++) {
      payer(premiere);
    }
    long autre = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    payer(autre).andExpect(status().isCreated());
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor investisseur() {
    return jeton(INVESTISSEUR, "INVESTISSEUR");
  }

  private ResultActions payer(long id) throws Exception {
    return mvc.perform(
        post("/api/v1/demandes/" + id + "/paiements")
            .with(investisseur())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"moyen\":\"TRESORPAY\"}"));
  }
}
