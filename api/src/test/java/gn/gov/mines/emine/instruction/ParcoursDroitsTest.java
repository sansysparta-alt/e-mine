package gn.gov.mines.emine.instruction;

import static gn.gov.mines.emine.support.Personas.GUICHET;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR;
import static gn.gov.mines.emine.support.Personas.MINISTRE;
import static gn.gov.mines.emine.support.Personas.jeton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gn.gov.mines.emine.paiement.domaine.SignatureTresorPay;
import gn.gov.mines.emine.support.DemandesDeTest;
import gn.gov.mines.emine.support.Personas;
import gn.gov.mines.emine.support.TestIntegration;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

/**
 * US-05.5 : après une décision favorable, le demandeur paie les droits (réutilise le paiement du
 * jour 3). Parcours complet par l'API, comme dans la démonstration : frais payés par TrésorPay →
 * recevable (guichet) → décision favorable (Ministre) → droits du barème → paiement → « prête pour
 * la délivrance » et reçu des droits.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ParcoursDroitsTest extends TestIntegration {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String NOTIFICATION = "/api/v1/paiements/notifications/tresorpay";

  @Autowired MockMvc mvc;

  @BeforeAll
  void personas() throws Exception {
    Personas.charger();
  }

  @Test
  void apresUneDecisionFavorableLeDemandeurPaieLesDroitsEtObtientSonRecu() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    for (String p : new String[] {"Étude de faisabilité", "EIES", "Plan de restauration"}) {
      DemandesDeTest.piece(id, p);
    }
    notifierPaye(payerEnLigne(id), 5_000_000);

    mvc.perform(
            post("/api/v1/demandes/" + id + "/recevabilite")
                .with(jeton(GUICHET, "GUICHET"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recevable\":true}"))
        .andExpect(status().isOk());

    // Avant la décision, aucun droit n'est attendu.
    mvc.perform(get("/api/v1/demandes/" + id + "/paiements/a-payer").with(investisseur()))
        .andExpect(jsonPath("$.objet").value(nullValue()));
    choisirTresorPay(id)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PAY-007"));

    mvc.perform(
            post("/api/v1/demandes/" + id + "/decision")
                .with(jeton(MINISTRE, "MINISTRE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"favorable\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.statut").value("decision"));

    mvc.perform(get("/api/v1/demandes/" + id + "/paiements/a-payer").with(investisseur()))
        .andExpect(jsonPath("$.objet").value("DROITS"))
        .andExpect(jsonPath("$.montantGnf").value(15_000_000));

    String droits = payerEnLigne(id);
    notifierPaye(droits, 15_000_000);

    mvc.perform(get("/api/v1/demandes/" + id).with(investisseur()))
        .andExpect(jsonPath("$.statut").value("droits_payes"));
    mvc.perform(get("/api/v1/demandes/" + id + "/paiements/a-payer").with(investisseur()))
        .andExpect(jsonPath("$.objet").value(nullValue()));
    byte[] recu =
        mvc.perform(get("/api/v1/paiements/" + droits + "/recu").with(investisseur()))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/pdf"))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertThat(new String(recu, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    assertThat(DemandesDeTest.statutPaiement(droits)).isEqualTo("paye");
  }

  @Test
  void unAutreDemandeurNePeutPasPayerLesDroitsDeLaDemande() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    DemandesDeTest.payer(id, "FRAIS_DOSSIER", 5_000_000);
    DemandesDeTest.changerStatut(id, "recevable", "decision");
    mvc.perform(
            get("/api/v1/demandes/" + id + "/paiements/a-payer")
                .with(jeton(Personas.INVESTISSEUR_2, "INVESTISSEUR")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("DEM-002"));
  }

  private ResultActions choisirTresorPay(long id) throws Exception {
    return mvc.perform(
        post("/api/v1/demandes/" + id + "/paiements")
            .with(investisseur())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"moyen\":\"TRESORPAY\"}"));
  }

  private String payerEnLigne(long id) throws Exception {
    String reponse =
        choisirTresorPay(id)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JSON.readTree(reponse).path("paiement").path("reference").asString();
  }

  /** Notification TrésorPay signée « payé » pour cette référence et ce montant. */
  private void notifierPaye(String reference, long montant) throws Exception {
    byte[] corps =
        ("{\"transactionId\":\"TP-"
                + UUID.randomUUID()
                + "\",\"reference\":\""
                + reference
                + "\",\"statut\":\"PAYE\",\"montantGnf\":"
                + montant
                + ",\"date\":\""
                + Instant.now()
                + "\"}")
            .getBytes(StandardCharsets.UTF_8);
    String horodatage = Long.toString(Instant.now().getEpochSecond());
    mvc.perform(
            post(NOTIFICATION)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-TresorPay-Marchand", MARCHAND_TRESORPAY)
                .header("X-TresorPay-Horodatage", horodatage)
                .header(
                    "X-TresorPay-Signature",
                    SignatureTresorPay.signer(SECRET_TRESORPAY, horodatage, corps))
                .content(corps))
        .andExpect(status().isOk());
  }

  private static RequestPostProcessor investisseur() {
    return jeton(INVESTISSEUR, "INVESTISSEUR");
  }
}
