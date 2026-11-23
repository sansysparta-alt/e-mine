package gn.gov.mines.emine.notification.infrastructure;

import gn.gov.mines.emine.notification.domaine.SmsGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * SMS simulés (D5, {@code SMS_MODE=simulateur}) : aucun SMS réel, donc aucun coût. Chaque SMS est
 * déposé dans Mailpit à l'adresse fictive {@code <numéro>@sms.test} (objet « SMS +224… »), pour
 * qu'on le voie en démonstration à côté des e-mails, et tracé (numéro masqué) dans le journal.
 */
@Component
@ConditionalOnProperty(
    name = "emine.notifications.sms-mode",
    havingValue = "simulateur",
    matchIfMissing = true)
class SmsSimuleGateway implements SmsGateway {

  private static final Logger LOG = LoggerFactory.getLogger(SmsSimuleGateway.class);

  private final JavaMailSender smtp;

  SmsSimuleGateway(JavaMailSender smtp) {
    this.smtp = smtp;
  }

  @Override
  public void envoyer(String telephone, String texte) {
    SimpleMailMessage m = new SimpleMailMessage();
    m.setFrom("simulateur-sms@sms.test");
    m.setTo(telephone.replace("+", "") + "@sms.test");
    m.setSubject("SMS " + telephone);
    m.setText(texte);
    smtp.send(m);
    LOG.info("SMS simulé vers {} ({} caractères)", masquer(telephone), texte.length());
  }

  /** Numéro masqué dans le journal : seuls les 3 derniers chiffres restent lisibles. */
  static String masquer(String telephone) {
    int n = telephone.length();
    return n <= 3 ? "***" : "*".repeat(n - 3) + telephone.substring(n - 3);
  }
}
