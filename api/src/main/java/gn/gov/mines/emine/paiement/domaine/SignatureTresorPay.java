package gn.gov.mines.emine.paiement.domaine;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signature des échanges avec TrésorPay (contrat §2) : {@code HMAC-SHA256(secret, horodatage + "."
 * + corps brut)} en hexadécimal minuscule. La vérification compare en temps constant et refuse un
 * horodatage écarté de plus de la tolérance (anti-rejeu).
 */
public final class SignatureTresorPay {

  private SignatureTresorPay() {}

  public static String signer(String secret, String horodatage, byte[] corps) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      mac.update((horodatage + ".").getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(mac.doFinal(corps));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HMAC-SHA256 indisponible", e);
    }
  }

  /**
   * Vrai si la signature est exacte et l'horodatage (secondes Unix) dans la tolérance. Toute valeur
   * absente ou mal formée est refusée.
   */
  public static boolean verifier(
      String secret,
      String horodatage,
      byte[] corps,
      String signature,
      Instant maintenant,
      Duration tolerance) {
    if (secret == null || secret.isBlank() || horodatage == null || signature == null) {
      return false;
    }
    if (corps == null) {
      return false;
    }
    long secondes;
    try {
      secondes = Long.parseLong(horodatage.strip());
    } catch (NumberFormatException e) {
      return false;
    }
    if (Math.abs(maintenant.getEpochSecond() - secondes) > tolerance.toSeconds()) {
      return false;
    }
    byte[] attendue = signer(secret, horodatage.strip(), corps).getBytes(StandardCharsets.UTF_8);
    byte[] recue = signature.strip().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
    return MessageDigest.isEqual(attendue, recue);
  }
}
