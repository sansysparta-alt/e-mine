package gn.gov.mines.emine.simulateur.tresorpay;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Envoie la notification signée à e-mine (contrat §4) selon le scénario choisi, avant la
 * redirection du navigateur.
 */
@Component
class ExpediteurNotifications {

  private static final Logger LOG = LoggerFactory.getLogger(ExpediteurNotifications.class);
  static final long ECART_MONTANT_DIVERGENT = 1_000L;

  private final ProprietesSimulateur proprietes;
  private final HttpClient client;
  private final JsonMapper json;
  private final Clock horloge;

  ExpediteurNotifications(
      ProprietesSimulateur proprietes, HttpClient client, JsonMapper json, Clock horloge) {
    this.proprietes = proprietes;
    this.client = client;
    this.json = json;
    this.horloge = horloge;
  }

  /**
   * Joue le scénario pour la session et renvoie le résultat (identifiant de transaction et compte
   * rendu lisible des envois).
   */
  SessionPaiement.Resultat jouer(SessionPaiement session, Scenario scenario) {
    String transactionId = "TP-" + UUID.randomUUID();
    if (scenario == Scenario.SANS_NOTIFICATION) {
      // Paiement accepté, mais notification perdue : e-mine doit interroger (contrat §5).
      return new SessionPaiement.Resultat(
          scenario,
          transactionId,
          "Aucune notification envoyée (paiement accepté, à interroger)",
          "PAYE",
          session.montantGnf(),
          horloge.instant());
    }
    byte[] corps = corps(session, scenario, transactionId);
    List<String> comptesRendus = new ArrayList<>();
    comptesRendus.add(envoyer(corps, scenario == Scenario.SIGNATURE_INVALIDE));
    if (scenario == Scenario.DOUBLE_NOTIFICATION) {
      comptesRendus.add(envoyer(corps, false));
    }
    return new SessionPaiement.Resultat(
        scenario,
        transactionId,
        String.join(" puis ", comptesRendus),
        statut(scenario),
        montant(session, scenario),
        horloge.instant());
  }

  private static String statut(Scenario scenario) {
    return switch (scenario) {
      case REFUS -> "REFUSE";
      case ABANDON -> "ANNULE";
      default -> "PAYE";
    };
  }

  private static long montant(SessionPaiement session, Scenario scenario) {
    return scenario == Scenario.MONTANT_DIVERGENT
        ? Math.max(1, session.montantGnf() - ECART_MONTANT_DIVERGENT)
        : session.montantGnf();
  }

  private byte[] corps(SessionPaiement session, Scenario scenario, String transactionId) {
    String statut = statut(scenario);
    long montant = montant(session, scenario);
    Map<String, Object> notification = new LinkedHashMap<>();
    notification.put("transactionId", transactionId);
    notification.put("reference", session.reference());
    notification.put("statut", statut);
    notification.put("montantGnf", montant);
    notification.put("date", horloge.instant().toString());
    return json.writeValueAsBytes(notification);
  }

  private String envoyer(byte[] corps, boolean signatureFaussee) {
    String horodatage = Long.toString(Instant.now(horloge).getEpochSecond());
    String signature = SignatureHmac.signer(proprietes.secretHmac(), horodatage, corps);
    if (signatureFaussee) {
      signature = SignatureHmac.signer("signature-volontairement-fausse", horodatage, corps);
    }
    HttpRequest requete =
        HttpRequest.newBuilder(URI.create(proprietes.urlNotification()))
            .timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/json")
            .header("X-TresorPay-Marchand", proprietes.marchandId())
            .header("X-TresorPay-Horodatage", horodatage)
            .header("X-TresorPay-Signature", signature)
            .POST(HttpRequest.BodyPublishers.ofByteArray(corps))
            .build();
    try {
      HttpResponse<Void> reponse = client.send(requete, HttpResponse.BodyHandlers.discarding());
      LOG.info("Notification envoyée : e-mine a répondu {}", reponse.statusCode());
      return "HTTP " + reponse.statusCode();
    } catch (IOException e) {
      LOG.warn("Notification non remise : {}", e.getMessage());
      return "e-mine injoignable";
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return "envoi interrompu";
    }
  }
}
