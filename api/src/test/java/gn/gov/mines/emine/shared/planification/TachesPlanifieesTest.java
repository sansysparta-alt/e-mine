package gn.gov.mines.emine.shared.planification;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.kagkarlsson.scheduler.task.helper.RecurringTask;
import gn.gov.mines.emine.support.TestIntegration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * US-07.1 : le contexte démarre avec toutes les tâches déclarées comme en production (deux tâches
 * de même nom de bean empêchaient l'API de démarrer) et chaque module déclare sa tâche récurrente
 * au planificateur. Le planificateur lui-même reste arrêté ici (il enverrait les notifications des
 * autres tests) ; son fonctionnement est prouvé par {@link PlanificateurTest}.
 */
class TachesPlanifieesTest extends TestIntegration {

  @Autowired List<RecurringTask<?>> taches;

  @Test
  void leContexteDemarreAvecToutesLesTachesDeclarees() {
    assertThat(taches.stream().map(t -> t.getName()))
        .contains("envoi-notifications", "relance-complements");
  }
}
