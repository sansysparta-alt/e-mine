package gn.gov.mines.emine.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Personas de démo du seed ({@code db/seed}) et jetons OIDC simulés pour MockMvc. Identifiants
 * Keycloak identiques à ceux du realm versionné.
 */
public final class Personas {

  public static final String INVESTISSEUR = "00000000-0000-4000-8000-000000000009";
  public static final String INVESTISSEUR_2 = "00000000-0000-4000-8000-000000000109";
  public static final String GUICHET = "00000000-0000-4000-8000-000000000002";
  public static final String DIRECTEUR = "00000000-0000-4000-8000-000000000007";
  public static final String FISCAL = "00000000-0000-4000-8000-000000000006";
  public static final String ADMIN_TECH = "00000000-0000-4000-8000-000000000001";

  private Personas() {}

  /** Joue tous les fichiers du seed (idempotents) en propriétaire du schéma. */
  public static void charger() throws IOException, SQLException {
    List<Path> fichiers;
    try (Stream<Path> s = Files.list(Path.of("..", "db", "seed"))) {
      fichiers = s.filter(f -> f.toString().endsWith(".sql")).sorted().toList();
    }
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        Statement st = c.createStatement()) {
      for (Path f : fichiers) {
        st.execute(Files.readString(f));
      }
    }
  }

  /** Jeton OIDC simulé : {@code sub} = identifiant Keycloak, rôle = code du profil. */
  public static RequestPostProcessor jeton(String sub, String role) {
    return jwt().jwt(j -> j.subject(sub).claim("realm_access", Map.of("roles", List.of(role))));
  }
}
