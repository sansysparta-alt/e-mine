package gn.gov.mines.emine.auth;

import static org.assertj.core.api.Assertions.assertThat;

import gn.gov.mines.emine.support.BaseDeDonneesDeTest;
import gn.gov.mines.emine.support.TestIntegration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * US-01.1 : le realm versionné (infra/keycloak/realm-emine.json) respecte les règles du cahier des
 * charges, ne contient aucun secret, et ses personas correspondent au seed de la base.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RealmKeycloakTest extends TestIntegration {

  private static final Path REALM = Path.of("..", "infra", "keycloak", "realm-emine.json");
  private static final Path SEED = Path.of("..", "db", "seed", "01-personas.sql");

  @Autowired JdbcTemplate jdbc;
  private JsonNode realm;

  @BeforeAll
  void lire() throws IOException {
    realm = JsonMapper.builder().build().readTree(Files.readString(REALM));
  }

  @Test
  void lesRolesSontLesCodesDesSeizeProfils() {
    List<String> roles = new ArrayList<>();
    realm.path("roles").path("realm").forEach(r -> roles.add(r.path("name").asString()));
    List<String> profils = jdbc.queryForList("SELECT code FROM profil", String.class);
    assertThat(profils).hasSize(16);
    assertThat(roles).containsExactlyInAnyOrderElementsOf(profils);
  }

  @Test
  void laPageDeConnexionUtiliseLeThemeEmine() throws IOException {
    assertThat(realm.path("loginTheme").asString()).isEqualTo("emine");
    Path theme = Path.of("..", "infra", "keycloak", "themes", "emine", "login");
    assertThat(Files.readString(theme.resolve("theme.properties")))
        .contains("parent=keycloak", "css/emine.css");
    String css = Files.readString(theme.resolve("resources/css/emine.css"));
    assertThat(css)
        .contains("--emine-royal: #0b3a8f")
        .doesNotContain("http://", "https://", "@import");
    assertThat(theme.resolve("messages/messages_fr.properties")).exists();
    assertThat(theme.resolve("messages/messages_en.properties")).exists();
  }

  @Test
  void politiqueDeMotDePasseDouzeCaracteresMajusculeChiffreSymbole() {
    String politique = realm.path("passwordPolicy").asString();
    assertThat(politique).contains("length(12)", "upperCase(1)", "digits(1)", "specialChars(1)");
  }

  @Test
  void blocageQuinzeMinutesApresCinqEchecs() {
    assertThat(realm.path("bruteForceProtected").asBoolean()).isTrue();
    assertThat(realm.path("failureFactor").asInt()).isEqualTo(5);
    assertThat(realm.path("waitIncrementSeconds").asInt()).isEqualTo(900);
    assertThat(realm.path("maxFailureWaitSeconds").asInt()).isEqualTo(900);
    assertThat(realm.path("permanentLockout").asBoolean()).isFalse();
  }

  @Test
  void aucunSecretDansLeFichierVersionne() {
    realm
        .path("users")
        .forEach(
            u ->
                u.path("credentials")
                    .forEach(
                        c -> assertThat(c.path("value").asString()).isEqualTo("${DEMO_PASSWORD}")));
    realm
        .path("clients")
        .forEach(
            c -> {
              if (!c.path("publicClient").asBoolean()) {
                assertThat(c.path("secret").asString()).startsWith("${").endsWith("}");
              }
            });
  }

  @Test
  void clientApplicationEnAuthorizationCodeAvecPkce() {
    JsonNode app = client("emine-app");
    assertThat(app.path("standardFlowEnabled").asBoolean()).isTrue();
    assertThat(app.path("implicitFlowEnabled").asBoolean()).isFalse();
    assertThat(app.path("directAccessGrantsEnabled").asBoolean()).isFalse();
    assertThat(app.path("attributes").path("pkce.code.challenge.method").asString())
        .isEqualTo("S256");
  }

  @Test
  void lesPersonasDuRealmSontLiesAuSeedAvecLeBonProfil() throws IOException, SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        Statement st = c.createStatement()) {
      st.execute(Files.readString(SEED));
      st.execute(Files.readString(SEED)); // idempotent
    }
    List<JsonNode> personas = new ArrayList<>();
    realm.path("users").forEach(personas::add);
    assertThat(personas).hasSizeGreaterThanOrEqualTo(5);

    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "SELECT p.code, u.email FROM utilisateur u JOIN profil p ON p.id = u.profil_id"
                    + " WHERE u.keycloak_id = ?")) {
      for (JsonNode u : personas) {
        ps.setObject(1, UUID.fromString(u.path("id").asString()));
        ResultSet rs = ps.executeQuery();
        assertThat(rs.next()).as("persona %s présent en base", u.path("email")).isTrue();
        assertThat(rs.getString(1)).isEqualTo(u.path("realmRoles").get(0).asString());
        assertThat(rs.getString(2)).isEqualTo(u.path("email").asString()).endsWith(".test");
        assertThat(rs.next()).isFalse();
      }
    }
  }

  private JsonNode client(String clientId) {
    for (JsonNode c : realm.path("clients")) {
      if (clientId.equals(c.path("clientId").asString())) {
        return c;
      }
    }
    throw new AssertionError("client absent : " + clientId);
  }
}
