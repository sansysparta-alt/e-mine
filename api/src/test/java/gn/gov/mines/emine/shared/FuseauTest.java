package gn.gov.mines.emine.shared;

import static org.assertj.core.api.Assertions.assertThat;

import gn.gov.mines.emine.support.TestIntegration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Les jours calendaires se comptent à l'heure de Conakry, quel que soit le fuseau du serveur
 * (défaut des tests de délais « vers minuit » sur un poste à Paris : le pilote PostgreSQL
 * transmettait le fuseau de la JVM, et {@code current_date} passait au lendemain à 23 h UTC).
 */
class FuseauTest extends TestIntegration {

  @Autowired JdbcTemplate jdbc;

  @Test
  void laSessionDeLApplicationEstAlHeureDeConakry() {
    assertThat(jdbc.queryForObject("SHOW TIME ZONE", String.class)).isEqualTo("Africa/Conakry");
    // 23 h 30 à Conakry le 24/11 : la date du jour reste le 24/11, pas celle de Paris (25/11).
    assertThat(
            jdbc.queryForObject(
                "SELECT ('2026-11-24 23:30:00+00'::timestamptz)::date::text", String.class))
        .isEqualTo("2026-11-24");
  }
}
