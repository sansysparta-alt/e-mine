package gn.gov.mines.emine.notification.domaine;

/**
 * Port d'envoi des SMS (D5) : simulé en développement et en démonstration, remplacé par la
 * passerelle de l'opérateur en production. Une exception signale un échec (nouvel essai).
 */
public interface SmsGateway {

  /**
   * Envoie un SMS.
   *
   * @param telephone numéro au format {@code +224XXXXXXXXX}
   * @param texte texte du message (160 caractères au plus)
   */
  void envoyer(String telephone, String texte);
}
