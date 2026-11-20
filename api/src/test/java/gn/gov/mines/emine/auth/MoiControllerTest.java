package gn.gov.mines.emine.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gn.gov.mines.emine.support.BaseDeDonneesDeTest;
import gn.gov.mines.emine.support.TestIntegration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** US-01.2 : l'API reconnaît l'utilisateur à partir du jeton OIDC transmis par le BFF. */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MoiControllerTest extends TestIntegration {

  static final String INVESTISSEUR = "00000000-0000-4000-8000-000000000009";
  static final String AGENT_GUICHET = "00000000-0000-4000-8000-000000000002";

  @Autowired MockMvc mvc;

  @BeforeAll
  void personas() throws Exception {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        Statement st = c.createStatement()) {
      st.execute(Files.readString(Path.of("..", "db", "seed", "01-personas.sql")));
    }
  }

  static RequestPostProcessor jeton(String sub, String role) {
    return jwt().jwt(j -> j.subject(sub).claim("realm_access", Map.of("roles", List.of(role))));
  }

  @Test
  void sansJetonLaRequeteEstRefuseeAvecUnMessageClair() throws Exception {
    mvc.perform(get("/api/v1/moi"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH-001"))
        .andExpect(
            jsonPath("$.message").value("Vous devez vous connecter pour accéder à ce service."));
  }

  @Test
  void lInvestisseurConnecteVoitSonNomEtSonProfil() throws Exception {
    mvc.perform(get("/api/v1/moi").with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.prenom").value("Fodé"))
        .andExpect(jsonPath("$.nom").value("Bangoura"))
        .andExpect(jsonPath("$.profil.code").value("INVESTISSEUR"))
        .andExpect(jsonPath("$.organisation").value("Horizon Invest SARL (fictive)"))
        .andExpect(jsonPath("$.profil.libelle").value("Investisseur Extérieur"))
        .andExpect(jsonPath("$.interne").value(false));
  }

  @Test
  void lAgentDuGuichetEstReconnuCommeInterne() throws Exception {
    mvc.perform(get("/api/v1/moi").with(jeton(AGENT_GUICHET, "GUICHET")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.prenom").value("Aïcha"))
        .andExpect(jsonPath("$.profil.code").value("GUICHET"))
        .andExpect(jsonPath("$.interne").value(true));
  }

  @Test
  void unCompteKeycloakNonRattacheEstRefuse() throws Exception {
    mvc.perform(get("/api/v1/moi").with(jeton(UUID.randomUUID().toString(), "INVESTISSEUR")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("AUTH-003"));
  }

  @Test
  void lesRoutesHorsApiSontFermees() throws Exception {
    mvc.perform(get("/interne/quelque-chose").with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PERM-001"));
  }

  @Test
  void laSanteResteAccessibleSansJeton() throws Exception {
    mvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }
}
