package gn.gov.mines.emine.paiement;

import static gn.gov.mines.emine.support.Personas.FISCAL;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR;
import static gn.gov.mines.emine.support.Personas.jeton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * US-04.12 : pour un paiement au montant inférieur, le Responsable fiscal demande le complément au
 * lieu de rejeter. Le paiement initial n'est pas rejeté ; le demandeur paie le reste par le circuit
 * normal ; la recette est comptée une seule fois, à la date du complément ; le complément a son
 * propre reçu, qui renvoie au paiement initial.
 */
@AutoConfigureMockMvc
@Import(StockageEnMemoire.Config.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ComplementPaiementTest extends TestIntegration {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final byte[] PDF =
      "%PDF-1.7\n1 0 obj << >> endobj\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
  private static final String EN_TETE = "date_operation;libelle;montant_gnf;sens\n";
  private static final AtomicInteger UNIQUE = new AtomicInteger();
  private static final String MOTIF = "Montant reçu inférieur aux frais de dossier dus.";

  @Autowired MockMvc mvc;

  @BeforeAll
  void personas() throws Exception {
    Personas.charger();
  }

  @Test
  void leComplementEstDemandeSansRejeterPuisPayeEtLaRecetteCompteeUneFois() throws Exception {
    long demande = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    String initial = virementDeclare(demande);
    long montantDu = montantDu(initial);
    long ligne = ligneImportee("18/11/2026;VIR " + initial + ";" + (montantDu - 1_000_000) + ";C");
    assertThat(decisions(ligne)).containsExactly("COMPLETER", "REJETER");

    // Motif obligatoire, communiqué au demandeur.
    decider(ligne, "{\"decision\":\"COMPLETER\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details.champs[0].champ").value("motif"));
    decider(ligne, "{\"decision\":\"COMPLETER\",\"motif\":\"" + MOTIF + "\"}")
        .andExpect(status().isNoContent());

    assertThat(DemandesDeTest.statutPaiement(initial)).isEqualTo("complement_attendu");
    assertThat(DemandesDeTest.notifications(demande, "PAIEMENT_COMPLEMENT")).isPositive();
    assertThat(DemandesDeTest.notifications(demande, "PAIEMENT_REJETE")).isZero();
    mvc.perform(get("/api/v1/paiements/" + initial).with(investisseur()))
        .andExpect(jsonPath("$.statut").value("complement_attendu"))
        .andExpect(jsonPath("$.resteAPayerGnf").value(1_000_000))
        .andExpect(jsonPath("$.motifComplement").value(MOTIF))
        .andExpect(jsonPath("$.complementEcheance").isNotEmpty());
    // La demande revient dans « Ce que je dois faire » ; seul le reste est à payer.
    mvc.perform(get("/api/v1/demandes/" + demande).with(investisseur()))
        .andExpect(jsonPath("$.aTraiterParMoi").value(true))
        .andExpect(jsonPath("$.fraisPayes").value(false));
    mvc.perform(get("/api/v1/demandes/" + demande + "/paiements/a-payer").with(investisseur()))
        .andExpect(jsonPath("$.montantGnf").value(1_000_000))
        .andExpect(jsonPath("$.complement.reference").value(initial))
        .andExpect(jsonPath("$.complement.resteGnf").value(1_000_000));
    // Rien n'est compté comme recette tant que le complément n'est pas payé.
    assertThat(lignesJournal("2026-11-18", initial)).isZero();

    // Le demandeur paie le reste par le circuit normal.
    String complement = virementDeclare(demande);
    mvc.perform(get("/api/v1/paiements/" + complement).with(investisseur()))
        .andExpect(jsonPath("$.montantGnf").value(1_000_000))
        .andExpect(jsonPath("$.complementDe").value(initial));
    long ligneComplement = ligneImportee("20/11/2026;VIR " + complement + ";1000000;C");
    assertThat(statutLigne(ligneComplement)).isEqualTo("rapprochee");

    assertThat(DemandesDeTest.statutPaiement(complement)).isEqualTo("paye");
    assertThat(DemandesDeTest.statutPaiement(initial)).isEqualTo("paye");
    mvc.perform(get("/api/v1/paiements/" + initial).with(investisseur()))
        .andExpect(jsonPath("$.montantRecuGnf").value(montantDu))
        .andExpect(jsonPath("$.recuNumero").isNotEmpty())
        .andExpect(jsonPath("$.datePaiement").value(org.hamcrest.Matchers.startsWith("2026-11-2")));
    mvc.perform(get("/api/v1/demandes/" + demande).with(investisseur()))
        .andExpect(jsonPath("$.fraisPayes").value(true));
    // Une seule recette, celle du paiement initial au montant dû, datée du complément.
    assertThat(lignesJournal("2026-11-20", initial)).isEqualTo(1);
    assertThat(lignesJournal("2026-11-20", complement)).isZero();
    // Le complément a son propre reçu.
    mvc.perform(get("/api/v1/paiements/" + complement + "/recu").with(investisseur()))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/paiements/" + initial + "/recu").with(investisseur()))
        .andExpect(status().isOk());
  }

  @Test
  void laBaseRefuseUnComplementQuiNeRegleePasExactementLeReste() throws Exception {
    long demande = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    String initial = virementDeclare(demande);
    long montantDu = montantDu(initial);
    long ligne = ligneImportee("18/11/2026;VIR " + initial + ";" + (montantDu - 500_000) + ";C");
    decider(ligne, "{\"decision\":\"COMPLETER\",\"motif\":\"" + MOTIF + "\"}")
        .andExpect(status().isNoContent());

    assertThatThrownBy(() -> complementDirect(initial, demande, 400_000))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("Aucun complément");
  }

  @Test
  void echeanceDepasseeLeResponsableFiscalPeutRejeterEtLaSommeEstProposeeAuRemboursement()
      throws Exception {
    long demande = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    String initial = virementDeclare(demande);
    long montantDu = montantDu(initial);
    long recu = montantDu - 2_000_000;
    long ligne = ligneImportee("18/11/2026;VIR " + initial + ";" + recu + ";C");
    decider(ligne, "{\"decision\":\"COMPLETER\",\"motif\":\"" + MOTIF + "\"}")
        .andExpect(status().isNoContent());
    // Avant l'échéance : rien à décider.
    assertThat(elementPaiement(initial)).isNull();

    echeancePassee(initial);
    JsonNode e = elementPaiement(initial);
    assertThat(e.path("nature").asString()).isEqualTo("complement_echu");
    assertThat(e.path("decisions").get(0).asString()).isEqualTo("REJETER");
    assertThat(e.path("decisions")).hasSize(1);

    mvc.perform(
            post("/api/v1/encaissements/paiements/" + initial + "/decision")
                .with(fiscal())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"decision\":\"REJETER\",\"motif\":\"Complément non reçu à l'échéance.\"}"))
        .andExpect(status().isNoContent());
    assertThat(DemandesDeTest.statutPaiement(initial)).isEqualTo("rejete");
    assertThat(remboursementPropose(initial)).isEqualTo(recu);
  }

  // --- Outils -----------------------------------------------------------------------------

  private long ligneImportee(String ligneCsv) throws Exception {
    String csv = EN_TETE + ligneCsv + "\n";
    String corps =
        mvc.perform(
                multipart("/api/v1/releves")
                    .file(
                        new MockMultipartFile(
                            "fichier",
                            "complement" + UNIQUE.incrementAndGet() + ".csv",
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

  private java.util.List<String> decisions(long ligne) throws Exception {
    for (JsonNode e : file().path("elements")) {
      if (e.path("ligneId").asLong() == ligne) {
        java.util.List<String> d = new java.util.ArrayList<>();
        e.path("decisions").forEach(n -> d.add(n.asString()));
        return d;
      }
    }
    throw new AssertionError("Ligne " + ligne + " absente de la file à vérifier");
  }

  private JsonNode elementPaiement(String reference) throws Exception {
    for (JsonNode e : file().path("elements")) {
      if ("paiement".equals(e.path("type").asString())
          && reference.equals(e.path("referencePaiement").asString())) {
        return e;
      }
    }
    return null;
  }

  private ResultActions decider(long ligne, String corps) throws Exception {
    return mvc.perform(
        post("/api/v1/encaissements/lignes/" + ligne + "/decision")
            .with(fiscal())
            .contentType(MediaType.APPLICATION_JSON)
            .content(corps));
  }

  private long lignesJournal(String jour, String reference) throws Exception {
    JsonNode page =
        JSON.readTree(
            mvc.perform(
                    get("/api/v1/encaissements/journal?taille=100&du=" + jour + "&au=" + jour)
                        .with(fiscal()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    long n = 0;
    for (JsonNode l : page.path("elements")) {
      if (reference.equals(l.path("reference").asString())) {
        n++;
      }
    }
    return n;
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

  private static String statutLigne(long ligne) throws SQLException {
    return valeur("SELECT statut::text FROM ligne_releve WHERE id = ?", ligne);
  }

  private static void complementDirect(String initial, long demande, long montant)
      throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "INSERT INTO paiement (demande_id, objet, moyen, montant_gnf, complement_de)"
                    + " SELECT ?, objet, 'VIREMENT', ?, id FROM paiement WHERE reference = ?")) {
      ps.setLong(1, demande);
      ps.setLong(2, montant);
      ps.setString(3, initial);
      ps.executeUpdate();
    }
  }

  private static void echeancePassee(String reference) throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "UPDATE paiement SET complement_echeance = now() - interval '1 day'"
                    + " WHERE reference = ?")) {
      ps.setString(1, reference);
      ps.executeUpdate();
    }
  }

  private static long remboursementPropose(String reference) throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "SELECT r.montant_gnf FROM remboursement r JOIN paiement p ON p.id = r.paiement_id"
                    + " WHERE p.reference = ?")) {
      ps.setString(1, reference);
      ResultSet rs = ps.executeQuery();
      assertThat(rs.next()).isTrue();
      return rs.getLong(1);
    }
  }

  private static String valeur(String sql, long id) throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setLong(1, id);
      ResultSet rs = ps.executeQuery();
      rs.next();
      return rs.getString(1);
    }
  }

  private static RequestPostProcessor fiscal() {
    return jeton(FISCAL, "FISCAL");
  }

  private static RequestPostProcessor investisseur() {
    return jeton(INVESTISSEUR, "INVESTISSEUR");
  }
}
