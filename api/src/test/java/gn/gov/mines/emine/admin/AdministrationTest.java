package gn.gov.mines.emine.admin;

import static gn.gov.mines.emine.support.Personas.ADMIN_TECH;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR;
import static gn.gov.mines.emine.support.Personas.MINISTRE;
import static gn.gov.mines.emine.support.Personas.jeton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gn.gov.mines.emine.support.BaseDeDonneesDeTest;
import gn.gov.mines.emine.support.Personas;
import gn.gov.mines.emine.support.StockageEnMemoire;
import gn.gov.mines.emine.support.TestIntegration;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Espace de l'administrateur technique : vue d'ensemble (comptes, profils, disponibilité) et «
 * Utilisateurs & rôles », réservés au droit « paramétrer » ; date de connexion notée une fois par
 * connexion, d'après le jeton, et tracée au journal d'audit.
 */
@AutoConfigureMockMvc
@Import(StockageEnMemoire.Config.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdministrationTest extends TestIntegration {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired MockMvc mvc;

  @BeforeAll
  void donnees() throws Exception {
    Personas.charger();
  }

  @Test
  void laVueDEnsembleDonneComptesProfilsEtServices() throws Exception {
    JsonNode t = lire("/api/v1/administration/tableau-de-bord", jeton(ADMIN_TECH, "ADMIN_TECH"));
    assertThat(t.path("comptes").path("actifs").asLong()).isPositive();
    List<String> profils = new ArrayList<>();
    t.path("profils").forEach(p -> profils.add(p.path("code").asString()));
    assertThat(profils).hasSize(16).startsWith("ADMIN_TECH");
    List<String> services = new ArrayList<>();
    t.path("services").forEach(s -> services.add(s.path("cle").asString()));
    assertThat(services).containsExactly("base", "authentification", "stockage", "messagerie");
    // La base répond forcément (la lecture vient d'elle) ; le stockage est simulé en mémoire
    // (aucun service extérieur requis par le test).
    assertThat(t.path("services").get(0).path("disponible").asBoolean()).isTrue();
    assertThat(t.path("services").get(2).path("disponible").asBoolean()).isTrue();
  }

  @Test
  void seulLAdministrateurTechniqueYAccede() throws Exception {
    for (var j : List.of(jeton(INVESTISSEUR, "INVESTISSEUR"), jeton(MINISTRE, "MINISTRE"))) {
      mvc.perform(get("/api/v1/administration/tableau-de-bord").with(j))
          .andExpect(status().isForbidden());
      mvc.perform(get("/api/v1/administration/utilisateurs").with(j))
          .andExpect(status().isForbidden());
    }
  }

  @Test
  void lesUtilisateursSeFiltrentParProfilEtSeCherchentSansAccents() throws Exception {
    JsonNode investisseurs =
        lire(
            "/api/v1/administration/utilisateurs?profil=INVESTISSEUR&taille=100",
            jeton(ADMIN_TECH, "ADMIN_TECH"));
    assertThat(investisseurs.path("total").asLong()).isGreaterThanOrEqualTo(2);
    investisseurs
        .path("elements")
        .forEach(u -> assertThat(u.path("profilCode").asString()).isEqualTo("INVESTISSEUR"));
    JsonNode fode =
        JSON.readTree(
            mvc.perform(
                    get("/api/v1/administration/utilisateurs")
                        .param("q", "fode BANGOURA")
                        .with(jeton(ADMIN_TECH, "ADMIN_TECH")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(fode.path("total").asLong()).isEqualTo(1);
    assertThat(fode.path("elements").get(0).path("email").asString())
        .isEqualTo("f.bangoura@horizon-invest.test");
    mvc.perform(
            get("/api/v1/administration/utilisateurs?profil=INCONNU")
                .with(jeton(ADMIN_TECH, "ADMIN_TECH")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void laDateDeConnexionEstNoteeUneFoisEtTracee() throws Exception {
    Instant authentifie = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(60);
    var jetonConnecte =
        jwt()
            .jwt(
                j ->
                    j.subject(MINISTRE)
                        .claim("realm_access", Map.of("roles", List.of("MINISTRE")))
                        .claim("auth_time", authentifie.getEpochSecond()));
    for (int i = 0; i < 3; i++) {
      mvc.perform(get("/api/v1/moi").with(jetonConnecte)).andExpect(status().isOk());
    }
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        Statement st = c.createStatement();
        ResultSet r =
            st.executeQuery(
                "SELECT u.derniere_connexion, (SELECT count(*) FROM journal_audit a"
                    + " WHERE a.table_nom = 'utilisateur' AND a.objet_id = u.id::text"
                    + " AND a.operation = 'U'"
                    + " AND (a.nouvelles_valeurs->>'derniere_connexion')"
                    + " IS DISTINCT FROM (a.anciennes_valeurs->>'derniere_connexion')) AS traces"
                    + " FROM utilisateur u WHERE u.keycloak_id = '"
                    + MINISTRE
                    + "'")) {
      assertThat(r.next()).isTrue();
      assertThat(r.getTimestamp(1).toInstant()).isEqualTo(authentifie);
      assertThat(r.getLong(2)).as("une seule trace pour trois requêtes").isEqualTo(1);
    }
    JsonNode ministres =
        lire(
            "/api/v1/administration/utilisateurs?profil=MINISTRE", jeton(ADMIN_TECH, "ADMIN_TECH"));
    assertThat(ministres.path("elements").get(0).path("derniereConnexion").asString())
        .isEqualTo(authentifie.toString());
    // Chaque carte de la vue d'ensemble ouvre la liste qui donne le même nombre.
    JsonNode t = lire("/api/v1/administration/tableau-de-bord", jeton(ADMIN_TECH, "ADMIN_TECH"));
    assertThat(
            lire(
                    "/api/v1/administration/utilisateurs?recents=true",
                    jeton(ADMIN_TECH, "ADMIN_TECH"))
                .path("total")
                .asLong())
        .isEqualTo(t.path("comptes").path("connectes30Jours").asLong())
        .isPositive();
    assertThat(
            lire(
                    "/api/v1/administration/utilisateurs?statut=actif",
                    jeton(ADMIN_TECH, "ADMIN_TECH"))
                .path("total")
                .asLong())
        .isEqualTo(t.path("comptes").path("actifs").asLong());
  }

  private JsonNode lire(
      String adresse, org.springframework.test.web.servlet.request.RequestPostProcessor j)
      throws Exception {
    String corps =
        mvc.perform(get(adresse).with(j))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JSON.readTree(corps);
  }
}
