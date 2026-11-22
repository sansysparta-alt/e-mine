package gn.gov.mines.emine.delivrance;

import static gn.gov.mines.emine.support.Personas.DIRECTEUR;
import static gn.gov.mines.emine.support.Personas.GUICHET;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR_2;
import static gn.gov.mines.emine.support.Personas.MINISTRE;
import static gn.gov.mines.emine.support.Personas.jeton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import eu.europa.esig.dss.diagnostic.SignatureWrapper;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import gn.gov.mines.emine.support.DemandesDeTest;
import gn.gov.mines.emine.support.Personas;
import gn.gov.mines.emine.support.StockageEnMemoire;
import gn.gov.mines.emine.support.TestIntegration;
import java.security.MessageDigest;
import java.time.Year;
import java.util.HexFormat;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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
 * US-06.3 / 06.4 : le signataire du catalogue signe et délivre une demande dont les droits sont
 * payés ; le PDF/A signé (PAdES) porte le numéro et le QR, il est archivé et inscrit au registre
 * avec son empreinte SHA-256, la demande passe à « délivrée » ; le demandeur le télécharge ou
 * l'affiche.
 */
@AutoConfigureMockMvc
@Import(StockageEnMemoire.Config.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DelivranceTest extends TestIntegration {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired MockMvc mvc;
  @Autowired StockageEnMemoire stockage;

  @BeforeAll
  void personas() throws Exception {
    Personas.charger();
  }

  @Test
  void leMinistreSigneEtDelivreLePexLeDocumentEstArchiveEtInscritAuRegistre() throws Exception {
    long id = pexPret();
    mvc.perform(get(adresse(id, "delivrance")).with(ministre()))
        .andExpect(jsonPath("$.aDelivrer").value(true))
        .andExpect(jsonPath("$.peutDelivrer").value(true))
        .andExpect(jsonPath("$.signataire").value("Ministre"));

    String reponse =
        mvc.perform(post(adresse(id, "delivrance")).with(ministre()))
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.numero")
                    .value(matchesPattern("PEX-" + Year.now().getValue() + "-\\d{4}")))
            .andExpect(jsonPath("$.valide").value(true))
            .andExpect(jsonPath("$.signataire").value("Ibrahima Soumah"))
            .andExpect(
                jsonPath("$.urlVerification")
                    .value(matchesPattern("http://localhost:3000/verifier/[0-9a-f]{32}")))
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode document = JSON.readTree(reponse);
    String numero = document.path("numero").asString();

    assertThat(DemandesDeTest.statutEtMotif(id)[0]).isEqualTo("delivree");
    assertThat(DemandesDeTest.auteurDernierStatut(id)).isEqualTo(MINISTRE);

    byte[] pdf = stockage.objet("emine-documents", "documents/" + numero + ".pdf");
    assertThat(pdf).as("archivé dans le stockage").isNotNull();
    assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdf)))
        .isEqualTo(document.path("empreinteSha256").asString());
    SignatureWrapper signature = valider(pdf);
    assertThat(signature.isSignatureIntact()).isTrue();
    try (PDDocument doc = Loader.loadPDF(pdf)) {
      assertThat(new PDFTextStripper().getText(doc))
          .contains(numero)
          .contains("Permis d'exploitation")
          .contains("Le Ministre")
          .contains("Dossier conforme.");
    }
  }

  @Test
  void leDemandeurConsulteEtTelechargeSonDocumentUnAutreNonPlus() throws Exception {
    long id = pexPret();
    mvc.perform(post(adresse(id, "delivrance")).with(ministre())).andExpect(status().isOk());
    mvc.perform(get(adresse(id, "document")).with(investisseur()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.codeType").value("PEX"));
    mvc.perform(get(adresse(id, "document/contenu") + "?inline=true").with(investisseur()))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/pdf"))
        .andExpect(
            header().string("Content-Disposition", matchesPattern("inline; filename=\"PEX-.*\"")))
        .andExpect(header().string("Cache-Control", "no-store"));
    mvc.perform(get(adresse(id, "document/contenu")).with(investisseur()))
        .andExpect(header().string("Content-Disposition", matchesPattern("attachment; .*")));
    mvc.perform(get(adresse(id, "document")).with(jeton(INVESTISSEUR_2, "INVESTISSEUR")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("DEM-002"));
  }

  @Test
  void unDocumentSansDroitsSignePuisDelivreParLeDirecteur() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "EXC");
    DemandesDeTest.pretePourLaDelivrance(id, DIRECTEUR, "ATTESTATION", 200_000, null);
    mvc.perform(post(adresse(id, "delivrance")).with(directeur()))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.numero").value(matchesPattern("EXC-" + Year.now().getValue() + "-\\d{4}")));
  }

  @Test
  void seulLeSignataireDuCatalogueDelivreDem008EtPerm001() throws Exception {
    long id = pexPret();
    mvc.perform(get(adresse(id, "delivrance")).with(directeur()))
        .andExpect(jsonPath("$.peutDelivrer").value(false));
    mvc.perform(post(adresse(id, "delivrance")).with(directeur()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("DEM-008"));
    mvc.perform(post(adresse(id, "delivrance")).with(jeton(GUICHET, "GUICHET")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PERM-001"));
    assertThat(DemandesDeTest.statutEtMotif(id)[0]).isEqualTo("droits_payes");
  }

  @Test
  void uneDemandeNonPreteOuDejaDelivreeEstRefuseeDem001() throws Exception {
    long id = pexPret();
    mvc.perform(post(adresse(id, "delivrance")).with(ministre())).andExpect(status().isOk());
    mvc.perform(post(adresse(id, "delivrance")).with(ministre()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DEM-001"));
    long nonPrete = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    mvc.perform(post(adresse(nonPrete, "delivrance")).with(ministre()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DEM-001"));
    mvc.perform(get(adresse(nonPrete, "document")).with(investisseur()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("DOC-005"));
  }

  private static long pexPret() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    DemandesDeTest.pretePourLaDelivrance(id, MINISTRE, "FRAIS_DOSSIER", 5_000_000, 15_000_000L);
    return id;
  }

  private static SignatureWrapper valider(byte[] pdf) {
    SignedDocumentValidator validateur =
        SignedDocumentValidator.fromDocument(new InMemoryDocument(pdf));
    validateur.setCertificateVerifier(new CommonCertificateVerifier());
    return validateur.validateDocument().getDiagnosticData().getSignatures().getFirst();
  }

  private static String adresse(long id, String suite) {
    return "/api/v1/demandes/" + id + "/" + suite;
  }

  private static RequestPostProcessor ministre() {
    return jeton(MINISTRE, "MINISTRE");
  }

  private static RequestPostProcessor directeur() {
    return jeton(DIRECTEUR, "DIRECTEUR");
  }

  private static RequestPostProcessor investisseur() {
    return jeton(INVESTISSEUR, "INVESTISSEUR");
  }
}
