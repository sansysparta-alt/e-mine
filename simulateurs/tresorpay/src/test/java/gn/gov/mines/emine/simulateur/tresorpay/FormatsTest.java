package gn.gov.mines.emine.simulateur.tresorpay;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Formats français des pages (mêmes règles que {@code packages/ui/src/format.ts}). */
class FormatsTest {

  private final Formats formats = new Formats();

  @Test
  void montantsEnGnfAvecEspacesFines() {
    assertThat(formats.gnf(500)).isEqualTo("500 GNF");
    assertThat(formats.gnf(1_500)).isEqualTo("1 500 GNF");
    assertThat(formats.gnf(500_000)).isEqualTo("500 000 GNF");
    assertThat(formats.gnf(12_500_000)).isEqualTo("12 500 000 GNF");
  }

  @Test
  void heuresDeConakry() {
    Instant instant = Instant.parse("2026-11-21T09:05:00Z");
    assertThat(formats.heure(instant)).isEqualTo("09:05");
    assertThat(formats.dateHeure(instant)).isEqualTo("21/11/2026 à 09:05");
  }
}
