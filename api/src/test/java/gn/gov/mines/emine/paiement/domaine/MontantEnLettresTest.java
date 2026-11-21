package gn.gov.mines.emine.paiement.domaine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Montant en lettres du reçu : règles de l'orthographe française traditionnelle. */
class MontantEnLettresTest {

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "1|un",
        "16|seize",
        "17|dix-sept",
        "21|vingt et un",
        "32|trente-deux",
        "70|soixante-dix",
        "71|soixante et onze",
        "77|soixante-dix-sept",
        "80|quatre-vingts",
        "81|quatre-vingt-un",
        "90|quatre-vingt-dix",
        "91|quatre-vingt-onze",
        "99|quatre-vingt-dix-neuf",
        "100|cent",
        "101|cent un",
        "200|deux cents",
        "201|deux cent un",
        "280|deux cent quatre-vingts",
        "1000|mille",
        "1001|mille un",
        "2000|deux mille",
        "80000|quatre-vingt mille",
        "200000|deux cent mille",
        "1000000|un million",
        "2000000|deux millions",
        "200000000|deux cents millions",
        "1000000000|un milliard",
        "3000000000|trois milliards"
      })
  void nombres(long n, String attendu) {
    assertThat(MontantEnLettres.nombre(n)).isEqualTo(attendu);
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "1|Un franc guinéen",
        "200000|Deux cent mille francs guinéens",
        "500000|Cinq cent mille francs guinéens",
        "1000000|Un million de francs guinéens",
        "1500000|Un million cinq cent mille francs guinéens",
        "5000000|Cinq millions de francs guinéens",
        "15000000|Quinze millions de francs guinéens",
        "4999000|Quatre millions neuf cent quatre-vingt-dix-neuf mille francs guinéens"
      })
  void montantsDuBareme(long n, String attendu) {
    assertThat(MontantEnLettres.gnf(n)).isEqualTo(attendu);
  }

  @Test
  void horsLimitesRefuse() {
    assertThatThrownBy(() -> MontantEnLettres.gnf(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> MontantEnLettres.gnf(1_000_000_000_000L))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
