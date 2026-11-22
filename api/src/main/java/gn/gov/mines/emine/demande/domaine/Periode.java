package gn.gov.mines.emine.demande.domaine;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Optional;

/**
 * Période des indicateurs de pilotage (correctifs ergonomie) : mois en cours par défaut, 30
 * derniers jours ou année en cours, en jours calendaires à l'heure de Conakry. Les dossiers en
 * retard et le travail à faire sont, eux, toujours « à ce jour ».
 */
public enum Periode {
  MOIS("mois"),
  TRENTE_JOURS("30-jours"),
  ANNEE("annee");

  /** Fuseau des dates affichées et des jours calendaires. */
  public static final ZoneId FUSEAU = ZoneId.of("Africa/Conakry");

  private final String cle;

  Periode(String cle) {
    this.cle = cle;
  }

  public String cle() {
    return cle;
  }

  public static Optional<Periode> depuis(String cle) {
    return Arrays.stream(values()).filter(p -> p.cle.equals(cle)).findFirst();
  }

  /** Début de la période (minuit, heure de Conakry), la période finissant maintenant. */
  public Instant debut(Instant maintenant) {
    LocalDate jour = LocalDate.ofInstant(maintenant, FUSEAU);
    LocalDate premier =
        switch (this) {
          case MOIS -> jour.withDayOfMonth(1);
          case TRENTE_JOURS -> jour.minusDays(29);
          case ANNEE -> jour.withDayOfYear(1);
        };
    return premier.atStartOfDay(FUSEAU).toInstant();
  }

  /** Début du jour calendaire (minuit, heure de Conakry). */
  public static Instant debutDuJour(Instant maintenant) {
    return LocalDate.ofInstant(maintenant, FUSEAU).atStartOfDay(FUSEAU).toInstant();
  }
}
