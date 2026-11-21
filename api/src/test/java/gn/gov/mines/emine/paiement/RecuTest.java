package gn.gov.mines.emine.paiement;

import static gn.gov.mines.emine.support.Personas.INVESTISSEUR;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR_2;
import static gn.gov.mines.emine.support.Personas.jeton;
import static org.assertj.core.api.Assertions.assertThat;
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
import java.time.Year;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * US-04.6 : reçu PDF REC-AAAA-NNNNNN, simple et sans signature en V1, disponible dès la
 * confirmation du paiement, réservé au demandeur (RLS).
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RecuTest extends TestIntegration {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired MockMvc mvc;

  @BeforeAll
  void personas() throws Exception {
    Personas.charger();
  }

  @Test
  void leRecuDUnPaiementConfirmeEstUnPdfAvecSonNumeroEtSonMontant() throws Exception {
    long demande = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    String ref = payerEtConfirmer(demande);
    byte[] pdf =
        mvc.perform(get("/api/v1/paiements/" + ref + "/recu").with(investisseur()))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/pdf"))
            .andExpect(
                header()
                    .string(
                        "Content-Disposition",
                        org.hamcrest.Matchers.matchesPattern(
                            "attachment; filename=\"recu-REC-\\d{4}-\\d{6}\\.pdf\"")))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    try (PDDocument doc = Loader.loadPDF(pdf)) {
      String texte = new PDFTextStripper().getText(doc).replace(' ', ' ');
      assertThat(texte)
          .contains("REÇU DE PAIEMENT")
          .containsPattern("REC-" + Year.now().getValue() + "-\\d{6}")
          .contains("5 000 000 GNF")
          .contains("Cinq millions de francs guinéens")
          .contains("Frais de dossier")
          .contains(ref)
          .contains("TrésorPay")
          .contains("démonstration");
      assertThat(doc.getNumberOfPages()).isEqualTo(1);
    }
  }

  @Test
  void pasDeRecuAvantLaConfirmation() throws Exception {
    long demande = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    String ref = payer(demande);
    mvc.perform(get("/api/v1/paiements/" + ref + "/recu").with(investisseur()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PAY-009"));
  }

  @Test
  void unAutreInvestisseurNObtientPasLeRecu() throws Exception {
    String ref = payerEtConfirmer(DemandesDeTest.deposee(INVESTISSEUR, "PEX"));
    mvc.perform(
            get("/api/v1/paiements/" + ref + "/recu").with(jeton(INVESTISSEUR_2, "INVESTISSEUR")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PAY-005"));
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor investisseur() {
    return jeton(INVESTISSEUR, "INVESTISSEUR");
  }

  private String payer(long demande) throws Exception {
    String reponse =
        mvc.perform(
                post("/api/v1/demandes/" + demande + "/paiements")
                    .with(investisseur())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"moyen\":\"TRESORPAY\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JSON.readTree(reponse).path("paiement").path("reference").asString();
  }

  private String payerEtConfirmer(long demande) throws Exception {
    String ref = payer(demande);
    byte[] corps =
        ("{\"transactionId\":\"TP-"
                + UUID.randomUUID()
                + "\",\"reference\":\""
                + ref
                + "\",\"statut\":\"PAYE\",\"montantGnf\":5000000}")
            .getBytes(StandardCharsets.UTF_8);
    String h = Long.toString(Instant.now().getEpochSecond());
    mvc.perform(
            post("/api/v1/paiements/notifications/tresorpay")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-TresorPay-Marchand", MARCHAND_TRESORPAY)
                .header("X-TresorPay-Horodatage", h)
                .header(
                    "X-TresorPay-Signature", SignatureTresorPay.signer(SECRET_TRESORPAY, h, corps))
                .content(corps))
        .andExpect(status().isOk());
    return ref;
  }
}
