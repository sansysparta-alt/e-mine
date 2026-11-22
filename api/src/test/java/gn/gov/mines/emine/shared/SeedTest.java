package gn.gov.mines.emine.shared;

import static org.assertj.core.api.Assertions.assertThat;

import gn.gov.mines.emine.support.BaseDeDonneesDeTest;
import gn.gov.mines.emine.support.TestIntegration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * US-00.9 (version V1) : le seed minimal est rejouable, ne crée que des données fictives et ne crée
 * aucune demande (la première demande de la démo reçoit le numéro 000001).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SeedTest extends TestIntegration {

  private static final Path SEED = Path.of("..", "db", "seed");

  private long demandesAvant;
  private long paiementsAvant;

  @BeforeAll
  void jouerLeSeedDeuxFois() throws Exception {
    demandesAvant = compter("SELECT count(*) FROM demande");
    paiementsAvant = compter("SELECT count(*) FROM paiement");
    jouerLeSeed();
    jouerLeSeed();
  }

  @Test
  void leSeedEstRejouableSansDoublon() throws SQLException {
    assertThat(compter("SELECT count(*) FROM organisation WHERE nif LIKE 'NIF-TEST-%'"))
        .isEqualTo(5);
    assertThat(
            compter(
                "SELECT count(*) FROM utilisateur WHERE keycloak_id::text LIKE"
                    + " '00000000-0000-4000-8000-%'"))
        .as(
            "5 personas de la V1, un second investisseur, le Ministre (US-05.4) et les avis"
                + " techniques DNM, Géologue, Inspecteur (US-05.3)")
        .isEqualTo(10);
  }

  @Test
  void cinqTypesDOrganisationsDifferents() throws SQLException {
    assertThat(
            compter(
                "SELECT count(DISTINCT type_org) FROM organisation WHERE nif LIKE 'NIF-TEST-%'"))
        .isGreaterThanOrEqualTo(4);
  }

  @Test
  void lesCinqPersonasDeLaDemoExistent() throws SQLException {
    assertThat(
            compter(
                "SELECT count(DISTINCT p.code) FROM utilisateur u JOIN profil p ON p.id ="
                    + " u.profil_id WHERE p.code IN"
                    + " ('INVESTISSEUR','GUICHET','DIRECTEUR','FISCAL','ADMIN_TECH')"))
        .isEqualTo(5);
  }

  @Test
  void leCatalogueEtLesTarifsSontDisponibles() throws SQLException {
    assertThat(compter("SELECT count(*) FROM type_document WHERE actif")).isEqualTo(15);
    assertThat(
            compter(
                "SELECT count(*) FROM type_document td WHERE NOT EXISTS (SELECT 1 FROM tarif t"
                    + " WHERE t.type_document_id = td.id AND t.actif)"))
        .as("documents sans tarif")
        .isZero();
  }

  @Test
  void leSeedNeCreeAucuneDemandeNiAucunPaiement() throws SQLException {
    assertThat(compter("SELECT count(*) FROM demande")).isEqualTo(demandesAvant);
    assertThat(compter("SELECT count(*) FROM paiement")).isEqualTo(paiementsAvant);
  }

  @Test
  void uniquementDesDonneesFictives() throws SQLException {
    assertThat(
            compter(
                "SELECT count(*) FROM organisation WHERE nif LIKE 'NIF-TEST-%' AND email NOT LIKE"
                    + " '%.test'"))
        .isZero();
    assertThat(
            compter(
                "SELECT count(*) FROM utilisateur WHERE keycloak_id::text LIKE"
                    + " '00000000-0000-4000-8000-%' AND email NOT LIKE '%.test'"))
        .isZero();
  }

  private static void jouerLeSeed() throws IOException, SQLException {
    List<Path> fichiers;
    try (Stream<Path> s = Files.list(SEED)) {
      fichiers = s.filter(f -> f.toString().endsWith(".sql")).sorted().toList();
    }
    assertThat(fichiers).isNotEmpty();
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        Statement st = c.createStatement()) {
      for (Path f : fichiers) {
        st.execute(Files.readString(f));
      }
    }
  }

  private static long compter(String sql) throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        Statement st = c.createStatement()) {
      st.execute("SET search_path = emine, public");
      ResultSet rs = st.executeQuery(sql);
      rs.next();
      return rs.getLong(1);
    }
  }
}
