package gn.gov.mines.emine.simulateur.tresorpay;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signature des échanges e-mine ↔ TrésorPay (contrat §2) : {@code HMAC-SHA256(secret, horodatage +
 * "." + corps brut)} en hexadécimal minuscule, comparée en temps constant.
 */
public final class SignatureHmac {

  private SignatureHmac() {}

  /** Signe {@code horodatage + "." + corps}. */
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
   * Vrai si la signature est exacte et l'horodatage (secondes Unix) dans la tolérance admise. Toute
   * valeur absente ou mal formée est refusée.
   */
  public static boolean verifier(
      String secret,
      String horodatage,
      byte[] corps,
      String signature,
      Clock horloge,
      Duration tolerance) {
    if (horodatage == null || signature == null || corps == null) {
      return false;
    }
    long secondes;
    try {
      secondes = Long.parseLong(horodatage.strip());
    } catch (NumberFormatException e) {
      return false;
    }
    long ecart = Math.abs(horloge.instant().getEpochSecond() - secondes);
    if (ecart > tolerance.toSeconds()) {
      return false;
    }
    byte[] attendue = signer(secret, horodatage.strip(), corps).getBytes(StandardCharsets.UTF_8);
    byte[] recue = signature.strip().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
    return MessageDigest.isEqual(attendue, recue);
  }
}
