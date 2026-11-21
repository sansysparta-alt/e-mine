package gn.gov.mines.emine.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gn.gov.mines.emine.support.BaseDeDonneesDeTest;
import gn.gov.mines.emine.support.TestIntegration;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Year;
import java.util.Arrays;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** US-00.2 : toutes les migrations passent sur une base vide amorcée par db/bootstrap. */
class MigrationsTest extends TestIntegration {

  @Autowired Flyway flyway;
  @Autowired JdbcTemplate jdbc;

  @Test
  void lesMigrationsSontAppliquees() {
    List<String> versions =
        Arrays.stream(flyway.info().applied())
            .filter(MigrationInfo::isVersioned)
            .map(m -> m.getVersion().getVersion())
            .toList();
    assertThat(versions).containsSequence("1", "2", "3", "4", "5");
    assertThat(flyway.info().pending()).isEmpty();
  }

  @Test
  void lesReferentielsSontCharges() {
    assertThat(compter("profil")).isEqualTo(16);
    assertThat(compter("type_document")).isEqualTo(15);
    assertThat(compter("permission")).isEqualTo(10);
    assertThat(compter("transition_statut")).isPositive();
    assertThat(compter("tarif")).isPositive();
  }

  @Test
  void aucuneDonneeFictiveDansLesMigrations() throws SQLException {
    // La base est partagée par toute la suite : on ne compte que les lignes écrites par Flyway.
    String parLesMigrations =
        " WHERE created_at <= (SELECT max(installed_on) FROM flyway_schema_history)";
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        Statement st = c.createStatement()) {
      for (String table : List.of("organisation", "utilisateur", "titre", "demande", "paiement")) {
        ResultSet rs = st.executeQuery("SELECT count(*) FROM " + table + parLesMigrations);
        rs.next();
        assertThat(rs.getInt(1)).as(table).isZero();
      }
      ResultSet rs =
          st.executeQuery("SELECT count(*) FROM compteur WHERE cle NOT IN ('DEM', 'PAY', 'REC')");
      rs.next();
      assertThat(rs.getInt(1)).as("compteurs amorcés").isZero();
    }
  }

  @Test
  void lApplicationSeConnecteEnEmineAppNonProprietaire() {
    assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo("emine_app");
    assertThat(
            jdbc.queryForObject(
                "SELECT tableowner FROM pg_tables WHERE tablename = 'demande'", String.class))
        .isEqualTo("emine_owner");
  }

  @Test
  void v2NumeroDeDemandeSurSixChiffres() throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        Statement st = c.createStatement()) {
      c.setAutoCommit(false);
      long utilisateur = creerUtilisateur(st, "v2.numero@exemple.test");
      ResultSet rs =
          st.executeQuery(
              "INSERT INTO demande (type_document_id, demandeur_id, statut) SELECT id, "
                  + utilisateur
                  + ", 'deposee' FROM type_document WHERE code = 'PEX' RETURNING numero");
      rs.next();
      assertThat(rs.getString(1)).matches("DEM-" + Year.now().getValue() + "-\\d{6}");
      c.rollback();
    }
  }

  @Test
  void v5UnBrouillonNAPasDeNumeroLeDepotEnAttribueUn() throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        Statement st = c.createStatement()) {
      c.setAutoCommit(false);
      long utilisateur = creerUtilisateur(st, "v5.numero@exemple.test");
      ResultSet rs =
          st.executeQuery(
              "INSERT INTO demande (type_document_id, demandeur_id) SELECT id, "
                  + utilisateur
                  + " FROM type_document WHERE code = 'PEX' RETURNING id, numero");
      rs.next();
      long id = rs.getLong(1);
      assertThat(rs.getString(2)).isNull();
      rs =
          st.executeQuery(
              "UPDATE demande SET statut = 'deposee' WHERE id = "
                  + id
                  + " RETURNING numero, date_depot IS NOT NULL");
      rs.next();
      assertThat(rs.getString(1)).matches("DEM-" + Year.now().getValue() + "-\\d{6}");
      assertThat(rs.getBoolean(2)).isTrue();
      c.rollback();
    }
  }

  @Test
  void v2UnNumeroSurTroisChiffresEstRefuse() throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        Statement st = c.createStatement()) {
      c.setAutoCommit(false);
      long utilisateur = creerUtilisateur(st, "v2.refus@exemple.test");
      assertThatThrownBy(
              () ->
                  st.execute(
                      "INSERT INTO demande (numero, type_document_id, demandeur_id)"
                          + " SELECT 'DEM-2026-001', id, "
                          + utilisateur
                          + " FROM type_document WHERE code = 'PEX'"))
          .hasMessageContaining("demande_numero_check");
      c.rollback();
    }
  }

  @Test
  void v3UtilisateurLieAKeycloakSansMotDePasse() throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        Statement st = c.createStatement()) {
      c.setAutoCommit(false);
      long id = creerUtilisateur(st, "v3.keycloak@exemple.test");
      ResultSet rs =
          st.executeQuery(
              "SELECT keycloak_id IS NOT NULL, mot_de_passe_hash IS NULL FROM utilisateur"
                  + " WHERE id = "
                  + id);
      rs.next();
      assertThat(rs.getBoolean(1)).isTrue();
      assertThat(rs.getBoolean(2)).isTrue();
      c.rollback();
    }
  }

  private long creerUtilisateur(Statement st, String email) throws SQLException {
    ResultSet rs =
        st.executeQuery(
            "INSERT INTO utilisateur (profil_id, nom, prenom, email, telephone, keycloak_id)"
                + " SELECT id, 'Test', 'Migration', '"
                + email
                + "', '+224600000000', gen_random_uuid() FROM profil"
                + " WHERE code = 'INVESTISSEUR' RETURNING id");
    rs.next();
    return rs.getLong(1);
  }

  private int compter(String table) {
    return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
  }
}
