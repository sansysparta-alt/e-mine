package gn.gov.mines.emine.support;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * PostgreSQL 16 de test (Testcontainers), amorcé comme l'environnement de dev : les scripts de
 * {@code db/bootstrap/} créent les rôles, les extensions et le schéma, puis Flyway migre en
 * emine_owner et l'API se connecte en emine_app (soumise à la RLS). Un seul conteneur pour toute la
 * suite de tests. Mots de passe générés pour les tests uniquement.
 */
public final class BaseDeDonneesDeTest {

  public static final String MDP_OWNER = "owner-test-" + System.nanoTime();
  public static final String MDP_APP = "app-test-" + System.nanoTime();

  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:16-alpine")
          .withDatabaseName("emine")
          .withUsername("postgres")
          .withPassword("postgres-test")
          .withEnv("EMINE_OWNER_PASSWORD", MDP_OWNER)
          .withEnv("EMINE_APP_PASSWORD", MDP_APP)
          .withCopyFileToContainer(
              MountableFile.forHostPath(Path.of("..", "db", "bootstrap").toAbsolutePath()),
              "/docker-entrypoint-initdb.d/")
          .withCommand("postgres", "-c", "shared_buffers=64MB", "-c", "fsync=off");

  private BaseDeDonneesDeTest() {}

  public static synchronized PostgreSQLContainer demarrer() {
    if (!POSTGRES.isRunning()) {
      POSTGRES.start();
    }
    return POSTGRES;
  }

  /** Propriétés Spring : datasource en emine_app, Flyway en emine_owner. */
  public static void proprietes(DynamicPropertyRegistry registry) {
    demarrer();
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", () -> "emine_app");
    registry.add("spring.datasource.password", () -> MDP_APP);
    registry.add("spring.flyway.user", () -> "emine_owner");
    registry.add("spring.flyway.password", () -> MDP_OWNER);
  }

  /**
   * Connexion directe en propriétaire du schéma (non soumis à la RLS), pour préparer les données.
   */
  public static Connection connexionOwner() throws SQLException {
    return DriverManager.getConnection(demarrer().getJdbcUrl(), "emine_owner", MDP_OWNER);
  }
}
