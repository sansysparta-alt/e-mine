package gn.gov.mines.emine.paiement;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * US-04.9 : le Responsable fiscal traite les écarts « À vérifier » — À vérifier → Payé ou Rejeté
 * (motif communiqué au demandeur), ligne associée au bon paiement ou classée avec motif ; chaque
 * décision est tracée et la règle des décisions possibles est celle de l'API.
 */
@AutoConfigureMockMvc
@Import(StockageEnMemoire.Config.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EcartsTest extends TestIntegration {

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
  void unMontantSuperieurSeValideEtConfirmeLePaiement() throws Exception {
    long demande = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    String ref = virementDeclare(demande);
    long ligne = ligneImportee("26/11/2026;VIR " + ref + ";6000000;C");
    JsonNode element = element(ligne);
    assertThat(element.path("nature").asString()).isEqualTo("superieur");
    assertThat(decisions(element)).containsExactly("VALIDER", "REJETER");
    long avant = total();

    decider(ligne, "{\"decision\":\"VALIDER\"}").andExpect(status().isNoContent());

    assertThat(DemandesDeTest.statutPaiement(ref)).isEqualTo("paye");
    assertThat(total()).isEqualTo(avant - 1);
    assertThat(trace(ligne)).isEqualTo("valide");
    mvc.perform(get("/api/v1/paiements/" + ref).with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(jsonPath("$.montantRecuGnf").value(6_000_000))
        .andExpect(jsonPath("$.datePaiement").value(org.hamcrest.Matchers.startsWith("2026-11-2")));
    // Déjà traitée : plus de décision possible.
    decider(ligne, "{\"decision\":\"VALIDER\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PAY-013"));
  }

  @Test
  void unMontantInferieurSeRejetteAvecUnMotifCommuniqueAuDemandeur() throws Exception {
    long demande = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    String ref = virementDeclare(demande);
    long ligne = ligneImportee("26/11/2026;VIR " + ref + ";4000000;C");
    // US-04.12 : demander le complément est proposé avant le rejet.
    assertThat(decisions(element(ligne))).containsExactly("COMPLETER", "REJETER");
    decider(ligne, "{\"decision\":\"VALIDER\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PAY-013"));
    decider(ligne, "{\"decision\":\"REJETER\",\"motif\":\"court\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details.champs[0].champ").value("motif"));

    decider(ligne, "{\"decision\":\"REJETER\",\"motif\":\"Montant reçu inférieur au montant dû.\"}")
        .andExpect(status().isNoContent());

    assertThat(DemandesDeTest.statutPaiement(ref)).isEqualTo("rejete");
    mvc.perform(get("/api/v1/paiements/" + ref).with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(jsonPath("$.motifRejet").value("Montant reçu inférieur au montant dû."));
    assertThat(DemandesDeTest.notifications(demande, "PAIEMENT_REJETE")).isPositive();
    // Les frais sont à nouveau à payer par le demandeur, qui peut choisir un moyen.
    mvc.perform(get("/api/v1/demandes/" + demande).with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(jsonPath("$.paiementEnVerification").value(false))
        .andExpect(jsonPath("$.aTraiterParMoi").value(true));
    mvc.perform(
            post("/api/v1/demandes/" + demande + "/paiements")
                .with(jeton(INVESTISSEUR, "INVESTISSEUR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"moyen\":\"VIREMENT\"}"))
        .andExpect(status().isCreated());
  }

  @Test
  void uneLigneSansReferenceSAssocieAuVirementDuMemeMontant() throws Exception {
    long demande = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    String ref = virementDeclare(demande);
    long ligne =
        ligneImportee("26/11/2026;VIR SOCIETE SANS REF " + UNIQUE.incrementAndGet() + ";5000000;C");
    assertThat(decisions(element(ligne))).containsExactly("ASSOCIER", "CLASSER");
    decider(ligne, "{\"decision\":\"ASSOCIER\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details.champs[0].champ").value("reference"));
    String autre = virementDeclare(DemandesDeTest.deposee(INVESTISSEUR, "EXC")); // 200 000 GNF
    decider(ligne, "{\"decision\":\"ASSOCIER\",\"reference\":\"" + autre + "\"}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("PAY-014"));

    decider(ligne, "{\"decision\":\"ASSOCIER\",\"reference\":\"" + ref.toLowerCase() + "\"}")
        .andExpect(status().isNoContent());

    assertThat(DemandesDeTest.statutPaiement(ref)).isEqualTo("paye");
    assertThat(trace(ligne)).isEqualTo("associe");
  }

  @Test
  void unDoublonSeClasseAvecUnMotif() throws Exception {
    long demande = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    String ref = virementDeclare(demande);
    ligneImportee("26/11/2026;VIR " + ref + ";5000000;C");
    long doublon = ligneImportee("27/11/2026;VIR " + ref + " BIS;5000000;C");
    assertThat(element(doublon).path("nature").asString()).isEqualTo("doublon");
    assertThat(decisions(element(doublon))).containsExactly("REMBOURSER", "CLASSER");
    decider(doublon, "{\"decision\":\"CLASSER\"}").andExpect(status().isBadRequest());
    decider(
            doublon,
            "{\"decision\":\"CLASSER\",\"motif\":\"Remboursé hors plateforme par la banque.\"}")
        .andExpect(status().isNoContent());
    assertThat(trace(doublon)).isEqualTo("classe");
  }

  @Test
  void unPaiementEnLigneAuMontantDivergentSeDecideSansLigneDeReleve() throws Exception {
    long demande = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    String ref =
        reference(
            mvc.perform(
                post("/api/v1/demandes/" + demande + "/paiements")
                    .with(jeton(INVESTISSEUR, "INVESTISSEUR"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"moyen\":\"TRESORPAY\"}")));
    aVerifier(ref, 5_500_000);
    JsonNode element = null;
    for (JsonNode e : file().path("elements")) {
      if (ref.equals(e.path("referencePaiement").asString())) {
        element = e;
      }
    }
    assertThat(element).isNotNull();
    assertThat(element.path("type").asString()).isEqualTo("paiement");
    assertThat(element.path("nature").asString()).isEqualTo("montant_divergent");
    assertThat(decisions(element)).containsExactly("VALIDER", "REJETER");

    mvc.perform(
            post("/api/v1/encaissements/paiements/" + ref + "/decision")
                .with(fiscal())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"VALIDER\"}"))
        .andExpect(status().isNoContent());
    assertThat(DemandesDeTest.statutPaiement(ref)).isEqualTo("paye");
  }

  @Test
  void seulLeResponsableFiscalTraiteLesEcarts() throws Exception {
    mvc.perform(get("/api/v1/encaissements/a-verifier").with(jeton(GUICHET, "GUICHET")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PERM-001"));
    mvc.perform(
            post("/api/v1/encaissements/lignes/1/decision")
                .with(jeton(INVESTISSEUR, "INVESTISSEUR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"CLASSER\",\"motif\":\"Tentative non autorisée\"}"))
        .andExpect(status().isForbidden());
  }

  // --- Outils -----------------------------------------------------------------------------

  /** Importe une ligne (fichier unique) et renvoie son identifiant. */
  private long ligneImportee(String ligneCsv) throws Exception {
    String csv = EN_TETE + ligneCsv + "\n";
    String corps =
        mvc.perform(
                multipart("/api/v1/releves")
                    .file(
                        new MockMultipartFile(
                            "fichier",
                            "r" + UNIQUE.incrementAndGet() + ".csv",
                            "text/csv",
                            csv.getBytes(StandardCharsets.UTF_8)))
                    .with(fiscal()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long releve = JSON.readTree(corps).path("id").asLong();
    JsonNode lignes =
        JSON.readTree(
            mvc.perform(get("/api/v1/releves/" + releve + "/lignes").with(fiscal()))
                .andReturn()
                .getResponse()
                .getContentAsString());
    return lignes.path("elements").get(0).path("id").asLong();
  }

  private JsonNode file() throws Exception {
    return JSON.readTree(
        mvc.perform(get("/api/v1/encaissements/a-verifier?taille=100").with(fiscal()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private long total() throws Exception {
    return file().path("total").asLong();
  }

  private JsonNode element(long ligne) throws Exception {
    for (JsonNode e : file().path("elements")) {
      if (e.path("ligneId").asLong() == ligne) {
        return e;
      }
    }
    throw new AssertionError("Ligne " + ligne + " absente de la file à vérifier");
  }

  private static java.util.List<String> decisions(JsonNode element) {
    java.util.List<String> d = new java.util.ArrayList<>();
    element.path("decisions").forEach(n -> d.add(n.asString()));
    return d;
  }

  private ResultActions decider(long ligne, String corps) throws Exception {
    return mvc.perform(
        post("/api/v1/encaissements/lignes/" + ligne + "/decision")
            .with(fiscal())
            .contentType(MediaType.APPLICATION_JSON)
            .content(corps));
  }

  /** Décision enregistrée sur la ligne, avec son auteur et sa date (traçabilité). */
  private static String trace(long ligne) throws Exception {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "SELECT decision, traitee_par, traitee_le FROM ligne_releve WHERE id = ?")) {
      ps.setLong(1, ligne);
      ResultSet rs = ps.executeQuery();
      rs.next();
      assertThat(rs.getLong(2)).isPositive();
      assertThat(rs.getTimestamp(3)).isNotNull();
      return rs.getString(1);
    }
  }

  /** Paiement en ligne passé « à vérifier » avec le montant reçu (notification divergente). */
  private static void aVerifier(String reference, long montantRecu) throws Exception {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "UPDATE paiement SET statut = 'a_verifier', montant_recu_gnf = ?"
                    + " WHERE reference = ?")) {
      ps.setLong(1, montantRecu);
      ps.setString(2, reference);
      ps.executeUpdate();
    }
  }

  private String virementDeclare(long demande) throws Exception {
    String ref =
        reference(
            mvc.perform(
                    post("/api/v1/demandes/" + demande + "/paiements")
                        .with(jeton(INVESTISSEUR, "INVESTISSEUR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"moyen\":\"VIREMENT\"}"))
                .andExpect(status().isCreated()));
    mvc.perform(
            multipart("/api/v1/paiements/" + ref + "/virement")
                .file(new MockMultipartFile("fichier", "ordre.pdf", "application/pdf", PDF))
                .with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(status().isOk());
    return ref;
  }

  private static RequestPostProcessor fiscal() {
    return jeton(FISCAL, "FISCAL");
  }

  private static String reference(ResultActions r) throws Exception {
    return JSON.readTree(r.andReturn().getResponse().getContentAsString())
        .path("paiement")
        .path("reference")
        .asString();
  }
}
