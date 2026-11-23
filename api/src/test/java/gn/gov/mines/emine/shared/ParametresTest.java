package gn.gov.mines.emine.shared;

import static gn.gov.mines.emine.support.Personas.ADMIN_TECH;
import static gn.gov.mines.emine.support.Personas.GUICHET;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR;
import static gn.gov.mines.emine.support.Personas.MINISTRE;
import static gn.gov.mines.emine.support.Personas.jeton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gn.gov.mines.emine.support.BaseDeDonneesDeTest;
import gn.gov.mines.emine.support.DemandesDeTest;
import gn.gov.mines.emine.support.Personas;
import gn.gov.mines.emine.support.TestIntegration;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

/**
 * US-02.6 et US-02.7 (V21) : la règle des délais et les canaux de chaque notification sont réglés
 * par l'administrateur, immédiatement et avec une trace ; le Ministre les consulte ; un événement
 * garde au moins un canal. Paramètres restaurés à la fin (les autres tests en dépendent).
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ParametresTest extends TestIntegration {

  private static final String DELAIS = "/api/v1/parametres/delais";
  private static final String CANAUX = "/api/v1/parametres/notifications";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  final JsonMapper json = JsonMapper.builder().build();
  final RequestPostProcessor admin = jeton(ADMIN_TECH, "ADMIN_TECH");

  @BeforeAll
  void donnees() throws Exception {
    Personas.charger();
  }

  @AfterAll
  void restaurer() throws Exception {
    delais(admin, "paiement_frais", true, true, 20, 2);
    canaux(admin, "DEMANDE_DEPOSEE", true, true, true);
  }

  @Test
  void laRegleDesDelaisEstModifieeParLAdministrateurEtTracee() throws Exception {
    mvc.perform(get(DELAIS).with(jeton(MINISTRE, "MINISTRE")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.depart").value("paiement_frais"))
        .andExpect(jsonPath("$.seuilProchePourcent").value(20));
    delais(admin, "paiement_frais", true, false, 25, 3)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pauseDroits").value(false))
        .andExpect(jsonPath("$.seuilProchePourcent").value(25));
    assertThat(dernierAudit("parametre_delai")).isEqualTo("ADMIN_TECH");
    delais(jeton(MINISTRE, "MINISTRE"), "depot", true, true, 20, 2)
        .andExpect(status().isForbidden());
    delais(admin, "demain", true, true, 20, 2)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VAL-001"));
    delais(admin, "depot", true, true, 0, 2).andExpect(status().isBadRequest());
    mvc.perform(get(DELAIS).with(jeton(GUICHET, "GUICHET"))).andExpect(status().isForbidden());
  }

  @Test
  void unCanalCoupeNEnvoiePlusRienParCeCanal() throws Exception {
    canaux(admin, "DEMANDE_DEPOSEE", false, true, true)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.modele == 'DEMANDE_DEPOSEE')].sms").value(false));
    long demande = DemandesDeTest.brouillon(INVESTISSEUR, "PEX");
    DemandesDeTest.changerStatut(demande, "deposee");
    assertThat(canauxEnvoyes(demande, "DEMANDE_DEPOSEE"))
        .containsExactlyInAnyOrder("email", "interne");
    assertThat(dernierAudit("notification_evenement")).isEqualTo("ADMIN_TECH");
  }

  @Test
  void unEvenementGardeAuMoinsUnCanal() throws Exception {
    canaux(admin, "DEMANDE_REJETEE", false, false, false)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VAL-001"));
    canaux(admin, "INCONNU", true, true, true).andExpect(status().isNotFound());
    canaux(jeton(MINISTRE, "MINISTRE"), "DEMANDE_REJETEE", true, true, false)
        .andExpect(status().isForbidden());
  }

  @Test
  void laBaseRefuseUneModificationSansLeDroitParametrer() {
    // Hors session d'administrateur, la base refuse (contrôle indépendant de l'API).
    assertThatThrownBy(() -> jdbc.update("UPDATE parametre_delai SET seuil_proche_pourcent = 50"))
        .rootCause()
        .hasMessageContaining("Seul l'administrateur");
  }

  private ResultActions delais(
      RequestPostProcessor qui, String depart, boolean complement, boolean droits, int pct, int min)
      throws Exception {
    return mvc.perform(
        put(DELAIS)
            .with(qui)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                json.writeValueAsString(
                    Map.of(
                        "depart", depart,
                        "pauseComplement", complement,
                        "pauseDroits", droits,
                        "seuilProchePourcent", pct,
                        "seuilProcheMinJours", min))));
  }

  private ResultActions canaux(
      RequestPostProcessor qui, String modele, boolean sms, boolean email, boolean interne)
      throws Exception {
    return mvc.perform(
        put(CANAUX)
            .with(qui)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                json.writeValueAsString(
                    List.of(
                        Map.of(
                            "modele", modele, "sms", sms, "email", email, "interne", interne)))));
  }

  private static List<String> canauxEnvoyes(long demande, String modele) throws Exception {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "SELECT canal::text FROM notification WHERE demande_id = ? AND modele = ?")) {
      ps.setLong(1, demande);
      ps.setString(2, modele);
      ResultSet rs = ps.executeQuery();
      List<String> l = new java.util.ArrayList<>();
      while (rs.next()) {
        l.add(rs.getString(1));
      }
      return l;
    }
  }

  private static String dernierAudit(String table) throws Exception {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "SELECT p.code FROM journal_audit j JOIN utilisateur u ON u.id = j.utilisateur_id"
                    + " JOIN profil p ON p.id = u.profil_id WHERE j.table_nom = ?"
                    + " ORDER BY j.id DESC LIMIT 1")) {
      ps.setString(1, table);
      ResultSet rs = ps.executeQuery();
      return rs.next() ? rs.getString(1) : null;
    }
  }
}
