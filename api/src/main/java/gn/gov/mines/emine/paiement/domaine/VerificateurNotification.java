package gn.gov.mines.emine.paiement.domaine;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Vérifie une notification TrésorPay (contrat §2 et §4) dans cet ordre : marchand, horodatage dans
 * la tolérance (anti-rejeu), signature HMAC-SHA256 sur le corps brut, puis seulement le contenu
 * JSON. Toute anomalie donne un résultat vide : la notification est alors refusée (PAY-004).
 */
public final class VerificateurNotification {

  private static final Pattern REFERENCE = Pattern.compile("^PAY-[0-9]{4}-[0-9]{6}$");
  private static final Pattern TRANSACTION = Pattern.compile("^[A-Za-z0-9._:-]{1,100}$");

  private final String secret;
  private final String marchandAttendu;
  private final Duration tolerance;
  private final JsonMapper json;

  public VerificateurNotification(
      String secret, String marchandAttendu, Duration tolerance, JsonMapper json) {
    this.secret = secret;
    this.marchandAttendu = marchandAttendu;
    this.tolerance = tolerance;
    this.json = json;
  }

  /**
   * Notification vérifiée, ou vide si le marchand, l'horodatage, la signature ou le contenu est
   * invalide.
   */
  public Optional<NotificationVerifiee> verifier(
      byte[] corps, String marchand, String horodatage, String signature, Instant maintenant) {
    if (marchandAttendu == null || !marchandAttendu.equals(marchand)) {
      return Optional.empty();
    }
    if (!SignatureTresorPay.verifier(secret, horodatage, corps, signature, maintenant, tolerance)) {
      return Optional.empty();
    }
    JsonNode n;
    try {
      n = json.readTree(corps);
    } catch (JacksonException e) {
      return Optional.empty();
    }
    String transactionId = n.path("transactionId").asString("");
    String reference = n.path("reference").asString("");
    String statut = n.path("statut").asString("");
    JsonNode montant = n.path("montantGnf");
    if (!TRANSACTION.matcher(transactionId).matches()
        || !REFERENCE.matcher(reference).matches()
        || !montant.isIntegralNumber()
        || montant.asLong() <= 0) {
      return Optional.empty();
    }
    NotificationVerifiee.Statut s;
    try {
      s = NotificationVerifiee.Statut.valueOf(statut);
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
    return Optional.of(
        new NotificationVerifiee(
            transactionId,
            reference,
            s,
            montant.asLong(),
            new String(corps, StandardCharsets.UTF_8)));
  }
}
