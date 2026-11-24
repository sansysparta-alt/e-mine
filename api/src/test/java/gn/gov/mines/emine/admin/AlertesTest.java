package gn.gov.mines.emine.admin;

import static org.assertj.core.api.Assertions.assertThat;

import gn.gov.mines.emine.admin.domaine.Supervision;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Alertes de la cloche de l'administrateur technique : un service indisponible d'abord, puis les
 * comptes suspendus et les invitations jamais activées ; rien quand tout va bien.
 */
class AlertesTest {

  @Test
  void unServiceIndisponibleEtDesComptesBloquesSontSignalesDuPlusGraveAuMoinsGrave() {
    var alertes =
        Supervision.alertes(
            new Supervision.Comptes(10, 2, 1, 5),
            List.of(
                new Supervision.Service("base", true), new Supervision.Service("stockage", false)));
    assertThat(alertes)
        .containsExactly(
            new Supervision.Alerte("service", "stockage", 1),
            new Supervision.Alerte("suspendus", null, 2),
            new Supervision.Alerte("invites", null, 1));
  }

  @Test
  void aucuneAlerteQuandToutVaBien() {
    assertThat(
            Supervision.alertes(
                new Supervision.Comptes(10, 0, 0, 5),
                List.of(new Supervision.Service("base", true))))
        .isEmpty();
  }
}
