package gn.gov.mines.emine.paiement.domaine;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Compatibilité des signatures avec le simulateur TrésorPay (contrat §2) : même valeur de référence
 * que {@code simulateurs/tresorpay/.../SignatureHmacTest}, calculée indépendamment par openssl.
 */
class SignatureTresorPayTest {

  private static final byte[] CORPS = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);

  @Test
  void memeSignatureQueLeSimulateurEtQueOpenssl() {
    // printf '1700000000.{"a":1}' | openssl dgst -sha256 -hmac secret-de-test
    assertThat(SignatureTresorPay.signer("secret-de-test", "1700000000", CORPS))
        .isEqualTo("7176b4ba69b6246f08e1419e6628f7a6c5388665e468172b6e350afb0575754e");
  }

  @Test
  void verificationEnTempsConstantAvecToleranceDeCinqMinutes() {
    Instant maintenant = Instant.ofEpochSecond(1_700_000_000L);
    String sig = SignatureTresorPay.signer("secret-de-test", "1700000000", CORPS);
    Duration cinqMinutes = Duration.ofMinutes(5);
    assertThat(
            SignatureTresorPay.verifier(
                "secret-de-test", "1700000000", CORPS, sig.toUpperCase(), maintenant, cinqMinutes))
        .isTrue();
    assertThat(
            SignatureTresorPay.verifier(
                "secret-de-test",
                "1700000000",
                CORPS,
                sig,
                maintenant.plusSeconds(301),
                cinqMinutes))
        .isFalse();
    assertThat(
            SignatureTresorPay.verifier(
                "secret-de-test", "1700000000", null, sig, maintenant, cinqMinutes))
        .isFalse();
  }
}
