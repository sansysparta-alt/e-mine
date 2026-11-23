package gn.gov.mines.emine.notification.infrastructure;

import gn.gov.mines.emine.notification.domaine.CourrielGateway;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** E-mails par SMTP (Mailpit en développement, relais du Ministère en production). */
@Component
class CourrielSmtpGateway implements CourrielGateway {

  private final JavaMailSender smtp;
  private final String expediteur;

  CourrielSmtpGateway(
      JavaMailSender smtp,
      @Value("${emine.notifications.expediteur:e-mine <ne-pas-repondre@e-mine.test>}")
          String expediteur) {
    this.smtp = smtp;
    this.expediteur = expediteur;
  }

  @Override
  public void envoyer(String destinataire, String sujet, String texte, String html) {
    MimeMessage message = smtp.createMimeMessage();
    try {
      MimeMessageHelper m = new MimeMessageHelper(message, true, "UTF-8");
      m.setFrom(expediteur);
      m.setTo(destinataire);
      m.setSubject(sujet);
      m.setText(texte, html);
    } catch (MessagingException e) {
      throw new MailSendException("E-mail invalide", e);
    }
    smtp.send(message);
  }
}
