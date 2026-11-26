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
import java.time.LocalDate;
import java.time.ZoneId;
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
 * US-02.5 et US-02.8 (V20, V27) : les 5 workflows sont consultés par l'administrateur et le
 * Ministre ; toute modification est <b>proposée par l'administrateur, validée par le Ministre, puis
 * appliquée à sa date d'effet</b>, et tracée ; une demande garde la version de son dépôt ({@code
 * workflow_version}). Workflow EXPORT, document AEX (inutilisés par les autres tests).
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WorkflowsTest extends TestIntegration {

  private static final String EXPORT = "/api/v1/workflows/EXPORT";

  private static final List<Map<String, String>> QUATRE_ETAPES =
      List.of(
          Map.of("acteur", "BNE", "libelle", "Expertise du lot"),
          Map.of("acteur", "Brigade anti-fraude", "libelle", "Contrôle de provenance"),
          Map.of("acteur", "DNM ou Fiscalité", "libelle", "Contrôle"),
          Map.of("acteur", "Signataire", "libelle", "Signature"));

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  final JsonMapper json = JsonMapper.builder().build();
  final RequestPostProcessor admin = jeton(ADMIN_TECH, "ADMIN_TECH");
  final RequestPostProcessor ministre = jeton(MINISTRE, "MINISTRE");
  final LocalDate aujourdhui = LocalDate.now(ZoneId.of("Africa/Conakry"));

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
        .andExpect(jsonPath("$.aValider").value(0))
        .andExpect(jsonPath("$.elements[0].code").value("TITRES"))
        .andExpect(jsonPath("$.elements[0].enVigueur.etat").value("en_vigueur"))
        .andExpect(jsonPath("$.elements[0].enVigueur.etapes.length()").value(6))
        .andExpect(jsonPath("$.elements[0].documents[0].code").value("CAR"))
        .andExpect(
            jsonPath("$.elements[0].documents[0].libelleFr").value("Autorisation de carrière"))
        .andExpect(jsonPath("$.elements[0].documents[0].actif").value(true));
    mvc.perform(get(EXPORT).with(ministre))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.peutProposer").value(false))
        .andExpect(jsonPath("$.peutDecider").value(true));
    mvc.perform(get("/api/v1/workflows").with(jeton(GUICHET, "GUICHET")))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(2)
  void lAdministrateurProposeEtRienNeChangeAvantLaDecisionDuMinistre() throws Exception {
    proposer(admin, QUATRE_ETAPES, "Contrôle de provenance ajouté (test)", aujourdhui)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.version").value(2));

    mvc.perform(get(EXPORT).with(admin))
        .andExpect(jsonPath("$.workflow.enVigueur.version").value(1))
        .andExpect(jsonPath("$.workflow.aValider.version").value(2))
        .andExpect(jsonPath("$.workflow.aValider.etat").value("a_valider"))
        .andExpect(jsonPath("$.workflow.aValider.modifieePar").isNotEmpty())
        .andExpect(jsonPath("$.peutProposer").value(false));
    mvc.perform(get("/api/v1/workflows").with(ministre)).andExpect(jsonPath("$.aValider").value(1));
    // Une seule proposition ouverte ; l'administrateur ne décide pas, le Ministre ne propose pas.
    proposer(admin, QUATRE_ETAPES.subList(0, 2), "Seconde proposition (test)", aujourdhui)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DOC-008"));
    mvc.perform(post(EXPORT + "/versions/2/approbation").with(admin))
        .andExpect(status().isForbidden());
    proposer(ministre, QUATRE_ETAPES, "Proposition du Ministre (test)", aujourdhui)
        .andExpect(status().isForbidden());
    // Tant que le Ministre n'a pas validé, une demande déposée prend la version en vigueur.
    assertThat(versionWorkflow(DemandesDeTest.deposee(INVESTISSEUR, "AEX"))).isEqualTo(1);
  }

  @Test
  @Order(3)
  void leMinistreValideEtLaVersionSAppliqueAuxDemandesDeposeesEnsuite() throws Exception {
    mvc.perform(post(EXPORT + "/versions/2/approbation").with(ministre))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dateEffet").value(aujourdhui.toString()));
    mvc.perform(get(EXPORT).with(admin))
        .andExpect(jsonPath("$.workflow.enVigueur.version").value(2))
        .andExpect(jsonPath("$.workflow.enVigueur.etapes[1].acteur").value("Brigade anti-fraude"))
        .andExpect(jsonPath("$.workflow.enVigueur.decideePar").isNotEmpty())
        .andExpect(jsonPath("$.workflow.aValider").isEmpty())
        .andExpect(jsonPath("$.versions[1].version").value(1))
        .andExpect(jsonPath("$.versions[1].etat").value("remplacee"))
        .andExpect(jsonPath("$.peutProposer").value(true));
    long aexApres = DemandesDeTest.deposee(INVESTISSEUR, "AEX");
    assertThat(versionWorkflow(aexAvant)).isEqualTo(1);
    assertThat(versionWorkflow(aexApres)).isEqualTo(2);
    // Proposition et validation tracées au nom de leurs auteurs.
    assertThat(auteursTraces()).isGreaterThanOrEqualTo(2);
  }

  @Test
  @Order(4)
  void uneVersionValideeAvecUneDateAVenirEstProgrammeeSansToucherLesDemandes() throws Exception {
    LocalDate effet = aujourdhui.plusDays(30);
    proposer(admin, QUATRE_ETAPES.subList(0, 3), "Signature déléguée (test)", effet)
        .andExpect(status().isCreated());
    // Retrait par l'administrateur, puis refus motivé du Ministre.
    mvc.perform(post(EXPORT + "/versions/3/retrait").with(admin)).andExpect(status().isNoContent());
    proposer(admin, QUATRE_ETAPES.subList(0, 3), "Signature déléguée (test)", effet)
        .andExpect(jsonPath("$.version").value(4));
    refuser(4, "court").andExpect(status().isBadRequest());
    refuser(4, "Le circuit doit garder une signature distincte.").andExpect(status().isNoContent());
    mvc.perform(post(EXPORT + "/versions/4/approbation").with(ministre))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DOC-009"));
    // Validée avec une date à venir : programmée, la version 2 reste en vigueur.
    proposer(admin, QUATRE_ETAPES.subList(0, 3), "Signature déléguée (test)", effet)
        .andExpect(jsonPath("$.version").value(5));
    mvc.perform(post(EXPORT + "/versions/5/approbation").with(ministre))
        .andExpect(jsonPath("$.dateEffet").value(effet.toString()));
    mvc.perform(get(EXPORT).with(admin))
        .andExpect(jsonPath("$.workflow.enVigueur.version").value(2))
        .andExpect(jsonPath("$.workflow.programmee.version").value(5))
        .andExpect(jsonPath("$.workflow.programmee.dateEffet").value(effet.toString()))
        .andExpect(jsonPath("$.versions[1].etat").value("refusee"))
        .andExpect(
            jsonPath("$.versions[1].motifRefus")
                .value("Le circuit doit garder une signature distincte."))
        .andExpect(jsonPath("$.versions[2].etat").value("retiree"));
    assertThat(versionWorkflow(DemandesDeTest.deposee(INVESTISSEUR, "AEX"))).isEqualTo(2);
  }

  @Test
  @Order(5)
  void unePropositionSansChangementSansEtapeOuDansLePasseEstRefusee() throws Exception {
    proposer(admin, QUATRE_ETAPES.subList(0, 3), "Aucune modification réelle", aujourdhui)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("DOC-010"));
    proposer(admin, List.of(), "Toutes les étapes supprimées", aujourdhui)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VAL-001"));
    proposer(admin, QUATRE_ETAPES, "Date d'effet passée (test)", aujourdhui.minusDays(1))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VAL-001"));
  }

  @Test
  @Order(6)
  void lApplicationNeModifiePasLesWorkflowsDirectement() {
    assertThatThrownBy(() -> jdbc.update("UPDATE workflow SET libelle = 'x' WHERE code = 'EXPORT'"))
        .rootCause()
        .hasMessageContaining("permission denied");
    assertThatThrownBy(
            () -> jdbc.update("UPDATE version_workflow SET statut = 'publiee' WHERE version = 1"))
        .rootCause()
        .hasMessageContaining("permission denied");
  }

  private ResultActions proposer(
      RequestPostProcessor qui, List<Map<String, String>> etapes, String motif, LocalDate effet)
      throws Exception {
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
                        motif,
                        "dateEffet",
                        effet.toString()))));
  }

  private ResultActions refuser(int version, String motif) throws Exception {
    return mvc.perform(
        post(EXPORT + "/versions/" + version + "/refus")
            .with(ministre)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("motif", motif))));
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

  private static int auteursTraces() throws Exception {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "SELECT count(DISTINCT utilisateur_id) FROM journal_audit"
                    + " WHERE table_nom = 'version_workflow' AND utilisateur_id IS NOT NULL")) {
      ResultSet rs = ps.executeQuery();
      rs.next();
      return rs.getInt(1);
    }
  }
}
