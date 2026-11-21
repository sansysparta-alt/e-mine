package gn.gov.mines.emine.simulateur.tresorpay;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/**
 * Formats français utilisés dans les pages ({@code ${@formats.gnf(...)}}) : mêmes règles que {@code
 * packages/ui/src/format.ts} (espace fine insécable entre milliers, heure de Conakry).
 */
@Component("formats")
public class Formats {

  private static final ZoneId CONAKRY = ZoneId.of("Africa/Conakry");
  private static final DateTimeFormatter HEURE =
      DateTimeFormatter.ofPattern("HH:mm").withZone(CONAKRY);
  private static final DateTimeFormatter DATE_HEURE =
      DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm").withZone(CONAKRY);

  /** {@code 1500000} → {@code 1 500 000 GNF}. */
  public String gnf(long montant) {
    String chiffres = Long.toString(Math.abs(montant));
    StringBuilder sb = new StringBuilder();
    int premier = chiffres.length() % 3;
    for (int i = 0; i < chiffres.length(); i++) {
      if (i > 0 && (i - premier) % 3 == 0) {
        sb.append(' ');
      }
      sb.append(chiffres.charAt(i));
    }
    return (montant < 0 ? "-" : "") + sb + " GNF";
  }

  public String heure(Instant instant) {
    return HEURE.format(instant);
  }

  public String dateHeure(Instant instant) {
    return DATE_HEURE.format(instant);
  }
}
