package gn.gov.mines.emine.notification.domaine;

/**
 * Canaux d'un événement de notification (US-02.7) : SMS, e-mail, centre de notifications de
 * l'application. Le centre de notifications (« Espace en ligne ») est toujours actif : l'usager
 * sait toujours où en est son dossier (V24) ; le SMS, payant, est réservé par défaut aux événements
 * où l'usager doit agir.
 */
public record CanauxEvenement(
    String modele,
    String libelleFr,
    String libelleEn,
    boolean sms,
    boolean email,
    boolean interne) {}
