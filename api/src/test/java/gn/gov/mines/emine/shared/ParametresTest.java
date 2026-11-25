package gn.gov.mines.emine.shared;

import static gn.gov.mines.emine.support.Personas.ADMIN_TECH;
import static gn.gov.mines.emine.support.Personas.GUICHET;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR;
import static gn.gov.mines.emine.support.Personas.MINISTRE;
import static gn.gov.mines.emine.support.Personas.jeton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import java.time.LocalDate;
import java.time.ZoneId;
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
 * US-02.6, US-02.7 et US-02.8 (V21, V27) : la règle des délais est proposée par l'administrateur,
 * validée par le Ministre et appliquée à sa date d'effet ; une demande garde la règle de son dépôt.
 * Les canaux de chaque notification sont réglés par l'administrateur, immédiatement ; un événement
 * garde au moins un canal. Tout est tracé. La règle en vigueur reste celle par défaut à la fin (les
 * autres tests en dépendent).
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
  final RequestPostProcessor ministre = jeton(MINISTRE, "MINISTRE");
  final LocalDate aujourdhui = LocalDate.now(ZoneId.of("Africa/Conakry"));

  @BeforeAll
  void donnees() throws Exception {
    Personas.charger();
  }

  @AfterAll
  void restaurer() throws Exception {
    canaux(admin, "DEMANDE_DEPOSEE", false, true, true);
  }

  @Test
  void laRegleDesDelaisEstProposeeValideeEtAppliqueeAuxSeulesNouvellesDemandes() throws Exception {
    long avant = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    int initiale = regleDuDepot(avant);
    mvc.perform(get(DELAIS).with(ministre))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enVigueur.depart").value("paiement_frais"))
        .andExpect(jsonPath("$.enVigueur.seuilProchePourcent").value(20))
        .andExpect(jsonPath("$.peutProposer").value(false))
        .andExpect(jsonPath("$.peutDecider").value(true));

    // 1. Proposition : rien ne change avant la décision du Ministre.
    int v = numero(proposer(admin, "paiement_frais", true, false, 25, 3, aujourdhui));
    mvc.perform(get(DELAIS).with(admin))
        .andExpect(jsonPath("$.enVigueur.seuilProchePourcent").value(20))
        .andExpect(jsonPath("$.versions[0].etat").value("a_valider"))
        .andExpect(jsonPath("$.versions[0].proposeePar").isNotEmpty())
        .andExpect(jsonPath("$.peutProposer").value(false));
    proposer(admin, "depot", true, true, 20, 2, aujourdhui)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DOC-008"));
    decider(admin, v, "approbation").andExpect(status().isForbidden());
    assertThat(dernierAudit("version_regle_delai")).isEqualTo("ADMIN_TECH");

    // 2. Validation : en vigueur pour les demandes déposées ensuite, pas pour les autres.
    decider(ministre, v, "approbation")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dateEffet").value(aujourdhui.toString()));
    assertThat(dernierAudit("version_regle_delai")).isEqualTo("MINISTRE");
    mvc.perform(get(DELAIS).with(admin))
        .andExpect(jsonPath("$.enVigueur.pauseDroits").value(false))
        .andExpect(jsonPath("$.enVigueur.seuilProchePourcent").value(25))
        .andExpect(jsonPath("$.versions[0].etat").value("en_vigueur"))
        .andExpect(jsonPath("$.versions[1].etat").value("remplacee"));
    long apres = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    assertThat(regleDuDepot(avant)).isEqualTo(initiale);
    assertThat(regleDuDepot(apres)).isEqualTo(v);

    // 3. Retour à la règle par défaut (pour les autres tests), par le même circuit.
    int retour = numero(proposer(admin, "paiement_frais", true, true, 20, 2, aujourdhui));
    decider(ministre, retour, "approbation").andExpect(status().isOk());

    // 4. Refus motivé ; version validée pour une date à venir : programmée, sans effet aujourd'hui.
    int refusee = numero(proposer(admin, "depot", true, true, 20, 2, aujourdhui));
    decider(ministre, refusee, "refus", "Le départ au dépôt pénaliserait le Ministère.")
        .andExpect(status().isNoContent());
    LocalDate plusTard = aujourdhui.plusYears(10);
    int programmee = numero(proposer(admin, "paiement_frais", true, true, 30, 2, plusTard));
    decider(ministre, programmee, "approbation")
        .andExpect(jsonPath("$.dateEffet").value(plusTard.toString()));
    mvc.perform(get(DELAIS).with(admin))
        .andExpect(jsonPath("$.enVigueur.seuilProchePourcent").value(20))
        .andExpect(jsonPath("$.enVigueur.pauseDroits").value(true))
        .andExpect(jsonPath("$.versions[0].etat").value("programmee"))
        .andExpect(jsonPath("$.versions[1].etat").value("refusee"))
        .andExpect(jsonPath("$.versions[1].motifRefus").isNotEmpty());
  }

  @Test
  void unePropositionInvalideEstRefusee() throws Exception {
    proposer(ministre, "depot", true, true, 20, 2, aujourdhui).andExpect(status().isForbidden());
    proposer(admin, "demain", true, true, 20, 2, aujourdhui)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VAL-001"));
    proposer(admin, "depot", true, true, 0, 2, aujourdhui).andExpect(status().isBadRequest());
    proposer(admin, "depot", true, true, 20, 2, aujourdhui.minusDays(1))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VAL-001"));
    mvc.perform(get(DELAIS).with(jeton(GUICHET, "GUICHET"))).andExpect(status().isForbidden());
  }

  @Test
  void unCanalCoupeNEnvoiePlusRienParCeCanal() throws Exception {
    canaux(admin, "DEMANDE_DEPOSEE", false, false, true)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.modele == 'DEMANDE_DEPOSEE')].email").value(false));
    long demande = DemandesDeTest.brouillon(INVESTISSEUR, "PEX");
    DemandesDeTest.changerStatut(demande, "deposee");
    assertThat(canauxEnvoyes(demande, "DEMANDE_DEPOSEE")).containsExactly("interne");
    assertThat(dernierAudit("notification_evenement")).isEqualTo("ADMIN_TECH");
  }

  @Test
  void unEvenementGardeAuMoinsUnCanal() throws Exception {
    canaux(admin, "DEMANDE_REJETEE", false, false, false)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VAL-001"));
    // L'espace en ligne est obligatoire : l'usager y voit toujours où en est son dossier.
    canaux(admin, "DEMANDE_REJETEE", true, true, false)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VAL-001"));
    canaux(admin, "INCONNU", true, true, true).andExpect(status().isNotFound());
    canaux(jeton(MINISTRE, "MINISTRE"), "DEMANDE_REJETEE", true, true, false)
        .andExpect(status().isForbidden());
  }

  @Test
  void laBaseRefuseUneModificationSansLeDroitParametrer() {
    // L'application n'écrit la règle que par les fonctions de la base (contrôle indépendant).
    assertThatThrownBy(
            () -> jdbc.update("UPDATE version_regle_delai SET seuil_proche_pourcent = 50"))
        .rootCause()
        .hasMessageContaining("permission denied");
    assertThatThrownBy(() -> jdbc.update("UPDATE parametre_delai SET seuil_proche_pourcent = 50"))
        .rootCause()
        .hasMessageContaining("permission denied");
  }

  private ResultActions proposer(
      RequestPostProcessor qui,
      String depart,
      boolean complement,
      boolean droits,
      int pct,
      int min,
      LocalDate effet)
      throws Exception {
    return mvc.perform(
        post(DELAIS + "/versions")
            .with(qui)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                json.writeValueAsString(
                    Map.of(
                        "depart", depart,
                        "pauseComplement", complement,
                        "pauseDroits", droits,
                        "seuilProchePourcent", pct,
                        "seuilProcheMinJours", min,
                        "dateEffet", effet.toString(),
                        "motif", "Ajustement de la règle (test)"))));
  }

  private ResultActions decider(RequestPostProcessor qui, int version, String decision)
      throws Exception {
    return mvc.perform(post(DELAIS + "/versions/" + version + "/" + decision).with(qui));
  }

  private ResultActions decider(
      RequestPostProcessor qui, int version, String decision, String motif) throws Exception {
    return mvc.perform(
        post(DELAIS + "/versions/" + version + "/" + decision)
            .with(qui)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("motif", motif))));
  }

  private int numero(ResultActions r) throws Exception {
    return json.readTree(
            r.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
        .get("version")
        .asInt();
  }

  private static int regleDuDepot(long demande) throws Exception {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement("SELECT regle_delai_version FROM demande WHERE id = ?")) {
      ps.setLong(1, demande);
      ResultSet rs = ps.executeQuery();
      rs.next();
      return rs.getInt(1);
    }
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
