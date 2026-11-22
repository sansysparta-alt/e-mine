package gn.gov.mines.emine.notification.domaine;

/**
 * Reprise sur échec des notifications (US-07.1), alignée sur le cahier des charges (« 3 tentatives
 * à 1, 5 et 15 min puis alerte à l'Administrateur Technique ; aucune écriture perdue ») : un
 * premier essai, puis 3 reprises. Le calcul est fait par la base ({@code
 * fn_notifications_a_envoyer}, {@code fn_notification_resultat}) ; cette classe le documente.
 */
public final class RegleEssais {

  /** Nombre total d'essais : le premier envoi et 3 reprises. */
  public static final int ESSAIS_MAX = 4;

  /** Délais avant chaque reprise, en minutes. */
  public static final int[] DELAIS_REPRISE_MINUTES = {1, 5, 15};

  /** Longueur maximale d'un SMS (un seul segment). */
  public static final int LONGUEUR_SMS_MAX = 160;

  private RegleEssais() {}
}
