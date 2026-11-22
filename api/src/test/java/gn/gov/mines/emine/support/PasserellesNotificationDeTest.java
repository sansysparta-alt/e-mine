package gn.gov.mines.emine.support;

import gn.gov.mines.emine.notification.domaine.CourrielGateway;
import gn.gov.mines.emine.notification.domaine.SmsGateway;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Ports SMS et e-mail des tests (US-07.1) : enregistrent les messages au lieu de les envoyer, ou
 * simulent une panne. Importés par {@link TestIntegration} : aucun test n'envoie de message réel.
 */
@TestConfiguration
public class PasserellesNotificationDeTest {

  /** SMS « envoyés » : numéro, espace, texte. */
  public final List<String> sms = new CopyOnWriteArrayList<>();

  /** E-mails « envoyés » : destinataire, objet, texte brut et HTML, un par ligne. */
  public final List<String> courriels = new CopyOnWriteArrayList<>();

  /** Vrai : chaque envoi échoue (panne simulée). */
  public volatile boolean enPanne;

  public void reinitialiser() {
    sms.clear();
    courriels.clear();
    enPanne = false;
  }

  @Bean
  @Primary
  SmsGateway smsDeTest() {
    return (telephone, texte) -> {
      if (enPanne) {
        throw new IllegalStateException("Panne simulée de la passerelle SMS");
      }
      sms.add(telephone + " " + texte);
    };
  }

  @Bean
  @Primary
  CourrielGateway courrielDeTest() {
    return (destinataire, sujet, texte, html) -> {
      if (enPanne) {
        throw new IllegalStateException("Panne simulée du serveur SMTP");
      }
      courriels.add(destinataire + "\n" + sujet + "\n" + texte + "\n" + html);
    };
  }
}
