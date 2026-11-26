package gn.gov.mines.emine.paiement;

import static gn.gov.mines.emine.support.Personas.GUICHET;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR_2;
import static gn.gov.mines.emine.support.Personas.jeton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gn.gov.mines.emine.support.DemandesDeTest;
import gn.gov.mines.emine.support.Personas;
import gn.gov.mines.emine.support.TestIntegration;
import java.time.Year;
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
 * US-04.2 : le demandeur choisit son moyen de paiement ; montant (barème) et référence
 * PAY-AAAA-NNNNNN sont fixés par le serveur ; changer de moyen annule le paiement ouvert ; PAY-001
 * si l'objet est déjà payé.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChoixMoyenPaiementTest extends TestIntegration {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String REFERENCE = "PAY-" + Year.now().getValue() + "-\\d{6}";

  @Autowired MockMvc mvc;

  @BeforeAll
  void personas() throws Exception {
    Personas.charger();
  }

  @Test
  void lesFraisAttendusSontCeuxDuBaremeSansMoyenPreselectionne() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    mvc.perform(get("/api/v1/demandes/" + id + "/paiements/a-payer").with(investisseur()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.objet").value("FRAIS_DOSSIER"))
        .andExpect(jsonPath("$.montantGnf").value(5_000_000))
        .andExpect(jsonPath("$.moyens[0].moyen").value("TRESORPAY"))
        .andExpect(jsonPath("$.moyens[0].disponible").value(true))
        .andExpect(jsonPath("$.moyens[1].moyen").value("VIREMENT"))
        .andExpect(jsonPath("$.moyens[1].disponible").value(true))
        .andExpect(jsonPath("$.referenceOuverte").value(nullValue()));
  }

  @Test
  void uneAttestationSePaieEnUneFois() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "EXC");
    mvc.perform(get("/api/v1/demandes/" + id + "/paiements/a-payer").with(investisseur()))
        .andExpect(jsonPath("$.objet").value("ATTESTATION"))
        .andExpect(jsonPath("$.montantGnf").value(200_000));
  }

  @Test
  void choisirTresorPayCreeLePaiementAuMontantDuBaremeAvecUneReferenceDeLaBase() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    choisir(id, "TRESORPAY", investisseur())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.paiement.reference").value(matchesPattern(REFERENCE)))
        .andExpect(jsonPath("$.paiement.objet").value("FRAIS_DOSSIER"))
        .andExpect(jsonPath("$.paiement.moyen").value("TRESORPAY"))
        .andExpect(jsonPath("$.paiement.montantGnf").value(5_000_000))
        .andExpect(jsonPath("$.paiement.dateExpiration").isNotEmpty());
  }

  @Test
  void leMontantEnvoyeParLeNavigateurEstIgnore() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    mvc.perform(
            post("/api/v1/demandes/" + id + "/paiements")
                .with(investisseur())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"moyen\":\"TRESORPAY\",\"montantGnf\":1}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.paiement.montantGnf").value(5_000_000));
  }

  @Test
  void changerDeMoyenAnnuleLAncienPaiementEtCreeUneNouvelleReference() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    String premiere = reference(choisir(id, "TRESORPAY", investisseur()));
    String seconde = reference(choisir(id, "TRESORPAY", investisseur()));
    assertThat(seconde).isNotEqualTo(premiere);
    assertThat(DemandesDeTest.statutPaiement(premiere)).isEqualTo("annule");
    assertThat(DemandesDeTest.statutPaiement(seconde)).isEqualTo("en_attente");
    mvc.perform(get("/api/v1/demandes/" + id + "/paiements/a-payer").with(investisseur()))
        .andExpect(jsonPath("$.referenceOuverte").value(seconde))
        .andExpect(jsonPath("$.moyenOuvert").value("TRESORPAY"));
    mvc.perform(get("/api/v1/demandes/" + id + "/paiements").with(investisseur()))
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].reference").value(seconde));
  }

  @Test
  void unObjetDejaPayeEstRefuseAvecPay001() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    String ref = reference(choisir(id, "TRESORPAY", investisseur()));
    DemandesDeTest.changerStatutPaiement(ref, "paye");
    choisir(id, "TRESORPAY", investisseur())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PAY-001"));
    mvc.perform(get("/api/v1/demandes/" + id + "/paiements/a-payer").with(investisseur()))
        .andExpect(jsonPath("$.objet").value(nullValue()));
  }

  @Test
  void leVirementCreeUneReferenceSansRedirection() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    choisir(id, "VIREMENT", investisseur())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.paiement.moyen").value("VIREMENT"))
        .andExpect(jsonPath("$.paiement.statut").value("initie"))
        .andExpect(jsonPath("$.paiement.montantGnf").value(5_000_000))
        .andExpect(jsonPath("$.urlPaiement").value(nullValue()));
  }

  @Test
  void lesDroitsSontAttendusApresLaDecision() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    String frais = reference(choisir(id, "TRESORPAY", investisseur()));
    DemandesDeTest.changerStatutPaiement(frais, "paye");
    DemandesDeTest.changerStatut(id, "recevable");
    DemandesDeTest.avisRendus(id);
    DemandesDeTest.changerStatut(id, "decision");
    mvc.perform(get("/api/v1/demandes/" + id + "/paiements/a-payer").with(investisseur()))
        .andExpect(jsonPath("$.objet").value("DROITS"))
        .andExpect(jsonPath("$.montantGnf").value(15_000_000));
  }

  @Test
  void rienNEstAttenduPendantLInstruction() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    String frais = reference(choisir(id, "TRESORPAY", investisseur()));
    DemandesDeTest.changerStatutPaiement(frais, "paye");
    DemandesDeTest.changerStatut(id, "recevable");
    choisir(id, "TRESORPAY", investisseur())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PAY-007"));
  }

  @Test
  void personneNePaieNiNeVoitLesPaiementsDUnAutre() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    String ref = reference(choisir(id, "TRESORPAY", investisseur()));
    choisir(id, "TRESORPAY", jeton(INVESTISSEUR_2, "INVESTISSEUR"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("DEM-002"));
    mvc.perform(get("/api/v1/paiements/" + ref).with(jeton(INVESTISSEUR_2, "INVESTISSEUR")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PAY-005"));
    choisir(id, "TRESORPAY", jeton(GUICHET, "GUICHET"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PERM-001"));
    mvc.perform(get("/api/v1/paiements/" + ref).with(investisseur()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reference").value(ref));
  }

  @Test
  void unMoyenInconnuEstRefuse() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    choisir(id, "ESPECES", investisseur())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VAL-001"));
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor investisseur() {
    return jeton(INVESTISSEUR, "INVESTISSEUR");
  }

  private ResultActions choisir(
      long id, String moyen, org.springframework.test.web.servlet.request.RequestPostProcessor qui)
      throws Exception {
    return mvc.perform(
        post("/api/v1/demandes/" + id + "/paiements")
            .with(qui)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"moyen\":\"" + moyen + "\"}"));
  }

  private static String reference(ResultActions r) throws Exception {
    return JSON.readTree(r.andReturn().getResponse().getContentAsString())
        .path("paiement")
        .path("reference")
        .asString();
  }
}
