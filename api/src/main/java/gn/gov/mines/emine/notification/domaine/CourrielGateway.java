package gn.gov.mines.emine.notification.domaine;

/**
 * Port d'envoi des e-mails : SMTP (Mailpit en développement). Une exception signale un échec
 * (nouvel essai).
 */
public interface CourrielGateway {

  /**
   * Envoie un e-mail en texte brut et en HTML.
   *
   * @param destinataire adresse du destinataire
   * @param sujet objet
   * @param texte version texte brut
   * @param html version HTML
   */
  void envoyer(String destinataire, String sujet, String texte, String html);
}
