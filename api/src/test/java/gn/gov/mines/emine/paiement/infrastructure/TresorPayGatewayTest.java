package gn.gov.mines.emine.paiement.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import gn.gov.mines.emine.paiement.domaine.PaiementGateway.DemandeSession;
import gn.gov.mines.emine.paiement.domaine.PaiementGateway.PasserelleIndisponible;
import gn.gov.mines.emine.paiement.domaine.PaiementGateway.SessionOuverte;
import gn.gov.mines.emine.paiement.domaine.SignatureTresorPay;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Adaptateur TrésorPay (contrat §3) contre un faux TrésorPay local : requête signée, réponses 201,
 * 503, 401, passerelle injoignable.
 */
class TresorPayGatewayTest {

  private static final String SECRET = "secret-de-test-gateway";
  private static final Clock HORLOGE =
      Clock.fixed(Instant.parse("2026-11-21T10:00:00Z"), ZoneOffset.UTC);
  private static final DemandeSession DEMANDE =
      new DemandeSession(
          "PAY-2026-000001",
          5_000_000L,
          "Frais de dossier · DEM-2026-000001",
          "http://localhost:3001/demandes/1/paiement/retour");

  private HttpServer serveur;
  private final AtomicInteger code = new AtomicInteger(201);
  private final AtomicReference<String> signatureRecue = new AtomicReference<>();
  private final AtomicReference<String> horodatageRecu = new AtomicReference<>();
  private final AtomicReference<String> marchandRecu = new AtomicReference<>();
  private final AtomicReference<byte[]> corpsRecu = new AtomicReference<>();

  @BeforeEach
  void demarrer() throws IOException {
    serveur = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    serveur.createContext(
        "/api/v1/sessions",
        e -> {
          corpsRecu.set(e.getRequestBody().readAllBytes());
          signatureRecue.set(e.getRequestHeaders().getFirst("X-TresorPay-Signature"));
          horodatageRecu.set(e.getRequestHeaders().getFirst("X-TresorPay-Horodatage"));
          marchandRecu.set(e.getRequestHeaders().getFirst("X-TresorPay-Marchand"));
          byte[] reponse =
              ("{\"sessionId\":\"s-1\",\"urlPaiement\":\"http://localhost:8090/payer/s-1\","
                      + "\"expireLe\":\"2026-11-21T10:15:00Z\"}")
                  .getBytes(StandardCharsets.UTF_8);
          e.sendResponseHeaders(code.get(), reponse.length);
          e.getResponseBody().write(reponse);
          e.close();
        });
    serveur.start();
  }

  @AfterEach
  void arreter() {
    serveur.stop(0);
  }

  @Test
  void ouvreUneSessionAvecUneRequeteSigneeConformeAuContrat() {
    SessionOuverte session = passerelle(url()).ouvrirSession(DEMANDE);
    assertThat(session.sessionId()).isEqualTo("s-1");
    assertThat(session.urlPaiement()).isEqualTo("http://localhost:8090/payer/s-1");
    assertThat(session.expireLe()).isEqualTo(Instant.parse("2026-11-21T10:15:00Z"));
    assertThat(marchandRecu.get()).isEqualTo("MARCHAND-TEST-0001");
    assertThat(horodatageRecu.get()).isEqualTo(Long.toString(HORLOGE.instant().getEpochSecond()));
    assertThat(
            SignatureTresorPay.verifier(
                SECRET,
                horodatageRecu.get(),
                corpsRecu.get(),
                signatureRecue.get(),
                HORLOGE.instant(),
                Duration.ofMinutes(5)))
        .isTrue();
    String corps = new String(corpsRecu.get(), StandardCharsets.UTF_8);
    assertThat(corps).contains("\"reference\":\"PAY-2026-000001\"", "\"montantGnf\":5000000");
  }

  @Test
  void indisponibiliteDeclareeDonnePasserelleIndisponible() {
    code.set(503);
    assertThatThrownBy(() -> passerelle(url()).ouvrirSession(DEMANDE))
        .isInstanceOf(PasserelleIndisponible.class);
  }

  @Test
  void passerelleInjoignableDonnePasserelleIndisponible() {
    String url = url();
    serveur.stop(0);
    assertThatThrownBy(() -> passerelle(url).ouvrirSession(DEMANDE))
        .isInstanceOf(PasserelleIndisponible.class);
  }

  @Test
  void signatureRefuseeEstUneErreurDeConfigurationEtNonUneIndisponibilite() {
    code.set(401);
    assertThatThrownBy(() -> passerelle(url()).ouvrirSession(DEMANDE))
        .isInstanceOf(IllegalStateException.class)
        .isNotInstanceOf(PasserelleIndisponible.class);
  }

  private String url() {
    return "http://localhost:" + serveur.getAddress().getPort();
  }

  private static TresorPayGateway passerelle(String url) {
    return new TresorPayGateway(
        new ProprietesTresorPay(
            url,
            SECRET,
            "MARCHAND-TEST-0001",
            "http://localhost:3001",
            Duration.ofMinutes(5),
            Duration.ofSeconds(2)),
        JsonMapper.builder().build(),
        HORLOGE);
  }
}
