package gn.gov.mines.emine.paiement;

import static gn.gov.mines.emine.support.Personas.DIRECTEUR;
import static gn.gov.mines.emine.support.Personas.FISCAL;
import static gn.gov.mines.emine.support.Personas.GUICHET;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR;
import static gn.gov.mines.emine.support.Personas.jeton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gn.gov.mines.emine.support.BaseDeDonneesDeTest;
import gn.gov.mines.emine.support.DemandesDeTest;
import gn.gov.mines.emine.support.Personas;
import gn.gov.mines.emine.support.StockageEnMemoire;
import gn.gov.mines.emine.support.TestIntegration;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Corrections de la recette du 27/11/2026 côté API : virements attendus sur le prochain relevé
 * (R3), association choisie dans une liste et refus clair d'un paiement déjà confirmé (R4), lignes
 * du relevé numérotées de 1 à n (R5), remboursement proposé à la main par le Responsable fiscal
 * (R7) et sa notification quand le Directeur décide (R9, US-04.17).
 */
@AutoConfigureMockMvc
@Import(StockageEnMemoire.Config.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RecetteLot6Test extends TestIntegration {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final byte[] PDF =
      "%PDF-1.7\n1 0 obj << >> endobj\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
  private static final String EN_TETE = "date_operation;libelle;montant_gnf;sens\n";
  private static final AtomicInteger UNIQUE = new AtomicInteger();

  @Autowired MockMvc mvc;

  @BeforeAll
  void personas() throws Exception {
    Personas.charger();
  }

  @Test
  void lesVirementsDeclaresAttendusSurLeProchainReleveSontAnnonces() throws Exception {
    long avant = aVerifier().path("virementsAttendus").asLong();
    virementDeclare(DemandesDeTest.deposee(INVESTISSEUR, "PEX"));
    assertThat(aVerifier().path("virementsAttendus").asLong()).isEqualTo(avant + 1);
  }

  @Test
  void uneLigneSansReferenceSAssocieAUnVirementChoisiDansLaListe() throws Exception {
    long demande = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    String ref = virementDeclare(demande);
    long montant = montantDu(ref);
    long ligne = ligneImportee("21/11/2026;VIREMENT SANS REFERENCE;" + montant + ";C");

    JsonNode liste =
        JSON.readTree(
            mvc.perform(
                    get("/api/v1/encaissements/lignes/" + ligne + "/paiements-associables")
                        .with(fiscal()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    boolean present = false;
    for (JsonNode a : liste) {
      assertThat(a.path("montantGnf").asLong()).isEqualTo(montant);
      present |= ref.equals(a.path("reference").asString());
    }
    assertThat(present).isTrue();

    mvc.perform(
            post("/api/v1/encaissements/lignes/" + ligne + "/decision")
                .with(fiscal())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"ASSOCIER\",\"reference\":\"" + ref + "\"}"))
        .andExpect(status().isNoContent());
    assertThat(DemandesDeTest.statutPaiement(ref)).isEqualTo("paye");

    // Le même paiement, déjà confirmé : un message clair (PAY-020), pas « aucun virement ».
    long autre = ligneImportee("22/11/2026;VIREMENT SANS REFERENCE BIS;" + montant + ";C");
    mvc.perform(
            post("/api/v1/encaissements/lignes/" + autre + "/decision")
                .with(fiscal())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"ASSOCIER\",\"reference\":\"" + ref + "\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PAY-020"));
  }

  @Test
  void lesLignesDuReleveSontNumeroteesDeUnAN() throws Exception {
    String csv =
        EN_TETE
            + "21/11/2026;LIGNE A;1000;C\n"
            + "\n"
            + "21/11/2026;LIGNE B;2000;D\n"
            + "21/11/2026;LIGNE C;3000;C\n";
    long releve = releveImporte(csv);
    JsonNode lignes =
        JSON.readTree(
            mvc.perform(get("/api/v1/releves/" + releve + "/lignes").with(fiscal()))
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(lignes.path("elements").get(0).path("numero").asInt()).isEqualTo(1);
    assertThat(lignes.path("elements").get(1).path("numero").asInt()).isEqualTo(2);
    assertThat(lignes.path("elements").get(2).path("numero").asInt()).isEqualTo(3);
  }

  @Test
  void leResponsableFiscalProposeUnRemboursementQueLeDirecteurDecide() throws Exception {
    long demande = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    String ref = virementDeclare(demande);
    long montant = montantDu(ref);
    ligneImportee("23/11/2026;VIR " + ref + ";" + montant + ";C");
    assertThat(DemandesDeTest.statutPaiement(ref)).isEqualTo("paye");

    String url = "/api/v1/paiements/" + ref + "/remboursements";
    // Seul le Responsable fiscal propose.
    mvc.perform(
            post(url)
                .with(jeton(GUICHET, "GUICHET"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"montantGnf\":1000,\"explication\":\"Versement en trop constaté.\"}"))
        .andExpect(status().isForbidden());
    // Motif obligatoire ; montant au plus égal à la somme reçue.
    mvc.perform(
            post(url)
                .with(fiscal())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"montantGnf\":1000,\"explication\":\"court\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details.champs[0].champ").value("explication"));
    mvc.perform(
            post(url)
                .with(fiscal())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"montantGnf\":"
                        + (montant + 1)
                        + ",\"explication\":\"Versement en trop constaté.\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details.champs[0].champ").value("montantGnf"));

    String corps =
        mvc.perform(
                post(url)
                    .with(fiscal())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"montantGnf\":1000,\"explication\":\"Versement en trop constaté.\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.numero").value(org.hamcrest.Matchers.startsWith("RMB-")))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long id = JSON.readTree(corps).path("id").asLong();
    // Le paiement reste payé ; le Directeur valide ; le Responsable fiscal en est notifié (V38).
    assertThat(DemandesDeTest.statutPaiement(ref)).isEqualTo("paye");
    mvc.perform(
            post("/api/v1/remboursements/" + id + "/validation")
                .with(jeton(DIRECTEUR, "DIRECTEUR")))
        .andExpect(status().isNoContent());
    assertThat(notificationsFiscal(demande, "REMBOURSEMENT_VALIDE")).isPositive();
  }

  // --- Outils -----------------------------------------------------------------------------

  private JsonNode aVerifier() throws Exception {
    return JSON.readTree(
        mvc.perform(get("/api/v1/encaissements/a-verifier").with(fiscal()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private long releveImporte(String csv) throws Exception {
    String corps =
        mvc.perform(
                multipart("/api/v1/releves")
                    .file(
                        new MockMultipartFile(
                            "fichier",
                            "recette" + UNIQUE.incrementAndGet() + ".csv",
                            "text/csv",
                            csv.getBytes(StandardCharsets.UTF_8)))
                    .with(fiscal()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JSON.readTree(corps).path("id").asLong();
  }

  private long ligneImportee(String ligneCsv) throws Exception {
    long releve = releveImporte(EN_TETE + ligneCsv + "\n");
    return JSON.readTree(
            mvc.perform(get("/api/v1/releves/" + releve + "/lignes").with(fiscal()))
                .andReturn()
                .getResponse()
                .getContentAsString())
        .path("elements")
        .get(0)
        .path("id")
        .asLong();
  }

  private long montantDu(String reference) throws Exception {
    return JSON.readTree(
            mvc.perform(get("/api/v1/paiements/" + reference).with(investisseur()))
                .andReturn()
                .getResponse()
                .getContentAsString())
        .path("montantGnf")
        .asLong();
  }

  private String virementDeclare(long demande) throws Exception {
    String ref =
        JSON.readTree(
                mvc.perform(
                        post("/api/v1/demandes/" + demande + "/paiements")
                            .with(investisseur())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"moyen\":\"VIREMENT\"}"))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .path("paiement")
            .path("reference")
            .asString();
    mvc.perform(
            multipart("/api/v1/paiements/" + ref + "/virement")
                .file(new MockMultipartFile("fichier", "ordre.pdf", "application/pdf", PDF))
                .with(investisseur()))
        .andExpect(status().isOk());
    return ref;
  }

  private static int notificationsFiscal(long demande, String modele) throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "SELECT count(*) FROM notification n JOIN utilisateur u ON u.id = n.destinataire_id"
                    + " WHERE n.demande_id = ? AND n.modele = ? AND u.keycloak_id = ?::uuid")) {
      ps.setLong(1, demande);
      ps.setString(2, modele);
      ps.setString(3, FISCAL);
      ResultSet rs = ps.executeQuery();
      rs.next();
      return rs.getInt(1);
    }
  }

  private static RequestPostProcessor fiscal() {
    return jeton(FISCAL, "FISCAL");
  }

  private static RequestPostProcessor investisseur() {
    return jeton(INVESTISSEUR, "INVESTISSEUR");
  }
}
