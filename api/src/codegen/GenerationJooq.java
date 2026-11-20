import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.jooq.codegen.GenerationTool;
import org.jooq.meta.jaxb.Configuration;
import org.jooq.meta.jaxb.Database;
import org.jooq.meta.jaxb.ForcedType;
import org.jooq.meta.jaxb.Generate;
import org.jooq.meta.jaxb.Generator;
import org.jooq.meta.jaxb.Jdbc;
import org.jooq.meta.jaxb.Target;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Génération du code jOOQ depuis le schéma migré (US-00.3).
 *
 * <p>Lancé par Maven (phase generate-sources, plugin exec) comme fichier source Java : démarre un
 * PostgreSQL 16 jetable (Testcontainers), l'amorce avec {@code db/bootstrap/}, applique les
 * migrations Flyway, puis génère le code dans {@code src/main/generated} (jamais versionné, jamais
 * modifié à la main). La génération est sautée si les migrations n'ont pas changé depuis la
 * dernière fois (empreinte SHA-256 dans {@code src/main/generated/.empreinte-migrations}).
 *
 * <p>Arguments : {@code [--force]}.
 */
public class GenerationJooq {

  private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");
  private static final Path BOOTSTRAP = Path.of("../db/bootstrap");
  private static final Path CIBLE = Path.of("src/main/generated");
  private static final Path EMPREINTE = CIBLE.resolve(".empreinte-migrations");
  private static final String PAQUET = "gn.gov.mines.emine.shared.jooq";

  public static void main(String[] args) throws Exception {
    boolean force = List.of(args).contains("--force");
    String empreinte = empreinte(MIGRATIONS, BOOTSTRAP);
    if (!force && Files.exists(EMPREINTE) && Files.readString(EMPREINTE).strip().equals(empreinte)) {
      System.out.println("[jOOQ] Code à jour (migrations inchangées) : génération sautée.");
      return;
    }

    System.out.println("[jOOQ] Démarrage de PostgreSQL 16 (Testcontainers)…");
    String mdpOwner = "owner-codegen-" + System.nanoTime();
    try (PostgreSQLContainer pg =
        new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("emine")
            .withUsername("postgres")
            .withPassword("postgres-codegen")
            .withEnv("EMINE_OWNER_PASSWORD", mdpOwner)
            .withEnv("EMINE_APP_PASSWORD", "app-codegen-" + System.nanoTime())
            .withCopyFileToContainer(
                MountableFile.forHostPath(BOOTSTRAP.toAbsolutePath()), "/docker-entrypoint-initdb.d/")
            .withCommand("postgres", "-c", "shared_buffers=64MB", "-c", "fsync=off")) {
      pg.start();

      Flyway.configure()
          .dataSource(pg.getJdbcUrl(), "emine_owner", mdpOwner)
          .schemas("emine")
          .defaultSchema("emine")
          .createSchemas(false)
          .locations("filesystem:" + MIGRATIONS)
          .load()
          .migrate();

      supprimerAncienCode();
      GenerationTool.generate(configuration(pg.getJdbcUrl(), mdpOwner));
    }
    Files.writeString(EMPREINTE, empreinte + "\n");
    System.out.println("[jOOQ] Code généré dans " + CIBLE.resolve(PAQUET.replace('.', '/')));
  }

  private static Configuration configuration(String url, String mdpOwner) {
    return new Configuration()
        .withJdbc(
            new Jdbc()
                .withDriver("org.postgresql.Driver")
                .withUrl(url)
                .withUser("emine_owner")
                .withPassword(mdpOwner))
        .withGenerator(
            new Generator()
                .withDatabase(
                    new Database()
                        .withName("org.jooq.meta.postgres.PostgresDatabase")
                        .withInputSchema("emine")
                        .withOutputSchemaToDefault(true)
                        // Historique Flyway et partitions mensuelles du journal : hors du code métier
                        .withExcludes("flyway_schema_history | journal_audit_.*")
                        .withForcedTypes(
                            // Dates : Instant en UTC (convention du projet)
                            new ForcedType()
                                .withName("INSTANT")
                                .withIncludeTypes("(?i:timestamp\\ with\\ time\\ zone|timestamptz)"),
                            // citext (e-mails) et inet (adresse IP du journal) lus comme texte
                            new ForcedType().withName("VARCHAR").withIncludeTypes("(?i:citext|inet)")))
                .withGenerate(
                    new Generate()
                        .withJavaTimeTypes(true)
                        .withRecords(true)
                        .withPojos(false)
                        .withDaos(false)
                        .withFluentSetters(true))
                .withTarget(new Target().withPackageName(PAQUET).withDirectory(CIBLE.toString())));
  }

  private static void supprimerAncienCode() throws IOException {
    Path paquet = CIBLE.resolve(PAQUET.replace('.', '/'));
    if (Files.exists(paquet)) {
      try (Stream<Path> fichiers = Files.walk(paquet)) {
        fichiers.sorted((a, b) -> b.compareTo(a)).forEach(GenerationJooq::supprimer);
      }
    }
    Files.createDirectories(CIBLE);
  }

  private static void supprimer(Path p) {
    try {
      Files.delete(p);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String empreinte(Path... dossiers) throws IOException, NoSuchAlgorithmException {
    MessageDigest sha = MessageDigest.getInstance("SHA-256");
    for (Path dossier : dossiers) {
      try (Stream<Path> fichiers = Files.walk(dossier)) {
        for (Path f : fichiers.filter(Files::isRegularFile).sorted().toList()) {
          sha.update(dossier.relativize(f).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
          // Fins de ligne normalisées : même empreinte sous Windows et Linux
          sha.update(Files.readString(f).replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8));
        }
      }
    }
    return HexFormat.of().formatHex(sha.digest());
  }
}
