package gn.gov.mines.emine.catalogue;

import static gn.gov.mines.emine.support.Personas.ADMIN_TECH;
import static gn.gov.mines.emine.support.Personas.GUICHET;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR;
import static gn.gov.mines.emine.support.Personas.MINISTRE;
import static gn.gov.mines.emine.support.Personas.jeton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gn.gov.mines.emine.support.BaseDeDonneesDeTest;
import gn.gov.mines.emine.support.DemandesDeTest;
import gn.gov.mines.emine.support.Personas;
import gn.gov.mines.emine.support.TestIntegration;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

/**
 * US-02.5 (V20) : les 5 workflows (étapes, intervenants, règle de paiement, délais) sont modifiés
 * par l'administrateur, immédiatement et avec une trace (une version par modification) ; un
 * changement de workflow n'affecte pas les demandes en cours ({@code workflow_version}). Workflow
 * EXPORT, document AEX (inutilisés par les autres tests).
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WorkflowsTest extends TestIntegration {

  private static final String EXPORT = "/api/v1/workflows/EXPORT";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  final JsonMapper json = JsonMapper.builder().build();
  final RequestPostProcessor admin = jeton(ADMIN_TECH, "ADMIN_TECH");

  long aexAvant;

  @BeforeAll
  void donnees() throws Exception {
    Personas.charger();
    aexAvant = DemandesDeTest.deposee(INVESTISSEUR, "AEX");
  }

  @Test
  @Order(1)
  void lesCinqWorkflowsSontConsultesParLAdministrateurEtLeMinistre() throws Exception {
    mvc.perform(get("/api/v1/workflows").with(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(5))
        .andExpect(jsonPath("$.elements[0].code").value("TITRES"))
        .andExpect(jsonPath("$.elements[0].enVigueur.etapes.length()").value(6))
        .andExpect(jsonPath("$.elements[0].documents[0].code").value("CAR"))
        .andExpect(
            jsonPath("$.elements[0].documents[0].libelleFr").value("Autorisation de carrière"))
        .andExpect(jsonPath("$.elements[0].documents[0].actif").value(true));
    mvc.perform(get(EXPORT).with(jeton(MINISTRE, "MINISTRE")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.peutModifier").value(false));
    mvc.perform(get("/api/v1/workflows").with(jeton(GUICHET, "GUICHET")))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(2)
  void lAdministrateurModifieLesEtapesEtLaModificationEstTracee() throws Exception {
    modifier(
            admin,
            List.of(
                Map.of("acteur", "BNE", "libelle", "Expertise du lot"),
                Map.of("acteur", "Brigade anti-fraude", "libelle", "Contrôle de provenance"),
                Map.of("acteur", "DNM ou Fiscalité", "libelle", "Contrôle"),
                Map.of("acteur", "Signataire", "libelle", "Signature")),
            "Contrôle de provenance ajouté (test)")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.version").value(2));

    mvc.perform(get(EXPORT).with(admin))
        .andExpect(jsonPath("$.workflow.enVigueur.version").value(2))
        .andExpect(jsonPath("$.workflow.enVigueur.etapes.length()").value(4))
        .andExpect(jsonPath("$.workflow.enVigueur.etapes[1].acteur").value("Brigade anti-fraude"))
        .andExpect(jsonPath("$.workflow.enVigueur.modifieePar").isNotEmpty())
        .andExpect(jsonPath("$.versions[1].version").value(1))
        .andExpect(jsonPath("$.versions[1].etapes.length()").value(3))
        .andExpect(jsonPath("$.peutModifier").value(true));
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "SELECT count(*) FROM journal_audit WHERE table_nom = 'version_workflow'"
                    + " AND utilisateur_id IS NOT NULL")) {
      ResultSet rs = ps.executeQuery();
      rs.next();
      assertThat(rs.getInt(1)).isEqualTo(1);
    }
  }

  @Test
  @Order(3)
  void uneDemandeGardeLaVersionDuWorkflowDeSonDepot() throws Exception {
    long aexApres = DemandesDeTest.deposee(INVESTISSEUR, "AEX");
    assertThat(versionWorkflow(aexAvant)).isEqualTo(1);
    assertThat(versionWorkflow(aexApres)).isEqualTo(2);
  }

  @Test
  @Order(4)
  void uneModificationSansChangementOuSansEtapeEstRefusee() throws Exception {
    modifier(
            admin,
            List.of(
                Map.of("acteur", "BNE", "libelle", "Expertise du lot"),
                Map.of("acteur", "Brigade anti-fraude", "libelle", "Contrôle de provenance"),
                Map.of("acteur", "DNM ou Fiscalité", "libelle", "Contrôle"),
                Map.of("acteur", "Signataire", "libelle", "Signature")),
            "Aucune modification réelle")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("DOC-010"));
    modifier(admin, List.of(), "Toutes les étapes supprimées")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VAL-001"));
    modifier(
            jeton(MINISTRE, "MINISTRE"),
            List.of(Map.of("acteur", "BNE", "libelle", "Expertise")),
            "Modification par le Ministre")
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(5)
  void lApplicationNeModifiePasLesWorkflowsDirectement() {
    assertThatThrownBy(() -> jdbc.update("UPDATE workflow SET libelle = 'x' WHERE code = 'EXPORT'"))
        .rootCause()
        .hasMessageContaining("permission denied");
  }

  private ResultActions modifier(
      RequestPostProcessor qui, List<Map<String, String>> etapes, String motif) throws Exception {
    return mvc.perform(
        post(EXPORT + "/versions")
            .with(qui)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                json.writeValueAsString(
                    Map.of(
                        "libelle",
                        "Export et expertise",
                        "reglePaiement",
                        "Frais d'expertise puis droits d'export",
                        "etapes",
                        etapes,
                        "motif",
                        motif))));
  }

  private static int versionWorkflow(long demande) throws Exception {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement("SELECT workflow_version FROM demande WHERE id = ?")) {
      ps.setLong(1, demande);
      ResultSet rs = ps.executeQuery();
      rs.next();
      return rs.getInt(1);
    }
  }
}
