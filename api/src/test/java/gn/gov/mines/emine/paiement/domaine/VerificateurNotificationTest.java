package gn.gov.mines.emine.paiement.domaine;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Vérification d'une notification TrésorPay (contrat §2 et §4), sans base. */
class VerificateurNotificationTest {

  private static final String SECRET = "secret-verif";
  private static final Instant MAINTENANT = Instant.parse("2026-11-21T10:00:00Z");
  private static final String H = Long.toString(MAINTENANT.getEpochSecond());
  private final VerificateurNotification verificateur =
      new VerificateurNotification(
          SECRET, "MARCHAND-TEST-0001", Duration.ofMinutes(5), JsonMapper.builder().build());

  private static byte[] corps(String contenu) {
    return contenu.getBytes(StandardCharsets.UTF_8);
  }

  private static final byte[] VALIDE =
      corps(
          "{\"transactionId\":\"TP-abc\",\"reference\":\"PAY-2026-000001\",\"statut\":\"PAYE\","
              + "\"montantGnf\":500000,\"date\":\"2026-11-21T10:00:00Z\"}");

  @Test
  void notificationValideAcceptee() {
    NotificationVerifiee n =
        verificateur
            .verifier(
                VALIDE,
                "MARCHAND-TEST-0001",
                H,
                SignatureTresorPay.signer(SECRET, H, VALIDE),
                MAINTENANT)
            .orElseThrow();
    assertThat(n.transactionId()).isEqualTo("TP-abc");
    assertThat(n.reference()).isEqualTo("PAY-2026-000001");
    assertThat(n.statut()).isEqualTo(NotificationVerifiee.Statut.PAYE);
    assertThat(n.montantGnf()).isEqualTo(500_000L);
  }

  @Test
  void mauvaisMarchandMauvaiseSignatureOuHorodatageAncienRefuses() {
    String sig = SignatureTresorPay.signer(SECRET, H, VALIDE);
    assertThat(verificateur.verifier(VALIDE, "AUTRE", H, sig, MAINTENANT)).isEmpty();
    assertThat(verificateur.verifier(VALIDE, "MARCHAND-TEST-0001", H, "00" + sig, MAINTENANT))
        .isEmpty();
    assertThat(
            verificateur.verifier(
                VALIDE, "MARCHAND-TEST-0001", H, sig, MAINTENANT.plusSeconds(301)))
        .isEmpty();
    assertThat(verificateur.verifier(VALIDE, null, null, null, MAINTENANT)).isEmpty();
  }

  @Test
  void contenuInvalideRefuseMemeAvecUneSignatureExacte() {
    for (String contenu :
        new String[] {
          "pas du json",
          "{\"transactionId\":\"TP-1\",\"reference\":\"PAY-1\",\"statut\":\"PAYE\",\"montantGnf\":1}",
          "{\"transactionId\":\"TP-1\",\"reference\":\"PAY-2026-000001\",\"statut\":\"INCONNU\",\"montantGnf\":1}",
          "{\"transactionId\":\"TP-1\",\"reference\":\"PAY-2026-000001\",\"statut\":\"PAYE\",\"montantGnf\":0}",
          "{\"transactionId\":\"TP-1\",\"reference\":\"PAY-2026-000001\",\"statut\":\"PAYE\",\"montantGnf\":1.5}",
          "{\"transactionId\":\"\",\"reference\":\"PAY-2026-000001\",\"statut\":\"PAYE\",\"montantGnf\":1}"
        }) {
      byte[] c = corps(contenu);
      assertThat(
              verificateur.verifier(
                  c, "MARCHAND-TEST-0001", H, SignatureTresorPay.signer(SECRET, H, c), MAINTENANT))
          .as(contenu)
          .isEmpty();
    }
  }

  @Test
  void secretAbsentRefuseTout() {
    VerificateurNotification sansSecret =
        new VerificateurNotification(
            "", "MARCHAND-TEST-0001", Duration.ofMinutes(5), JsonMapper.builder().build());
    assertThat(
            sansSecret.verifier(
                VALIDE,
                "MARCHAND-TEST-0001",
                H,
                SignatureTresorPay.signer("x", H, VALIDE),
                MAINTENANT))
        .isEmpty();
  }
}
