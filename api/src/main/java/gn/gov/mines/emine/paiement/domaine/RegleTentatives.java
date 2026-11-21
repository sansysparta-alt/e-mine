package gn.gov.mines.emine.paiement.domaine;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Limite des paiements en ligne (US-04.3) : 3 tentatives TrésorPay par objet et par jour, le jour
 * s'entendant à l'heure de Conakry.
 */
public final class RegleTentatives {

  public static final int MAX_PAR_JOUR = 3;
  public static final ZoneId CONAKRY = ZoneId.of("Africa/Conakry");

  private RegleTentatives() {}

  /** Début du jour en cours (minuit, heure de Conakry). */
  public static Instant debutDuJour(Instant maintenant) {
    return LocalDate.ofInstant(maintenant, CONAKRY).atStartOfDay(CONAKRY).toInstant();
  }

  /** Début du jour suivant : moment où les tentatives sont à nouveau possibles. */
  public static Instant debutDuJourSuivant(Instant maintenant) {
    return LocalDate.ofInstant(maintenant, CONAKRY).plusDays(1).atStartOfDay(CONAKRY).toInstant();
  }

  /** Tentatives restantes aujourd'hui, jamais négatif. */
  public static int restantes(long faitesAujourdhui) {
    return (int) Math.max(0, MAX_PAR_JOUR - faitesAujourdhui);
  }
}
