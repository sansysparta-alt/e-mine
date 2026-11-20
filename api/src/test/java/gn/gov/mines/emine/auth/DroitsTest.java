package gn.gov.mines.emine.auth;

import static gn.gov.mines.emine.support.Personas.jeton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gn.gov.mines.emine.support.BaseDeDonneesDeTest;
import gn.gov.mines.emine.support.Personas;
import gn.gov.mines.emine.support.TestIntegration;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * US-01.6 (partiel V1) : les droits de chaque profil viennent de {@code profil_permission} ; le
 * droit « déposer » est appliqué (PERM-001 sinon) pour chacun des 16 profils ; la base fait foi
 * face au rôle porté par le jeton.
 */
@AutoConfigureMockMvc
@Import(DroitsTest.ControleurDeTest.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DroitsTest extends TestIntegration {

  /** Profils autorisés à déposer selon la matrice du cahier des charges. */
  static final Set<String> DEPOSANTS = Set.of("INVESTISSEUR", "SOCIETE_MINIERE", "PME");

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  /** Un compte par profil : code du profil → identifiant Keycloak. */
  private final Map<String, String> comptes = new LinkedHashMap<>();

  @BeforeAll
  void unCompteParProfil() throws Exception {
    Personas.charger();
    try (Connection c = BaseDeDonneesDeTest.connexionOwner()) {
      ResultSet profils = c.createStatement().executeQuery("SELECT code FROM profil ORDER BY id");
      while (profils.next()) {
        String profil = profils.getString(1);
        String kc = UUID.randomUUID().toString();
        try (PreparedStatement ps =
            c.prepareStatement(
                "INSERT INTO utilisateur (profil_id, nom, prenom, email, telephone, keycloak_id)"
                    + " SELECT id, 'Fictif', ?, ?, '+224600000000', ?::uuid FROM profil"
                    + " WHERE code = ?")) {
          ps.setString(1, profil);
          ps.setString(2, "droits." + kc + "@exemple.test");
          ps.setString(3, kc);
          ps.setString(4, profil);
          ps.executeUpdate();
        }
        comptes.put(profil, kc);
      }
    }
    assertThat(comptes).hasSize(16);
  }

  List<String> profils() {
    return new ArrayList<>(comptes.keySet());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("profils")
  void lesPermissionsDuProfilSontCellesDeLaMatrice(String profil) throws Exception {
    String corps =
        mvc.perform(get("/api/v1/moi").with(jeton(comptes.get(profil), profil)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode moi = JsonMapper.builder().build().readTree(corps);
    List<String> recues = new ArrayList<>();
    moi.path("permissions").forEach(p -> recues.add(p.asString()));
    List<String> matrice =
        jdbc.queryForList(
            "SELECT pm.code FROM profil_permission pp JOIN permission pm ON pm.id ="
                + " pp.permission_id JOIN profil p ON p.id = pp.profil_id WHERE p.code = ?",
            String.class,
            profil);
    assertThat(recues).containsExactlyInAnyOrderElementsOf(matrice);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("profils")
  void seulsLesProfilsDeposantsPeuventDeposer(String profil) throws Exception {
    var resultat =
        mvc.perform(post("/api/v1/test-droits/deposer").with(jeton(comptes.get(profil), profil)));
    if (DEPOSANTS.contains(profil)) {
      resultat.andExpect(status().isOk());
    } else {
      resultat
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("PERM-001"))
          .andExpect(jsonPath("$.message").value("Votre profil ne permet pas cette action."));
    }
  }

  @Test
  void laBaseFaitFoiFaceAuRoleDuJeton() throws Exception {
    // L'agent du guichet présente un jeton portant le rôle INVESTISSEUR : refusé quand même.
    mvc.perform(post("/api/v1/test-droits/deposer").with(jeton(Personas.GUICHET, "INVESTISSEUR")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PERM-001"));
  }

  @Test
  void laMatriceDuSchemaDonneDeposerAuxTroisProfilsExternesAttendus() {
    assertThat(
            jdbc.queryForList(
                "SELECT p.code FROM profil_permission pp JOIN permission pm ON pm.id ="
                    + " pp.permission_id JOIN profil p ON p.id = pp.profil_id"
                    + " WHERE pm.code = 'DEPOSER'",
                String.class))
        .containsExactlyInAnyOrderElementsOf(DEPOSANTS);
  }

  /** Action de test protégée par le droit « déposer ». */
  @RestController
  static class ControleurDeTest {
    @PostMapping("/api/v1/test-droits/deposer")
    @PreAuthorize("hasAuthority('PERM_DEPOSER')")
    String deposer() {
      return "ok";
    }
  }
}
