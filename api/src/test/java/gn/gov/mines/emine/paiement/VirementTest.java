package gn.gov.mines.emine.paiement;

import static gn.gov.mines.emine.support.Personas.FISCAL;
import static gn.gov.mines.emine.support.Personas.GUICHET;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR_2;
import static gn.gov.mines.emine.support.Personas.jeton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gn.gov.mines.emine.support.DemandesDeTest;
import gn.gov.mines.emine.support.Personas;
import gn.gov.mines.emine.support.StockageEnMemoire;
import gn.gov.mines.emine.support.TestIntegration;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

/**
 * US-04.7 : le demandeur paie par virement — coordonnées du compte, référence et date limite à 7
 * jours ; justificatif PDF/JPG/PNG de 5 Mo au plus obligatoire avant « J'ai effectué le virement ».
 * Un virement déclaré est en cours de vérification : la demande sort de « Frais à payer » et le
 * moyen ne peut plus être changé.
 */
@AutoConfigureMockMvc
@Import(StockageEnMemoire.Config.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VirementTest extends TestIntegration {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final byte[] PDF =
      "%PDF-1.7\n1 0 obj << >> endobj\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

  @Autowired MockMvc mvc;

  @BeforeAll
  void personas() throws Exception {
    Personas.charger();
  }

  @Test
  void leVirementDonneLesCoordonneesLaReferenceEtUneDateLimiteASeptJours() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    String ref = choisirVirement(id);
    String corps =
        mvc.perform(get("/api/v1/paiements/" + ref).with(investisseur()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statut").value("initie"))
            .andExpect(jsonPath("$.montantGnf").value(5_000_000))
            .andExpect(jsonPath("$.coordonnees.beneficiaire").isNotEmpty())
            .andExpect(jsonPath("$.coordonnees.banque").isNotEmpty())
            .andExpect(jsonPath("$.coordonnees.iban").value("GN00 0000 0000 0000 0000 0000 000"))
            .andExpect(jsonPath("$.coordonnees.bic").isNotEmpty())
            .andExpect(jsonPath("$.justificatif").value(false))
            .andExpect(jsonPath("$.virementDeclareLe").value(nullValue()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    Instant limite = Instant.parse(JSON.readTree(corps).path("dateExpiration").asString());
    assertThat(Duration.between(Instant.now(), limite))
        .isBetween(Duration.ofDays(7).minusMinutes(5), Duration.ofDays(7));
    // Tant que le virement n'est pas déclaré, les frais restent à payer par le demandeur.
    mvc.perform(get("/api/v1/demandes/" + id).with(investisseur()))
        .andExpect(jsonPath("$.paiementEnVerification").value(false))
        .andExpect(jsonPath("$.aTraiterParMoi").value(true));
  }

  @Test
  void unPaiementEnLigneNAPasDeCoordonneesDeVirement() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    String ref =
        reference(
            mvc.perform(
                post("/api/v1/demandes/" + id + "/paiements")
                    .with(investisseur())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"moyen\":\"TRESORPAY\"}")));
    mvc.perform(get("/api/v1/paiements/" + ref).with(investisseur()))
        .andExpect(jsonPath("$.coordonnees").value(nullValue()));
  }

  @Test
  void leJustificatifEstObligatoireEtControle() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    String ref = choisirVirement(id);
    mvc.perform(multipart("/api/v1/paiements/" + ref + "/virement").with(investisseur()))
        .andExpect(status().isBadRequest());
    declarer(ref, "releve.txt", "texte".getBytes(StandardCharsets.UTF_8), investisseur())
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value("DOC-001"));
    byte[] lourd = new byte[5 * 1024 * 1024 + 1];
    System.arraycopy(PDF, 0, lourd, 0, PDF.length);
    declarer(ref, "lourd.pdf", lourd, investisseur())
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.code").value("DOC-011"));
    assertThat(DemandesDeTest.statutPaiement(ref)).isEqualTo("initie");
  }

  @Test
  void declarerLeVirementLeMetEnVerificationEtSortLaDemandeDesFraisAPayer() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    String ref = choisirVirement(id);
    declarer(ref, "ordre-de-virement.pdf", PDF, investisseur())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.statut").value("en_attente"))
        .andExpect(jsonPath("$.justificatif").value(true))
        .andExpect(jsonPath("$.virementDeclareLe").isNotEmpty());
    mvc.perform(get("/api/v1/paiements/" + ref).with(investisseur()))
        .andExpect(jsonPath("$.justificatifFichier.nomFichier").value("ordre-de-virement.pdf"))
        .andExpect(jsonPath("$.justificatifFichier.typeMime").value("application/pdf"));
    // Le justificatif se consulte (demandeur et service financier), hors des pièces du dossier.
    mvc.perform(get("/api/v1/paiements/" + ref + "/justificatif").with(investisseur()))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/pdf"))
        .andExpect(header().string("Content-Disposition", startsWith("inline")))
        .andExpect(content().bytes(PDF));
    mvc.perform(get("/api/v1/paiements/" + ref + "/justificatif").with(jeton(FISCAL, "FISCAL")))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/demandes/" + id + "/pieces").with(investisseur()))
        .andExpect(jsonPath("$[*].typePiece", not(hasItem("JUSTIFICATIF_VIREMENT"))));
    // Rien à payer pour l'instant : « en vérification », plus dans « À traiter ».
    mvc.perform(get("/api/v1/demandes/" + id).with(investisseur()))
        .andExpect(jsonPath("$.paiementEnVerification").value(true))
        .andExpect(jsonPath("$.aTraiterParMoi").value(false));
    mvc.perform(get("/api/v1/demandes/" + id + "/paiements/a-payer").with(investisseur()))
        .andExpect(jsonPath("$.referenceOuverte").value(ref))
        .andExpect(jsonPath("$.moyenOuvert").value("VIREMENT"))
        .andExpect(jsonPath("$.enVerification").value(true));
    mvc.perform(get("/api/v1/demandes?vue=a-traiter&taille=100").with(investisseur()))
        .andExpect(jsonPath("$.elements[*].id", not(hasItem((int) id))));
    // Côté guichet, la demande n'attend plus le demandeur.
    mvc.perform(get("/api/v1/demandes/" + id).with(jeton(GUICHET, "GUICHET")))
        .andExpect(jsonPath("$.paiementEnVerification").value(true));
  }

  @Test
  void unVirementDeclareNeSeRemplacePasNiNeSeDeclareDeuxFois() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    String ref = choisirVirement(id);
    declarer(ref, "ordre.pdf", PDF, investisseur()).andExpect(status().isOk());
    declarer(ref, "ordre.pdf", PDF, investisseur())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PAY-010"));
    mvc.perform(
            post("/api/v1/demandes/" + id + "/paiements")
                .with(investisseur())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"moyen\":\"TRESORPAY\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PAY-010"));
    assertThat(DemandesDeTest.statutPaiement(ref)).isEqualTo("en_attente");
  }

  @Test
  void uneReferenceExpireeNeSeDeclarePlus() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    String ref = choisirVirement(id);
    DemandesDeTest.expirerPaiement(ref);
    declarer(ref, "ordre.pdf", PDF, investisseur())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PAY-010"));
    assertThat(DemandesDeTest.statutPaiement(ref)).isEqualTo("initie");
    // Plus proposée comme paiement en cours : le demandeur choisit à nouveau un moyen.
    mvc.perform(get("/api/v1/demandes/" + id + "/paiements/a-payer").with(investisseur()))
        .andExpect(jsonPath("$.referenceOuverte").value(nullValue()));
  }

  @Test
  void seulLeDemandeurDeclareSonVirement() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    String ref = choisirVirement(id);
    declarer(ref, "ordre.pdf", PDF, jeton(INVESTISSEUR_2, "INVESTISSEUR"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PAY-005"));
    declarer(ref, "ordre.pdf", PDF, jeton(GUICHET, "GUICHET"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PERM-001"));
  }

  private String choisirVirement(long id) throws Exception {
    return reference(
        mvc.perform(
                post("/api/v1/demandes/" + id + "/paiements")
                    .with(investisseur())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"moyen\":\"VIREMENT\"}"))
            .andExpect(status().isCreated()));
  }

  private ResultActions declarer(String ref, String nom, byte[] contenu, RequestPostProcessor qui)
      throws Exception {
    return mvc.perform(
        multipart("/api/v1/paiements/" + ref + "/virement")
            .file(new MockMultipartFile("fichier", nom, "application/pdf", contenu))
            .with(qui));
  }

  private static RequestPostProcessor investisseur() {
    return jeton(INVESTISSEUR, "INVESTISSEUR");
  }

  private static String reference(ResultActions r) throws Exception {
    return JSON.readTree(r.andReturn().getResponse().getContentAsString())
        .path("paiement")
        .path("reference")
        .asString();
  }
}
