package gn.gov.mines.emine.simulateur.tresorpay;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** Signature HMAC-SHA256 du contrat §2 : valeur de référence, anti-rejeu, entrées invalides. */
class SignatureHmacTest {

  private static final String SECRET = "secret-de-test";
  private static final byte[] CORPS = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
  private static final Clock HORLOGE =
      Clock.fixed(Instant.ofEpochSecond(1_700_000_000L), ZoneOffset.UTC);
  private static final Duration TOLERANCE = Duration.ofMinutes(5);

  @Test
  void signatureConformeALaValeurDeReference() {
    // Calculée indépendamment : printf '1700000000.{"a":1}' | openssl dgst -sha256 -hmac
    // secret-de-test
    assertThat(SignatureHmac.signer(SECRET, "1700000000", CORPS))
        .isEqualTo("7176b4ba69b6246f08e1419e6628f7a6c5388665e468172b6e350afb0575754e");
  }

  @Test
  void signatureExacteAcceptee() {
    String sig = SignatureHmac.signer(SECRET, "1700000000", CORPS);
    assertThat(SignatureHmac.verifier(SECRET, "1700000000", CORPS, sig, HORLOGE, TOLERANCE))
        .isTrue();
    assertThat(
            SignatureHmac.verifier(
                SECRET, "1700000000", CORPS, sig.toUpperCase(), HORLOGE, TOLERANCE))
        .isTrue();
  }

  @Test
  void corpsModifieRefuse() {
    String sig = SignatureHmac.signer(SECRET, "1700000000", CORPS);
    byte[] modifie = "{\"a\":2}".getBytes(StandardCharsets.UTF_8);
    assertThat(SignatureHmac.verifier(SECRET, "1700000000", modifie, sig, HORLOGE, TOLERANCE))
        .isFalse();
  }

  @Test
  void autreSecretRefuse() {
    String sig = SignatureHmac.signer("autre-secret", "1700000000", CORPS);
    assertThat(SignatureHmac.verifier(SECRET, "1700000000", CORPS, sig, HORLOGE, TOLERANCE))
        .isFalse();
  }

  @Test
  void horodatageHorsToleranceRefuse() {
    String ancien = Long.toString(1_700_000_000L - 301);
    String futur = Long.toString(1_700_000_000L + 301);
    String limite = Long.toString(1_700_000_000L - 300);
    assertThat(
            SignatureHmac.verifier(
                SECRET,
                ancien,
                CORPS,
                SignatureHmac.signer(SECRET, ancien, CORPS),
                HORLOGE,
                TOLERANCE))
        .isFalse();
    assertThat(
            SignatureHmac.verifier(
                SECRET,
                futur,
                CORPS,
                SignatureHmac.signer(SECRET, futur, CORPS),
                HORLOGE,
                TOLERANCE))
        .isFalse();
    assertThat(
            SignatureHmac.verifier(
                SECRET,
                limite,
                CORPS,
                SignatureHmac.signer(SECRET, limite, CORPS),
                HORLOGE,
                TOLERANCE))
        .isTrue();
  }

  @Test
  void horodatageModifieApresSignatureRefuse() {
    String sig = SignatureHmac.signer(SECRET, "1700000000", CORPS);
    assertThat(SignatureHmac.verifier(SECRET, "1700000001", CORPS, sig, HORLOGE, TOLERANCE))
        .isFalse();
  }

  @Test
  void entreesAbsentesOuMalFormeesRefusees() {
    String sig = SignatureHmac.signer(SECRET, "1700000000", CORPS);
    assertThat(SignatureHmac.verifier(SECRET, null, CORPS, sig, HORLOGE, TOLERANCE)).isFalse();
    assertThat(SignatureHmac.verifier(SECRET, "abc", CORPS, sig, HORLOGE, TOLERANCE)).isFalse();
    assertThat(SignatureHmac.verifier(SECRET, "1700000000", CORPS, null, HORLOGE, TOLERANCE))
        .isFalse();
    assertThat(SignatureHmac.verifier(SECRET, "1700000000", null, sig, HORLOGE, TOLERANCE))
        .isFalse();
  }
}
