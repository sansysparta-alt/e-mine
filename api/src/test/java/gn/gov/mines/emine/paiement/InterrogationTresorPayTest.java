package gn.gov.mines.emine.paiement;

import static gn.gov.mines.emine.support.Personas.INVESTISSEUR;
import static gn.gov.mines.emine.support.Personas.jeton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gn.gov.mines.emine.paiement.application.ServiceInterrogationTresorPay;
import gn.gov.mines.emine.paiement.domaine.PaiementGateway.EtatTransaction;
import gn.gov.mines.emine.paiement.domaine.PaiementGateway.StatutTransaction;
import gn.gov.mines.emine.support.BaseDeDonneesDeTest;
import gn.gov.mines.emine.support.DemandesDeTest;
import gn.gov.mines.emine.support.PasserelleDeTest;
import gn.gov.mines.emine.support.Personas;
import gn.gov.mines.emine.support.TestIntegration;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * US-04.5 : sans notification, e-mine interroge TrésorPay toutes les 30 s pendant 15 min ; au-delà,
 * le paiement devient {@code expire} ou {@code paye} selon la réponse, sinon {@code a_verifier}.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InterrogationTresorPayTest extends TestIntegration {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired MockMvc mvc;
  @Autowired ServiceInterrogationTresorPay interrogation;
  @Autowired PasserelleDeTest passerelle;

  @BeforeAll
  void personas() throws Exception {
    Personas.charger();
  }

  @AfterEach
  void passerelleDisponible() {
    passerelle.definirIndisponible(false);
  }

  @Test
  void uneReponsePayeeConfirmeLePaiementEtEstTracee() throws Exception {
    String ref = paiementEnLigne();
    passerelle.programmer(
        ref,
        new EtatTransaction(StatutTransaction.PAYE, "TP-interro-1", 5_000_000L, Instant.now()));
    // Avant 30 s, la notification a encore sa chance : pas d'interrogation.
    interrogation.executer(Instant.now());
    assertThat(DemandesDeTest.statutPaiement(ref)).isEqualTo("en_attente");

    interrogation.executer(Instant.now().plus(Duration.ofMinutes(1)));
    assertThat(DemandesDeTest.statutPaiement(ref)).isEqualTo("paye");
    assertThat(evenements(ref)).isEqualTo(1);
  }

  @Test
  void unMontantDifferentPasseAVerifier() throws Exception {
    String ref = paiementEnLigne();
    passerelle.programmer(
        ref,
        new EtatTransaction(StatutTransaction.PAYE, "TP-interro-2", 4_999_000L, Instant.now()));
    interrogation.executer(Instant.now().plus(Duration.ofMinutes(1)));
    assertThat(DemandesDeTest.statutPaiement(ref)).isEqualTo("a_verifier");
  }

  @Test
  void uneSessionToujoursOuverteSeReinterrogeEtExpireApresQuinzeMinutes() throws Exception {
    String ref = paiementEnLigne();
    interrogation.executer(Instant.now().plus(Duration.ofMinutes(5)));
    assertThat(DemandesDeTest.statutPaiement(ref)).isEqualTo("en_attente");
    interrogation.executer(Instant.now().plus(Duration.ofMinutes(16)));
    assertThat(DemandesDeTest.statutPaiement(ref)).isEqualTo("expire");
    assertThat(evenements(ref)).isEqualTo(2);
  }

  @Test
  void sansReponseApresQuinzeMinutesLePaiementEstAVerifier() throws Exception {
    String ref = paiementEnLigne();
    passerelle.definirIndisponible(true);
    interrogation.executer(Instant.now().plus(Duration.ofMinutes(5)));
    assertThat(DemandesDeTest.statutPaiement(ref)).isEqualTo("en_attente");
    interrogation.executer(Instant.now().plus(Duration.ofMinutes(16)));
    assertThat(DemandesDeTest.statutPaiement(ref)).isEqualTo("a_verifier");
  }

  @Test
  void unRefusEstApplique() throws Exception {
    String ref = paiementEnLigne();
    passerelle.programmer(
        ref, new EtatTransaction(StatutTransaction.REFUSE, "TP-interro-3", null, Instant.now()));
    interrogation.executer(Instant.now().plus(Duration.ofMinutes(1)));
    assertThat(DemandesDeTest.statutPaiement(ref)).isEqualTo("echoue");
  }

  @Test
  void unPaiementDejaNotifieNEstPasRetouche() throws Exception {
    String ref = paiementEnLigne();
    DemandesDeTest.changerStatutPaiement(ref, "paye");
    passerelle.programmer(
        ref, new EtatTransaction(StatutTransaction.REFUSE, "TP-interro-4", null, Instant.now()));
    interrogation.executer(Instant.now().plus(Duration.ofMinutes(20)));
    assertThat(DemandesDeTest.statutPaiement(ref)).isEqualTo("paye");
  }

  @Test
  void uneReferenceDeVirementNonDeclareeExpireASaDateLimite() throws Exception {
    long demande = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    String ref;
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "INSERT INTO paiement (demande_id, objet, moyen, montant_gnf, payeur_id,"
                    + " date_expiration) SELECT id, 'FRAIS_DOSSIER', 'VIREMENT', 5000000,"
                    + " demandeur_id, now() - interval '1 minute' FROM demande WHERE id = ?"
                    + " RETURNING reference")) {
      ps.setLong(1, demande);
      ResultSet rs = ps.executeQuery();
      rs.next();
      ref = rs.getString(1);
    }
    interrogation.executer(Instant.now());
    assertThat(DemandesDeTest.statutPaiement(ref)).isEqualTo("expire");
  }

  private String paiementEnLigne() throws Exception {
    long demande = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    String corps =
        mvc.perform(
                post("/api/v1/demandes/" + demande + "/paiements")
                    .with(jeton(INVESTISSEUR, "INVESTISSEUR"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"moyen\":\"TRESORPAY\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JSON.readTree(corps).path("paiement").path("reference").asString();
  }

  private static int evenements(String reference) throws Exception {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "SELECT count(*) FROM paiement_evenement WHERE reference_recue = ?"
                    + " AND type_evenement = 'INTERROGATION'")) {
      ps.setString(1, reference);
      ResultSet rs = ps.executeQuery();
      rs.next();
      return rs.getInt(1);
    }
  }
}
