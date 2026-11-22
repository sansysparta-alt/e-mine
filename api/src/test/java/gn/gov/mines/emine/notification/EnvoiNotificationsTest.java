package gn.gov.mines.emine.notification;

import static gn.gov.mines.emine.support.Personas.INVESTISSEUR;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR_2;
import static org.assertj.core.api.Assertions.assertThat;

import gn.gov.mines.emine.notification.application.ServiceEnvoiNotifications;
import gn.gov.mines.emine.support.BaseDeDonneesDeTest;
import gn.gov.mines.emine.support.DemandesDeTest;
import gn.gov.mines.emine.support.PasserellesNotificationDeTest;
import gn.gov.mines.emine.support.Personas;
import gn.gov.mines.emine.support.TestIntegration;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * US-07.1 : la table {@code notification} sert d'outbox ; la tâche envoie SMS et e-mails par les
 * ports, retente un échec à 1, 5 puis 15 min, passe en « echec » après le 4e essai, et chaque
 * destinataire ne lit que ses propres notifications (RLS).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EnvoiNotificationsTest extends TestIntegration {

  @Autowired ServiceEnvoiNotifications service;
  @Autowired PasserellesNotificationDeTest passerelles;

  @BeforeAll
  void personas() throws Exception {
    Personas.charger();
  }

  @BeforeEach
  void viderBoite() throws Exception {
    // Les autres notifications en attente (écrites par d'autres tests) sont mises de côté.
    executer("UPDATE notification SET statut = 'envoyee' WHERE statut = 'en_attente'");
    passerelles.reinitialiser();
  }

  @Test
  void unSmsEtUnEmailEnAttenteSontEnvoyesParLesPortsDansLaLangueDuDestinataire() throws Exception {
    long demande = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    long sms = ecrire(INVESTISSEUR, "sms", demande);
    long email = ecrire(INVESTISSEUR, "email", demande);

    assertThat(service.envoyerDues()).isEqualTo(2);

    assertThat(statut(sms)).isEqualTo("envoyee");
    assertThat(statut(email)).isEqualTo("envoyee");
    assertThat(passerelles.sms)
        .singleElement()
        .satisfies(
            s -> {
              assertThat(s).startsWith("+224");
              assertThat(s).contains("complement demande pour DEM-");
            });
    assertThat(passerelles.courriels)
        .singleElement()
        .satisfies(
            c -> {
              assertThat(c).contains("Complément demandé · DEM-");
              assertThat(c).contains("/demandes/" + demande);
              assertThat(c).contains("Pièces attendues : EIES");
            });
    // Rien de plus : une notification envoyée ne repart jamais.
    assertThat(service.envoyerDues()).isZero();
  }

  @Test
  void unEchecEstRetenteA1Puis5Puis15MinutesPuisPasseEnEchecApresLe4eEssai() throws Exception {
    long demande = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    long id = ecrire(INVESTISSEUR, "email", demande);
    passerelles.enPanne = true;

    List<Duration> reports =
        List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15));
    for (int essai = 1; essai <= 3; essai++) {
      Instant avant = Instant.now();
      assertThat(service.envoyerDues()).isZero();
      assertThat(statut(id)).isEqualTo("en_attente");
      assertThat(tentatives(id)).isEqualTo(essai);
      assertThat(Duration.between(avant, prochainEssai(id)))
          .isBetween(reports.get(essai - 1).minusSeconds(5), reports.get(essai - 1).plusSeconds(5));
      // Pas d'essai avant l'heure prévue.
      assertThat(service.envoyerDues()).isZero();
      assertThat(tentatives(id)).isEqualTo(essai);
      executer("UPDATE notification SET prochain_essai_le = now() WHERE id = " + id);
    }
    assertThat(service.envoyerDues()).isZero();
    assertThat(statut(id)).isEqualTo("echec");
    assertThat(tentatives(id)).isEqualTo(4);
    assertThat(derniereErreur(id)).contains("Panne simulée");
  }

  @Test
  void unEssaiReussiApresUnEchecPasseLaNotificationEnvoyee() throws Exception {
    long demande = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    long id = ecrire(INVESTISSEUR, "sms", demande);
    passerelles.enPanne = true;
    service.envoyerDues();
    passerelles.enPanne = false;
    executer("UPDATE notification SET prochain_essai_le = now() WHERE id = " + id);

    assertThat(service.envoyerDues()).isEqualTo(1);
    assertThat(statut(id)).isEqualTo("envoyee");
    assertThat(tentatives(id)).isEqualTo(2);
  }

  @Test
  void lesNotificationsInternesNeSontPasEnvoyeesParSmsNiEmail() throws Exception {
    long demande = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    long id = ecrire(INVESTISSEUR, "interne", demande);

    assertThat(service.envoyerDues()).isZero();
    assertThat(tentatives(id)).isZero();
    assertThat(passerelles.sms).isEmpty();
    assertThat(passerelles.courriels).isEmpty();
  }

  @Test
  void unModeleInconnuNEnvoieRienEtFinitEnEchec() throws Exception {
    long demande = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    long id = ecrire(INVESTISSEUR, "email", demande);
    executer("UPDATE notification SET modele = 'MODELE_INCONNU' WHERE id = " + id);

    service.envoyerDues();
    assertThat(passerelles.courriels).isEmpty();
    assertThat(derniereErreur(id)).contains("MODELE_INCONNU");
  }

  @Test
  void chaqueDestinataireNeLitQueSesPropresNotifications() throws Exception {
    long demande = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    long id = ecrire(INVESTISSEUR, "interne", demande);

    assertThat(visible(INVESTISSEUR, id)).isTrue();
    assertThat(visible(INVESTISSEUR_2, id)).isFalse();
  }

  // --- Outils ---------------------------------------------------------------------------------

  /** Écrit une notification « complément demandé » comme le ferait le trigger métier. */
  private static long ecrire(String keycloakId, String canal, long demande) throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "INSERT INTO notification (destinataire_id, canal, modele, demande_id, contenu)"
                    + " SELECT u.id, ?::canal_notification, 'COMPLEMENT_DEMANDE', ?,"
                    + " jsonb_build_object('dateLimite', now() + interval '5 days',"
                    + " 'pieces', jsonb_build_array('EIES'))"
                    + " FROM utilisateur u WHERE u.keycloak_id = ?::uuid RETURNING id")) {
      ps.setString(1, canal);
      ps.setLong(2, demande);
      ps.setString(3, keycloakId);
      ResultSet rs = ps.executeQuery();
      rs.next();
      return rs.getLong(1);
    }
  }

  /** Lecture en tant qu'emine_app, avec le contexte de session d'un utilisateur (RLS). */
  private static boolean visible(String keycloakId, long id) throws SQLException {
    try (Connection c =
        DriverManager.getConnection(
            BaseDeDonneesDeTest.demarrer().getJdbcUrl(),
            "emine_app",
            BaseDeDonneesDeTest.MDP_APP)) {
      c.setAutoCommit(false);
      long utilisateur =
          valeur("SELECT id FROM utilisateur WHERE keycloak_id = '" + keycloakId + "'");
      try (PreparedStatement ps =
          c.prepareStatement("SELECT set_config('emine.user_id', ?, true)")) {
        ps.setString(1, Long.toString(utilisateur));
        ps.execute();
      }
      try (PreparedStatement ps =
          c.prepareStatement("SELECT count(*) FROM notification WHERE id = ?")) {
        ps.setLong(1, id);
        ResultSet rs = ps.executeQuery();
        rs.next();
        boolean vu = rs.getInt(1) == 1;
        c.rollback();
        return vu;
      }
    }
  }

  private static void executer(String sql) throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps = c.prepareStatement(sql)) {
      ps.execute();
    }
  }

  private static long valeur(String sql) throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps = c.prepareStatement(sql)) {
      ResultSet rs = ps.executeQuery();
      rs.next();
      return rs.getLong(1);
    }
  }

  private static String texte(String sql) throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps = c.prepareStatement(sql)) {
      ResultSet rs = ps.executeQuery();
      rs.next();
      return rs.getString(1);
    }
  }

  private static String statut(long id) throws SQLException {
    return texte("SELECT statut::text FROM notification WHERE id = " + id);
  }

  private static long tentatives(long id) throws SQLException {
    return valeur("SELECT tentatives FROM notification WHERE id = " + id);
  }

  private static String derniereErreur(long id) throws SQLException {
    return texte("SELECT derniere_erreur FROM notification WHERE id = " + id);
  }

  private static Instant prochainEssai(long id) throws SQLException {
    return Instant.parse(
        texte(
            "SELECT to_char(prochain_essai_le AT TIME ZONE 'UTC',"
                + " 'YYYY-MM-DD\"T\"HH24:MI:SS.US\"Z\"') FROM notification WHERE id = "
                + id));
  }
}
