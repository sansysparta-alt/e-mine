package gn.gov.mines.emine.simulateur.tresorpay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Parcours complet du simulateur (US-04.1) : initiation signée, page de paiement, notifications de
 * chaque scénario reçues par un faux e-mine, redirection de retour, indisponibilité.
 */
@SpringBootTest(
    properties = {
      "tresorpay.secret-hmac=" + SimulateurTest.SECRET,
      "tresorpay.marchand-id=MARCHAND-TEST-0001",
      "tresorpay.url-retour-autorisee=http://localhost:3001/"
    })
@AutoConfigureMockMvc
class SimulateurTest {

  static final String SECRET = "secret-hmac-de-test";
  private static final String RETOUR = "http://localhost:3001/demandes/42/paiement/retour";

  /** Notification reçue par le faux e-mine. */
  record Recue(String horodatage, String signature, String marchand, byte[] corps) {}

  private static final List<Recue> RECUES = new CopyOnWriteArrayList<>();
  private static final HttpServer FAUX_EMINE = demarrerFauxEmine();

  /** Horloge réglable : avance pour tester l'expiration. */
  static final class HorlogeReglable extends Clock {
    private volatile Instant maintenant = Instant.parse("2026-11-21T10:00:00Z");

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return maintenant;
    }

    void avancer(Duration duree) {
      maintenant = maintenant.plus(duree);
    }
  }

  @TestConfiguration
  static class Config {
    @Bean
    @Primary
    HorlogeReglable horlogeDeTest() {
      return new HorlogeReglable();
    }
  }

  @DynamicPropertySource
  static void proprietes(DynamicPropertyRegistry registre) {
    registre.add(
        "tresorpay.url-notification",
        () -> "http://localhost:" + FAUX_EMINE.getAddress().getPort() + "/notifications");
  }

  @Autowired MockMvc mvc;
  @Autowired JsonMapper json;
  @Autowired RegistreSessions registre;
  @Autowired HorlogeReglable horloge;

  @BeforeEach
  void initialiser() {
    RECUES.clear();
    registre.definirDisponible(true);
  }

  @AfterAll
  static void arreter() {
    FAUX_EMINE.stop(0);
  }

  // --- Initiation (contrat §3) ---------------------------------------------------------------

  @Test
  void initiationSigneeOuvreUneSessionDeQuinzeMinutes() throws Exception {
    initier(corpsSession("PAY-2026-000001", 500_000), "MARCHAND-TEST-0001", true)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.sessionId").isNotEmpty())
        .andExpect(jsonPath("$.urlPaiement").value(org.hamcrest.Matchers.containsString("/payer/")))
        .andExpect(
            jsonPath("$.expireLe")
                .value(horloge.instant().plus(Duration.ofMinutes(15)).toString()));
  }

  @Test
  void signatureInvalideRefusee() throws Exception {
    initier(corpsSession("PAY-2026-000001", 500_000), "MARCHAND-TEST-0001", false)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("SIGNATURE_INVALIDE"));
  }

  @Test
  void marchandInconnuRefuse() throws Exception {
    initier(corpsSession("PAY-2026-000001", 500_000), "MARCHAND-INCONNU", true)
        .andExpect(status().isUnauthorized());
  }

  @Test
  void horodatageAncienRefuse() throws Exception {
    byte[] corps = corpsSession("PAY-2026-000001", 500_000);
    String ancien = Long.toString(horloge.instant().getEpochSecond() - 600);
    mvc.perform(
            post("/api/v1/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-TresorPay-Marchand", "MARCHAND-TEST-0001")
                .header("X-TresorPay-Horodatage", ancien)
                .header("X-TresorPay-Signature", SignatureHmac.signer(SECRET, ancien, corps))
                .content(corps))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void requetesInvalidesRefusees() throws Exception {
    initier(corpsSession("PAY-2026-1", 500_000), "MARCHAND-TEST-0001", true)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("REQUETE_INVALIDE"));
    initier(corpsSession("PAY-2026-000001", 0), "MARCHAND-TEST-0001", true)
        .andExpect(status().isBadRequest());
    byte[] retourEtranger =
        json.writeValueAsBytes(
            java.util.Map.of(
                "reference", "PAY-2026-000001",
                "montantGnf", 500_000,
                "libelle", "Frais",
                "urlRetour", "https://site-pirate.test/vol"));
    initier(retourEtranger, "MARCHAND-TEST-0001", true).andExpect(status().isBadRequest());
  }

  @Test
  void indisponibiliteRepond503() throws Exception {
    registre.definirDisponible(false);
    initier(corpsSession("PAY-2026-000001", 500_000), "MARCHAND-TEST-0001", true)
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("INDISPONIBLE"));
    mvc.perform(post("/disponibilite").param("disponible", "true"))
        .andExpect(status().isSeeOther());
    assertThat(registre.disponible()).isTrue();
  }

  // --- Page de paiement et scénarios (contrat §5, §6) ----------------------------------------

  @Test
  void pageDePaiementAfficheMontantReferenceEtScenarios() throws Exception {
    String id = ouvrirSession("PAY-2026-000002", 1_500_000);
    mvc.perform(get("/payer/" + id))
        .andExpect(status().isOk())
        .andExpect(
            content().string(org.hamcrest.Matchers.containsString("1\u202F500\u202F000\u00A0GNF")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("PAY-2026-000002")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Scénarios de test")));
  }

  @Test
  void succesEnvoieUneNotificationPayeeSigneeAvantLeRetour() throws Exception {
    String id = ouvrirSession("PAY-2026-000003", 500_000);
    mvc.perform(post("/payer/" + id).param("scenario", "succes"))
        .andExpect(status().isSeeOther())
        .andExpect(redirectedUrl(RETOUR + "?reference=PAY-2026-000003"));
    assertThat(RECUES).hasSize(1);
    Recue r = RECUES.getFirst();
    assertThat(signatureValide(r)).isTrue();
    assertThat(r.marchand()).isEqualTo("MARCHAND-TEST-0001");
    JsonNode n = json.readTree(r.corps());
    assertThat(n.get("reference").asString()).isEqualTo("PAY-2026-000003");
    assertThat(n.get("statut").asString()).isEqualTo("PAYE");
    assertThat(n.get("montantGnf").asLong()).isEqualTo(500_000L);
    assertThat(n.get("transactionId").asString()).startsWith("TP-");
  }

  @Test
  void refusEnvoieRefuse() throws Exception {
    String id = ouvrirSession("PAY-2026-000004", 500_000);
    mvc.perform(post("/payer/" + id).param("scenario", "refus")).andExpect(status().isSeeOther());
    assertThat(json.readTree(RECUES.getFirst().corps()).get("statut").asString())
        .isEqualTo("REFUSE");
  }

  @Test
  void abandonEnvoieAnnule() throws Exception {
    String id = ouvrirSession("PAY-2026-000005", 500_000);
    mvc.perform(post("/payer/" + id + "/annuler"))
        .andExpect(status().isSeeOther())
        .andExpect(redirectedUrl(RETOUR + "?reference=PAY-2026-000005"));
    assertThat(json.readTree(RECUES.getFirst().corps()).get("statut").asString())
        .isEqualTo("ANNULE");
  }

  @Test
  void signatureInvalideEnvoieUneSignatureFausse() throws Exception {
    String id = ouvrirSession("PAY-2026-000006", 500_000);
    mvc.perform(post("/payer/" + id).param("scenario", "signature-invalide"));
    assertThat(RECUES).hasSize(1);
    assertThat(signatureValide(RECUES.getFirst())).isFalse();
  }

  @Test
  void doubleNotificationEnvoieDeuxFoisLaMemeTransaction() throws Exception {
    String id = ouvrirSession("PAY-2026-000007", 500_000);
    mvc.perform(post("/payer/" + id).param("scenario", "double-notification"));
    assertThat(RECUES).hasSize(2);
    assertThat(RECUES.get(0).corps()).isEqualTo(RECUES.get(1).corps());
    assertThat(signatureValide(RECUES.get(1))).isTrue();
  }

  @Test
  void montantDivergentEnvoieMilleGnfDeMoins() throws Exception {
    String id = ouvrirSession("PAY-2026-000008", 500_000);
    mvc.perform(post("/payer/" + id).param("scenario", "montant-divergent"));
    assertThat(json.readTree(RECUES.getFirst().corps()).get("montantGnf").asLong())
        .isEqualTo(499_000L);
  }

  @Test
  void sansNotificationRedirigeSansRienEnvoyer() throws Exception {
    String id = ouvrirSession("PAY-2026-000009", 500_000);
    mvc.perform(post("/payer/" + id).param("scenario", "sans-notification"))
        .andExpect(redirectedUrl(RETOUR + "?reference=PAY-2026-000009"));
    assertThat(RECUES).isEmpty();
  }

  @Test
  void scenarioChoisiParLAdressePreselectionne() throws Exception {
    String id = ouvrirSession("PAY-2026-000010", 500_000);
    mvc.perform(get("/payer/" + id).param("scenario", "refus"))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.matchesPattern(
                        "(?s).*value=\"refus\"\\s+checked=\"checked\".*")));
  }

  @Test
  void deuxiemeEnvoiNeRejouePasLePaiement() throws Exception {
    String id = ouvrirSession("PAY-2026-000011", 500_000);
    mvc.perform(post("/payer/" + id).param("scenario", "succes"));
    mvc.perform(post("/payer/" + id).param("scenario", "succes"))
        .andExpect(status().isSeeOther())
        .andExpect(redirectedUrl("/payer/" + id));
    assertThat(RECUES).hasSize(1);
    mvc.perform(get("/payer/" + id))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("déjà été traité")));
  }

  @Test
  void sessionExpireeApresQuinzeMinutesSansNotification() throws Exception {
    String id = ouvrirSession("PAY-2026-000012", 500_000);
    horloge.avancer(Duration.ofMinutes(16));
    mvc.perform(get("/payer/" + id))
        .andExpect(
            content().string(org.hamcrest.Matchers.containsString("Session de paiement expirée")));
    mvc.perform(post("/payer/" + id).param("scenario", "succes"));
    assertThat(RECUES).isEmpty();
  }

  @Test
  void sessionInconnueRepond404() throws Exception {
    mvc.perform(get("/payer/inconnue")).andExpect(status().isNotFound());
  }

  @Test
  void accueilListeLesSessions() throws Exception {
    ouvrirSession("PAY-2026-000013", 500_000);
    mvc.perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("PAY-2026-000013")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Disponible")));
  }

  @Test
  void lInterrogationDonneLIssueDeLaDerniereSession() throws Exception {
    String id = ouvrirSession("PAY-2026-000020", 500_000);
    interroger("PAY-2026-000020", true)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.statut").value("EN_COURS"));
    // Paiement accepté sans notification : l'interrogation le confirme (US-04.5).
    mvc.perform(post("/payer/" + id).param("scenario", "sans-notification"));
    interroger("PAY-2026-000020", true)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.statut").value("PAYE"))
        .andExpect(jsonPath("$.montantGnf").value(500_000))
        .andExpect(jsonPath("$.transactionId").isNotEmpty());
  }

  @Test
  void lInterrogationRefuseUneSignatureFausseEtSignaleLesReferencesInconnues() throws Exception {
    interroger("PAY-2026-000021", false).andExpect(status().isUnauthorized());
    interroger("PAY-2026-000099", true)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INCONNUE"));
  }

  @Test
  void uneSessionNonPayeeExpireApresQuinzeMinutes() throws Exception {
    ouvrirSession("PAY-2026-000022", 500_000);
    horloge.avancer(Duration.ofMinutes(16));
    interroger("PAY-2026-000022", true).andExpect(jsonPath("$.statut").value("EXPIREE"));
  }

  // --- Outils --------------------------------------------------------------------------------

  private ResultActions interroger(String reference, boolean bonneSignature) throws Exception {
    String horodatage = Long.toString(horloge.instant().getEpochSecond());
    String secret = bonneSignature ? SECRET : "mauvais-secret";
    return mvc.perform(
        get("/api/v1/transactions/" + reference)
            .header("X-TresorPay-Marchand", "MARCHAND-TEST-0001")
            .header("X-TresorPay-Horodatage", horodatage)
            .header(
                "X-TresorPay-Signature", SignatureHmac.signer(secret, horodatage, new byte[0])));
  }

  private byte[] corpsSession(String reference, long montant) {
    return json.writeValueAsBytes(
        java.util.Map.of(
            "reference", reference,
            "montantGnf", montant,
            "libelle", "Frais de dossier · DEM-2026-000001",
            "urlRetour", RETOUR));
  }

  private ResultActions initier(byte[] corps, String marchand, boolean bonneSignature)
      throws Exception {
    String horodatage = Long.toString(horloge.instant().getEpochSecond());
    String secret = bonneSignature ? SECRET : "mauvais-secret";
    return mvc.perform(
        post("/api/v1/sessions")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-TresorPay-Marchand", marchand)
            .header("X-TresorPay-Horodatage", horodatage)
            .header("X-TresorPay-Signature", SignatureHmac.signer(secret, horodatage, corps))
            .content(corps));
  }

  private String ouvrirSession(String reference, long montant) throws Exception {
    String reponse =
        initier(corpsSession(reference, montant), "MARCHAND-TEST-0001", true)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    return json.readTree(reponse).get("sessionId").asString();
  }

  private boolean signatureValide(Recue r) {
    return SignatureHmac.verifier(
        SECRET, r.horodatage(), r.corps(), r.signature(), horloge, Duration.ofMinutes(5));
  }

  private static HttpServer demarrerFauxEmine() {
    try {
      HttpServer serveur = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      serveur.createContext(
          "/notifications",
          echange -> {
            byte[] corps = echange.getRequestBody().readAllBytes();
            RECUES.add(
                new Recue(
                    echange.getRequestHeaders().getFirst("X-TresorPay-Horodatage"),
                    echange.getRequestHeaders().getFirst("X-TresorPay-Signature"),
                    echange.getRequestHeaders().getFirst("X-TresorPay-Marchand"),
                    corps));
            echange.sendResponseHeaders(200, -1);
            echange.close();
          });
      serveur.start();
      return serveur;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
