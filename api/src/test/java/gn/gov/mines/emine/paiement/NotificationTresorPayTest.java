package gn.gov.mines.emine.paiement;

import static gn.gov.mines.emine.shared.jooq.Tables.PAIEMENT;
import static gn.gov.mines.emine.support.Personas.INVESTISSEUR;
import static gn.gov.mines.emine.support.Personas.jeton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gn.gov.mines.emine.paiement.domaine.SignatureTresorPay;
import gn.gov.mines.emine.support.BaseDeDonneesDeTest;
import gn.gov.mines.emine.support.DemandesDeTest;
import gn.gov.mines.emine.support.Personas;
import gn.gov.mines.emine.support.TestIntegration;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.Year;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * US-04.4 : la notification TrésorPay signée est la seule preuve du paiement. Signature invalide →
 * 401 PAY-004 et journal ; même transaction → 200 sans retraitement ; montant divergent → à
 * vérifier ; transitions contrôlées par la base. Le contexte système n'est actif que pendant la
 * transaction de la notification.
 */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NotificationTresorPayTest extends TestIntegration {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String URL = "/api/v1/paiements/notifications/tresorpay";

  @Autowired MockMvc mvc;
  @Autowired DSLContext dsl;
  @Autowired PlatformTransactionManager transactions;

  @BeforeAll
  void personas() throws Exception {
    Personas.charger();
  }

  @Test
  void notificationPayeeConfirmeLePaiementEtLaBaseAttribueLeRecu() throws Exception {
    long demande = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    String ref = payerEnLigne(demande);
    mvc.perform(get("/api/v1/demandes/" + demande).with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(jsonPath("$.fraisPayes").value(false));
    String transaction = transaction();
    notifier(transaction, ref, "PAYE", 5_000_000, SECRET_TRESORPAY, maintenant())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.issue").value("TRAITEE"));
    assertThat(DemandesDeTest.statutPaiement(ref)).isEqualTo("paye");
    mvc.perform(get("/api/v1/demandes/" + demande).with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(jsonPath("$.statut").value("deposee"))
        .andExpect(jsonPath("$.fraisPayes").value(true));
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "SELECT recu_numero, transaction_externe_id, montant_recu_gnf, d.statut::text"
                    + " FROM paiement p JOIN demande d ON d.id = p.demande_id"
                    + " WHERE p.reference = ?")) {
      ps.setString(1, ref);
      ResultSet rs = ps.executeQuery();
      rs.next();
      assertThat(rs.getString(1)).matches("REC-" + Year.now().getValue() + "-\\d{6}");
      assertThat(rs.getString(2)).isEqualTo(transaction);
      assertThat(rs.getLong(3)).isEqualTo(5_000_000L);
      assertThat(rs.getString(4))
          .as("V8 : le guichet déclare la recevabilité")
          .isEqualTo("deposee");
    }
    assertThat(evenements(transaction)).isEqualTo(1);
  }

  @Test
  void laMemeTransactionRecueDeuxFoisNestTraiteeQuUneFois() throws Exception {
    String ref = payerEnLigne(DemandesDeTest.deposee(INVESTISSEUR, "PEX"));
    String transaction = transaction();
    notifier(transaction, ref, "PAYE", 5_000_000, SECRET_TRESORPAY, maintenant())
        .andExpect(status().isOk());
    String recu = recu(ref);
    notifier(transaction, ref, "PAYE", 5_000_000, SECRET_TRESORPAY, maintenant())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.issue").value("DEJA_TRAITEE"));
    assertThat(recu(ref)).as("pas de second reçu").isEqualTo(recu);
    assertThat(evenements(transaction)).isEqualTo(1);
  }

  @Test
  void signatureInvalideRefuseeAvecPay004JournaliseeEtSansEffet() throws Exception {
    String ref = payerEnLigne(DemandesDeTest.deposee(INVESTISSEUR, "PEX"));
    long refusesAvant = notificationsRefusees();
    notifier(transaction(), ref, "PAYE", 5_000_000, "secret-pirate", maintenant())
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("PAY-004"));
    assertThat(DemandesDeTest.statutPaiement(ref)).isEqualTo("en_attente");
    assertThat(notificationsRefusees()).isEqualTo(refusesAvant + 1);
  }

  @Test
  void notificationTropAncienneRefuseeAntiRejeu() throws Exception {
    String ref = payerEnLigne(DemandesDeTest.deposee(INVESTISSEUR, "PEX"));
    notifier(
            transaction(),
            ref,
            "PAYE",
            5_000_000,
            SECRET_TRESORPAY,
            Instant.now().minusSeconds(6 * 60).getEpochSecond())
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("PAY-004"));
    assertThat(DemandesDeTest.statutPaiement(ref)).isEqualTo("en_attente");
  }

  @Test
  void marchandInconnuRefuse() throws Exception {
    String ref = payerEnLigne(DemandesDeTest.deposee(INVESTISSEUR, "PEX"));
    byte[] corps = corps(transaction(), ref, "PAYE", 5_000_000);
    String h = Long.toString(maintenant());
    mvc.perform(
            post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-TresorPay-Marchand", "MARCHAND-PIRATE")
                .header("X-TresorPay-Horodatage", h)
                .header(
                    "X-TresorPay-Signature", SignatureTresorPay.signer(SECRET_TRESORPAY, h, corps))
                .content(corps))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void montantDivergentMetLePaiementAVerifier() throws Exception {
    String ref = payerEnLigne(DemandesDeTest.deposee(INVESTISSEUR, "PEX"));
    notifier(transaction(), ref, "PAYE", 4_999_000, SECRET_TRESORPAY, maintenant())
        .andExpect(status().isOk());
    assertThat(DemandesDeTest.statutPaiement(ref)).isEqualTo("a_verifier");
  }

  @Test
  void unRefusLaisseUneNouvelleTentativePossible() throws Exception {
    long demande = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    String ref = payerEnLigne(demande);
    notifier(transaction(), ref, "REFUSE", 5_000_000, SECRET_TRESORPAY, maintenant())
        .andExpect(status().isOk());
    assertThat(DemandesDeTest.statutPaiement(ref)).isEqualTo("echoue");
    mvc.perform(
            get("/api/v1/demandes/" + demande + "/paiements/a-payer")
                .with(jeton(INVESTISSEUR, "INVESTISSEUR")))
        .andExpect(jsonPath("$.objet").value("FRAIS_DOSSIER"))
        .andExpect(jsonPath("$.referenceOuverte").value(nullValue()))
        .andExpect(jsonPath("$.tentativesRestantes").value(2));
    String nouvelle = payerEnLigne(demande);
    assertThat(nouvelle).isNotEqualTo(ref);
  }

  @Test
  void unAbandonAnnuleLePaiement() throws Exception {
    String ref = payerEnLigne(DemandesDeTest.deposee(INVESTISSEUR, "PEX"));
    notifier(transaction(), ref, "ANNULE", 5_000_000, SECRET_TRESORPAY, maintenant())
        .andExpect(status().isOk());
    assertThat(DemandesDeTest.statutPaiement(ref)).isEqualTo("annule");
  }

  @Test
  void referenceInconnueRepond404EtEstJournalisee() throws Exception {
    String transaction = transaction();
    notifier(transaction, "PAY-1999-999999", "PAYE", 5_000_000, SECRET_TRESORPAY, maintenant())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PAY-005"));
    assertThat(evenements(transaction)).isEqualTo(1);
  }

  @Test
  void notificationSurUnPaiementDejaCloSansEffet() throws Exception {
    long demande = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    String ancien = payerEnLigne(demande);
    payerEnLigne(demande); // changement d'avis : l'ancien est annulé
    notifier(transaction(), ancien, "PAYE", 5_000_000, SECRET_TRESORPAY, maintenant())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.issue").value("IGNOREE"));
    assertThat(DemandesDeTest.statutPaiement(ancien)).isEqualTo("annule");
  }

  @Test
  void lesDroitsPayesFontPasserLaDemandeADroitsPayes() throws Exception {
    long demande = DemandesDeTest.deposee(INVESTISSEUR, "PEX");
    String frais = payerEnLigne(demande);
    notifier(transaction(), frais, "PAYE", 5_000_000, SECRET_TRESORPAY, maintenant());
    DemandesDeTest.changerStatut(demande, "recevable", "en_instruction", "avis_rendu", "decision");
    String droits = payerEnLigne(demande);
    notifier(transaction(), droits, "PAYE", 15_000_000, SECRET_TRESORPAY, maintenant())
        .andExpect(status().isOk());
    assertThat(statutDemande(demande)).isEqualTo("droits_payes");
  }

  // --- Garde-fous du contexte système ---------------------------------------------------------

  @Test
  void leContexteSystemeNeSurvitPasALaNotification() throws Exception {
    String ref = payerEnLigne(DemandesDeTest.deposee(INVESTISSEUR, "PEX"));
    notifier(transaction(), ref, "PAYE", 5_000_000, SECRET_TRESORPAY, maintenant())
        .andExpect(status().isOk());
    // Même fil, juste après : une transaction ordinaire est anonyme, la RLS ne montre rien.
    TransactionTemplate tx = new TransactionTemplate(transactions);
    Integer visibles = tx.execute(s -> dsl.fetchCount(PAIEMENT, PAIEMENT.REFERENCE.eq(ref)));
    String interne =
        tx.execute(
            s ->
                dsl.fetchValue(
                    org.jooq.impl.DSL.field(
                        "current_setting('emine.interne', true)", String.class)));
    assertThat(visibles).isZero();
    assertThat(interne).isEqualTo("off");
  }

  @Test
  void leTraitementEstJournaliseAuNomDuSystemeSansUtilisateur() throws Exception {
    String ref = payerEnLigne(DemandesDeTest.deposee(INVESTISSEUR, "PEX"));
    UUID id;
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps = c.prepareStatement("SELECT id FROM paiement WHERE reference = ?")) {
      ps.setString(1, ref);
      ResultSet rs = ps.executeQuery();
      rs.next();
      id = (UUID) rs.getObject(1);
    }
    notifier(transaction(), ref, "PAYE", 5_000_000, SECRET_TRESORPAY, maintenant())
        .andExpect(status().isOk());
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement(
                "SELECT utilisateur_id FROM journal_audit WHERE table_nom = 'paiement'"
                    + " AND objet_id = ? AND nouvelles_valeurs->>'statut' = 'paye'")) {
      ps.setString(1, id.toString());
      ResultSet rs = ps.executeQuery();
      assertThat(rs.next()).as("passage à « payé » journalisé").isTrue();
      rs.getLong(1);
      assertThat(rs.wasNull()).as("aucun utilisateur : le système").isTrue();
    }
  }

  // --- Outils ---------------------------------------------------------------------------------

  private String payerEnLigne(long demande) throws Exception {
    String reponse =
        mvc.perform(
                post("/api/v1/demandes/" + demande + "/paiements")
                    .with(jeton(INVESTISSEUR, "INVESTISSEUR"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"moyen\":\"TRESORPAY\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JSON.readTree(reponse).path("paiement").path("reference").asString();
  }

  private static String transaction() {
    return "TP-" + UUID.randomUUID();
  }

  private static long maintenant() {
    return Instant.now().getEpochSecond();
  }

  private static byte[] corps(String transaction, String ref, String statut, long montant) {
    return ("{\"transactionId\":\""
            + transaction
            + "\",\"reference\":\""
            + ref
            + "\",\"statut\":\""
            + statut
            + "\",\"montantGnf\":"
            + montant
            + ",\"date\":\""
            + Instant.now()
            + "\"}")
        .getBytes(StandardCharsets.UTF_8);
  }

  private ResultActions notifier(
      String transaction, String ref, String statut, long montant, String secret, long horodatage)
      throws Exception {
    byte[] corps = corps(transaction, ref, statut, montant);
    String h = Long.toString(horodatage);
    return mvc.perform(
        post(URL)
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-TresorPay-Marchand", MARCHAND_TRESORPAY)
            .header("X-TresorPay-Horodatage", h)
            .header("X-TresorPay-Signature", SignatureTresorPay.signer(secret, h, corps))
            .content(corps));
  }

  private static long evenements(String transaction) throws SQLException {
    return compter(
        "SELECT count(*) FROM paiement_evenement WHERE transaction_id = ?"
            + " AND type_evenement = 'NOTIFICATION'",
        transaction);
  }

  private static long notificationsRefusees() throws SQLException {
    return compter(
        "SELECT count(*) FROM paiement_evenement WHERE signature_valide = false AND ? = ?", "x");
  }

  private static long compter(String sql, String valeur) throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setString(1, valeur);
      if (sql.indexOf('?') != sql.lastIndexOf('?')) {
        ps.setString(2, valeur);
      }
      ResultSet rs = ps.executeQuery();
      rs.next();
      return rs.getLong(1);
    }
  }

  private static String recu(String ref) throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement("SELECT recu_numero FROM paiement WHERE reference = ?")) {
      ps.setString(1, ref);
      ResultSet rs = ps.executeQuery();
      rs.next();
      return rs.getString(1);
    }
  }

  private static String statutDemande(long id) throws SQLException {
    try (Connection c = BaseDeDonneesDeTest.connexionOwner();
        PreparedStatement ps =
            c.prepareStatement("SELECT statut::text FROM demande WHERE id = ?")) {
      ps.setLong(1, id);
      ResultSet rs = ps.executeQuery();
      rs.next();
      return rs.getString(1);
    }
  }
}
