package gn.gov.mines.emine.shared.erreur;

import static gn.gov.mines.emine.support.Personas.GUICHET;
import static gn.gov.mines.emine.support.Personas.jeton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gn.gov.mines.emine.support.BaseDeDonneesDeTest;
import gn.gov.mines.emine.support.Personas;
import gn.gov.mines.emine.support.TestIntegration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * US-00.8 : toute erreur de l'API suit le format {@code {code, message, details}} (ProblemDetail),
 * avec un code du catalogue et un message FR (par défaut) ou EN ; SYS-001 donne un identifiant
 * d'incident ; les refus des triggers de la base deviennent des codes du catalogue.
 */
@AutoConfigureMockMvc
@Import(ErreursTest.ControleurDeTest.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ErreursTest extends TestIntegration {

  @Autowired MockMvc mvc;
  private long demandeBrouillon;

  @BeforeAll
  void donnees() throws Exception {
    Personas.charger();
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        Statement st = c.createStatement()) {
      ResultSet rs =
          st.executeQuery(
              "INSERT INTO demande (type_document_id, demandeur_id)"
                  + " SELECT td.id, u.id FROM type_document td, utilisateur u"
                  + " WHERE td.code = 'PEX' AND u.keycloak_id = '"
                  + Personas.INVESTISSEUR
                  + "' RETURNING id");
      rs.next();
      demandeBrouillon = rs.getLong(1);
    }
  }

  @Test
  void uneErreurMetierDonneSonCodeSonStatutEtSonMessageFrancais() throws Exception {
    mvc.perform(get("/api/v1/test-erreurs/metier").with(jeton(GUICHET, "GUICHET")))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("DEM-002"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.message").value("Demande introuvable."))
        .andExpect(jsonPath("$.detail").value("Demande introuvable."))
        .andExpect(jsonPath("$.details.numero").value("DEM-2026-999999"));
  }

  @Test
  void leMessageEstEnAnglaisSiLeClientLeDemande() throws Exception {
    mvc.perform(
            get("/api/v1/test-erreurs/metier")
                .header("Accept-Language", "en-GB,en;q=0.9")
                .with(jeton(GUICHET, "GUICHET")))
        .andExpect(jsonPath("$.code").value("DEM-002"))
        .andExpect(jsonPath("$.message").value("Application not found."));
  }

  @Test
  void lesErreursDeSecuriteSontAussiTraduites() throws Exception {
    mvc.perform(get("/api/v1/moi").header("Accept-Language", "en"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH-001"))
        .andExpect(jsonPath("$.message").value("You must sign in to use this service."));
  }

  @Test
  void desDonneesInvalidesDonnentVal001AvecLeChampEnCause() throws Exception {
    mvc.perform(
            post("/api/v1/test-erreurs/validation")
                .with(jeton(GUICHET, "GUICHET"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nom\": \"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VAL-001"))
        .andExpect(jsonPath("$.details.champs[0].champ").value("nom"));
  }

  @Test
  void unJsonIllisibleDonneVal002() throws Exception {
    mvc.perform(
            post("/api/v1/test-erreurs/validation")
                .with(jeton(GUICHET, "GUICHET"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{nom"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VAL-002"));
  }

  @Test
  void uneErreurInattendueDonneSys001AvecUnIdentifiantDIncident() throws Exception {
    String uuid = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    mvc.perform(get("/api/v1/test-erreurs/panne").with(jeton(GUICHET, "GUICHET")))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("SYS-001"))
        .andExpect(jsonPath("$.details.incident").value(matchesPattern(uuid)))
        .andExpect(jsonPath("$.message").value(containsString("Référence de l’incident : ")))
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(containsString("NPE"))));
  }

  @Test
  void uneTransitionInterditeParLaBaseDonneDem001() throws Exception {
    mvc.perform(
            post("/api/v1/test-erreurs/delivrer/" + demandeBrouillon)
                .with(jeton(GUICHET, "GUICHET")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DEM-001"));
  }

  @Test
  void uneAdresseInconnueDonneSys002() throws Exception {
    mvc.perform(get("/api/v1/n-existe-pas").with(jeton(GUICHET, "GUICHET")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SYS-002"));
  }

  @Test
  void uneMethodeNonPriseEnChargeDonneSys005() throws Exception {
    mvc.perform(delete("/api/v1/test-erreurs/metier").with(jeton(GUICHET, "GUICHET")))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.code").value("SYS-005"));
  }

  @Test
  void lesErreursSqlConnuesSontTraduites() {
    assertThat(GestionnaireErreurs.codeBase(sql("P0001", "HPAY-006"))).contains(CodeErreur.PAY_006);
    assertThat(GestionnaireErreurs.codeBase(sql("42501", null))).contains(CodeErreur.PERM_001);
    assertThat(GestionnaireErreurs.codeBase(sql("40001", null))).contains(CodeErreur.SYS_004);
    assertThat(GestionnaireErreurs.codeBase(sql("23505", "nux_paiement_paye")))
        .contains(CodeErreur.PAY_001);
    assertThat(GestionnaireErreurs.codeBase(sql("23505", "nautre_contrainte"))).isEmpty();
  }

  @Test
  void chaqueCodeDuCatalogueAUnMessageEnFrancaisEtEnAnglais(@Autowired FabriqueProblemes f) {
    for (CodeErreur code : CodeErreur.values()) {
      String fr = f.message(code, java.util.Locale.FRENCH, "x");
      String en = f.message(code, java.util.Locale.ENGLISH, "x");
      assertThat(fr).as(code.code()).isNotEqualTo(code.code());
      assertThat(en).as(code.code()).isNotEqualTo(code.code()).isNotEqualTo(fr);
    }
  }

  private static DataIntegrityViolationException sql(String etat, String champ) {
    String brut = "SERROR\0C" + etat + "\0Mmessage" + (champ == null ? "" : "\0" + champ) + "\0";
    return new DataIntegrityViolationException(
        "x", new PSQLException(new ServerErrorMessage(brut)));
  }

  /** Contrôleur de test : chaque route déclenche un type d'erreur. */
  @RestController
  @RequestMapping("/api/v1/test-erreurs")
  static class ControleurDeTest {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    ControleurDeTest(JdbcTemplate jdbc, TransactionTemplate tx) {
      this.jdbc = jdbc;
      this.tx = tx;
    }

    record Corps(@NotBlank String nom) {}

    @GetMapping("/metier")
    String metier() {
      throw new ErreurMetier(CodeErreur.DEM_002, Map.of("numero", "DEM-2026-999999"));
    }

    @PostMapping("/validation")
    String validation(@Valid @RequestBody Corps corps) {
      return corps.nom();
    }

    @GetMapping("/panne")
    String panne() {
      throw new IllegalStateException("NPE simulée : détail technique à ne pas divulguer");
    }

    @PostMapping("/delivrer/{id}")
    void delivrer(@PathVariable long id) {
      tx.executeWithoutResult(
          s -> jdbc.update("UPDATE demande SET statut = 'delivree' WHERE id = ?", id));
    }
  }
}
