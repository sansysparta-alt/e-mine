package gn.gov.mines.emine.notification.domaine;

/**
 * Canaux d'un événement de notification (US-02.7) : SMS, e-mail, centre de notifications de
 * l'application. Au moins un canal reste actif : l'usager sait toujours où en est son dossier.
 */
public record CanauxEvenement(
    String modele,
    String libelleFr,
    String libelleEn,
    boolean sms,
    boolean email,
    boolean interne) {

  /** Au moins un canal actif. */
  public boolean auMoinsUnCanal() {
    return sms || email || interne;
  }
}
