package gn.gov.mines.emine.notification;

import static gn.gov.mines.emine.support.Personas.DIRECTEUR;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR_2;
import static gn.gov.mines.emine.support.Personas.jeton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gn.gov.mines.emine.notification.application.ServiceEnvoiNotifications;
import gn.gov.mines.emine.support.BaseDeDonneesDeTest;
import gn.gov.mines.emine.support.DemandesDeTest;
import gn.gov.mines.emine.support.PasserellesNotificationDeTest;
import gn.gov.mines.emine.support.Personas;
import gn.gov.mines.emine.support.TestIntegration;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * US-07.2 : le demandeur est notifié à chaque changement d'étape et pour chaque événement de
 * paiement, par les canaux paramétrés de l'événement (SMS, e-mail, application), dans sa langue ;
 * il consulte ses notifications dans l'application et les marque comme lues.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EvenementsNotificationsTest extends TestIntegration {

  @Autowired MockMvc mvc;
  @Autowired ServiceEnvoiNotifications envoi;
  @Autowired PasserellesNotificationDeTest passerelles;

  @BeforeAll
  void personas() throws Exception {
    Personas.charger();
  }

  @BeforeEach
  void viderBoite() throws Exception {
    executer("UPDATE notification SET statut = 'envoyee' WHERE statut = 'en_attente'");
    executer("UPDATE notification_evenement SET sms = true, email = true, interne = true");
    passerelles.reinitialiser();
  }

  @Test
  void chaqueChangementDEtapeNotifieLeDemandeurParSmsEmailEtApplication() throws Exception {
    long id = DemandesDeTest.brouillon(INVESTISSEUR, "PEX");
    DemandesDeTest.changerStatut(id, "deposee");
    payer(id, "FRAIS_DOSSIER", 5_000_000);
    DemandesDeTest.changerStatut(id, "recevable");
    DemandesDeTest.avisRendus(id);
    DemandesDeTest.changerStatut(id, "decision");
    payer(id, "DROITS", 15_000_000);
    DemandesDeTest.changerStatut(id, "delivree");

    assertThat(modeles(id))
        .containsExactly(
            "DEMANDE_DEPOSEE",
            "PAIEMENT_CONFIRME",
            "DEMANDE_RECEVABLE",
            "DECISION_DROITS",
            "PAIEMENT_CONFIRME",
            "DOCUMENT_DELIVRE");
    for (String canal : List.of("sms", "email", "interne")) {
      assertThat(compter(id, canal)).as(canal).isEqualTo(6);
    }
    // Toutes se rédigent avec le contenu réel écrit par la base, et partent.
    assertThat(envoi.envoyerDues()).isEqualTo(12);
    assertThat(erreurs(id)).isZero();
    assertThat(passerelles.courriels)
        .anySatisfy(c -> assertThat(c).contains("droits de 15 000 000 GNF").doesNotContain("{"))
        .anySatisfy(c -> assertThat(c).contains("Permis d'exploitation"));
  }

  @Test
  void unDocumentSansDroitsAnnonceLaDecisionFavorableEtUnRejetDonneLeMotif() throws Exception {
    long attestation = DemandesDeTest.brouillon(INVESTISSEUR, "EXC");
    DemandesDeTest.changerStatut(attestation, "deposee");
    payer(attestation, "ATTESTATION", 200_000);
    DemandesDeTest.changerStatut(attestation, "recevable");
    DemandesDeTest.avisRendus(attestation);
    DemandesDeTest.changerStatut(attestation, "decision", "droits_payes");
    assertThat(modeles(attestation))
        .containsExactly(
            "DEMANDE_DEPOSEE", "PAIEMENT_CONFIRME", "DEMANDE_RECEVABLE", "DECISION_FAVORABLE");

    long rejetee = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    executer(
        "UPDATE demande SET statut = 'rejetee', motif_rejet = 'Périmètre en chevauchement.'"
            + " WHERE id = "
            + rejetee);
    assertThat(modeles(rejetee)).containsExactly("DEMANDE_REJETEE");
    envoi.envoyerDues();
    assertThat(passerelles.courriels)
        .anySatisfy(c -> assertThat(c).contains("Motif : Périmètre en chevauchement."));
  }

  @Test
  void lesCanauxDeChaqueEvenementSontParametrables() throws Exception {
    executer("UPDATE notification_evenement SET sms = false WHERE modele = 'DEMANDE_DEPOSEE'");
    long id = DemandesDeTest.brouillon(INVESTISSEUR, "PEX");
    DemandesDeTest.changerStatut(id, "deposee");

    assertThat(compter(id, "sms")).isZero();
    assertThat(compter(id, "email")).isEqualTo(1);
    assertThat(compter(id, "interne")).isEqualTo(1);
  }

  @Test
  void lesEvenementsDePaiementAVerifierRembourseEtReferenceProcheDeLExpirationSontNotifies()
      throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    String virement = paiementVirement(id, "now() + interval '1 day'");
    DemandesDeTest.changerStatutPaiement(virement, "en_attente");

    // J-2 : une seule alerte par référence, même si la tâche repasse.
    assertThat(envoi.alerterReferencesExpirantes()).isEqualTo(1);
    assertThat(envoi.alerterReferencesExpirantes()).isZero();

    DemandesDeTest.changerStatutPaiement(virement, "a_verifier");
    long autre = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    String paye = paiementVirement(autre, "now() + interval '7 days'");
    DemandesDeTest.changerStatutPaiement(
        paye, "en_attente", "paye", "remboursement_demande", "rembourse");

    assertThat(modeles(id)).containsExactly("REFERENCE_EXPIRE_BIENTOT", "PAIEMENT_A_VERIFIER");
    assertThat(modeles(autre)).containsExactly("PAIEMENT_CONFIRME", "REMBOURSEMENT");
    envoi.envoyerDues();
    assertThat(erreurs(id) + erreurs(autre)).isZero();
  }

  @Test
  void uneReferenceTresorPayDeQuinzeMinutesNEstJamaisConcerneeParLAlerteJ2() throws Exception {
    long id = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    executer(
        "INSERT INTO paiement (demande_id, objet, moyen, montant_gnf, payeur_id)"
            + " SELECT "
            + id
            + ", 'FRAIS_DOSSIER', 'TRESORPAY', 5000000, demandeur_id FROM demande WHERE id = "
            + id);
    envoi.alerterReferencesExpirantes();
    assertThat(modeles(id)).isEmpty();
  }

  @Test
  void chaqueModeleRedigeAUnEvenementParametreEtInversement() throws Exception {
    Properties p = new Properties();
    try (Reader r =
        new InputStreamReader(
            getClass().getClassLoader().getResourceAsStream("messages/notifications.properties"),
            StandardCharsets.UTF_8)) {
      p.load(r);
    }
    Set<String> modeles = new TreeSet<>();
    p.stringPropertyNames().stream()
        .filter(c -> !c.startsWith("commun."))
        .forEach(c -> modeles.add(c.substring(0, c.indexOf('.'))));
    assertThat(modeles).isEqualTo(evenements());
  }

  @Test
  void leDemandeurConsulteSesNotificationsDansSaLangueEtLesMarqueCommeLues() throws Exception {
    long id = DemandesDeTest.brouillon(INVESTISSEUR, "PEX");
    DemandesDeTest.changerStatut(id, "deposee");
    long notification = derniere(id);

    String non =
        mvc.perform(get("/api/v1/notifications/non-lues").with(investisseur()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(non).contains("\"nombre\"");

    mvc.perform(get("/api/v1/notifications").param("taille", "10").with(investisseur()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.elements[0].id").value(notification))
        .andExpect(jsonPath("$.elements[0].modele").value("DEMANDE_DEPOSEE"))
        .andExpect(
            jsonPath("$.elements[0].titre")
                .value(org.hamcrest.Matchers.startsWith("Demande déposée · DEM-")))
        .andExpect(
            jsonPath("$.elements[0].texte")
                .value(org.hamcrest.Matchers.containsString("Prochaine étape")))
        .andExpect(jsonPath("$.elements[0].demandeId").value(id))
        .andExpect(jsonPath("$.elements[0].lue").value(false));

    mvc.perform(get("/api/v1/notifications").header("Accept-Language", "en").with(investisseur()))
        .andExpect(
            jsonPath("$.elements[0].titre")
                .value(org.hamcrest.Matchers.startsWith("Request submitted · DEM-")));

    // Un autre usager ne la voit pas et ne peut pas la marquer.
    mvc.perform(get("/api/v1/notifications").with(jeton(INVESTISSEUR_2, "INVESTISSEUR")))
        .andExpect(jsonPath("$.elements[?(@.id == " + notification + ")]").isEmpty());
    mvc.perform(
            post("/api/v1/notifications/" + notification + "/lue")
                .with(jeton(INVESTISSEUR_2, "INVESTISSEUR")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SYS-002"));
    // Ni un agent : les notifications sont personnelles.
    mvc.perform(get("/api/v1/notifications").with(jeton(DIRECTEUR, "DIRECTEUR")))
        .andExpect(jsonPath("$.elements[?(@.id == " + notification + ")]").isEmpty());

    mvc.perform(post("/api/v1/notifications/" + notification + "/lue").with(investisseur()))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/notifications").param("taille", "10").with(investisseur()))
        .andExpect(jsonPath("$.elements[0].lue").value(true));

    mvc.perform(post("/api/v1/notifications/lues").with(investisseur()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nombre").value(0));
  }

  @Test
  void lesDroitsAPayerAnnoncentLeMontantEtLActionTantQuElleEstAttendue() throws Exception {
    long id = DemandesDeTest.brouillon(INVESTISSEUR, "PEX");
    DemandesDeTest.changerStatut(id, "deposee");
    payer(id, "FRAIS_DOSSIER", 5_000_000);
    DemandesDeTest.changerStatut(id, "recevable");
    DemandesDeTest.avisRendus(id);
    DemandesDeTest.changerStatut(id, "decision");
    String droits = "$.elements[?(@.demandeId == " + id + " && @.modele == 'DECISION_DROITS')]";
    mvc.perform(get("/api/v1/notifications").param("taille", "50").with(investisseur()))
        .andExpect(jsonPath(droits + ".montantGnf").value(15_000_000))
        .andExpect(jsonPath(droits + ".action").value("payer-droits"));
    payer(id, "DROITS", 15_000_000);
    // Une fois payés, la notification redevient un simple historique.
    mvc.perform(get("/api/v1/notifications").param("taille", "50").with(investisseur()))
        .andExpect(
            jsonPath(droits + ".action").value(org.hamcrest.Matchers.contains((Object) null)));
  }

  @Test
  void leFiltreNonLuesNeMontreQueLesNonLuesEtLesDeuxCompteursSontDonnes() throws Exception {
    long id = DemandesDeTest.brouillon(INVESTISSEUR, "PEX");
    DemandesDeTest.changerStatut(id, "deposee");
    long notification = derniere(id);
    mvc.perform(post("/api/v1/notifications/" + notification + "/lue").with(investisseur()))
        .andExpect(status().isNoContent());
    String page =
        mvc.perform(
                get("/api/v1/notifications")
                    .param("nonLues", "true")
                    .param("taille", "100")
                    .with(investisseur()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.elements[?(@.id == " + notification + ")]").isEmpty())
            .andExpect(jsonPath("$.elements[?(@.lue == true)]").isEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
    var json = tools.jackson.databind.json.JsonMapper.builder().build().readTree(page);
    assertThat(json.path("total").asLong()).isEqualTo(json.path("nonLues").asLong());
    assertThat(json.path("toutes").asLong()).isGreaterThan(json.path("nonLues").asLong());
  }

  // --- Outils ---------------------------------------------------------------------------------

  private static org.springframework.test.web.servlet.request.RequestPostProcessor investisseur() {
    return jeton(INVESTISSEUR, "INVESTISSEUR");
  }

  /** Paiement payé par le demandeur (les triggers notifient le payeur). */
  private static void payer(long demande, String objet, long montant) throws SQLException {
    String reference;
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "INSERT INTO paiement (demande_id, objet, moyen, montant_gnf, payeur_id)"
                    + " SELECT ?, ?::objet_paiement, 'TRESORPAY', ?, demandeur_id"
                    + " FROM demande WHERE id = ? RETURNING reference")) {
      ps.setLong(1, demande);
      ps.setString(2, objet);
      ps.setLong(3, montant);
      ps.setLong(4, demande);
      ResultSet rs = ps.executeQuery();
      rs.next();
      reference = rs.getString(1);
    }
    DemandesDeTest.changerStatutPaiement(reference, "en_attente", "paye");
  }

  /** Virement de frais dont la référence expire à l'instant donné ; renvoie sa référence. */
  private static String paiementVirement(long demande, String expiration) throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "INSERT INTO paiement (demande_id, objet, moyen, montant_gnf, payeur_id,"
                    + " date_expiration)"
                    + " SELECT ?, 'FRAIS_DOSSIER', 'VIREMENT', 5000000, demandeur_id, "
                    + expiration
                    + " FROM demande WHERE id = ? RETURNING reference")) {
      ps.setLong(1, demande);
      ps.setLong(2, demande);
      ResultSet rs = ps.executeQuery();
      rs.next();
      return rs.getString(1);
    }
  }

  /** Modèles notifiés pour la demande (canal e-mail), dans l'ordre. */
  private static List<String> modeles(long demande) throws SQLException {
    List<String> modeles = new ArrayList<>();
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "SELECT modele FROM notification WHERE demande_id = ? AND canal = 'email'"
                    + " ORDER BY id")) {
      ps.setLong(1, demande);
      ResultSet rs = ps.executeQuery();
      while (rs.next()) {
        modeles.add(rs.getString(1));
      }
    }
    return modeles;
  }

  private static Set<String> evenements() throws SQLException {
    Set<String> e = new TreeSet<>();
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps = c.prepareStatement("SELECT modele FROM notification_evenement")) {
      ResultSet rs = ps.executeQuery();
      while (rs.next()) {
        e.add(rs.getString(1));
      }
    }
    return e;
  }

  private static long compter(long demande, String canal) throws SQLException {
    return valeur(
        "SELECT count(*) FROM notification WHERE demande_id = "
            + demande
            + " AND canal = '"
            + canal
            + "'");
  }

  private static long erreurs(long demande) throws SQLException {
    return valeur(
        "SELECT count(*) FROM notification WHERE demande_id = "
            + demande
            + " AND derniere_erreur IS NOT NULL");
  }

  private static long derniere(long demande) throws SQLException {
    return valeur(
        "SELECT max(id) FROM notification WHERE canal = 'interne' AND demande_id = " + demande);
  }

  private static long valeur(String sql) throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps = c.prepareStatement(sql)) {
      ResultSet rs = ps.executeQuery();
      rs.next();
      return rs.getLong(1);
    }
  }

  private static void executer(String sql) throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps = c.prepareStatement(sql)) {
      ps.execute();
    }
  }
}
