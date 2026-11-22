package gn.gov.mines.emine.shared.planification;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.task.helper.RecurringTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import com.github.kagkarlsson.scheduler.task.schedule.FixedDelay;
import gn.gov.mines.emine.support.BaseDeDonneesDeTest;
import gn.gov.mines.emine.support.TestIntegration;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * US-07.1 : db-scheduler fonctionne sur la base réelle avec le rôle applicatif {@code emine_app}
 * (table {@code scheduled_tasks} de V15, droits de lecture, écriture et suppression) et exécute une
 * tâche récurrente, comme l'envoi des notifications.
 */
class PlanificateurTest extends TestIntegration {

  @Test
  void uneTacheRecurrenteEstExecuteeParLePlanificateurAvecLeRoleApplicatif() throws Exception {
    PGSimpleDataSource source = new PGSimpleDataSource();
    source.setUrl(BaseDeDonneesDeTest.demarrer().getJdbcUrl());
    source.setUser("emine_app");
    source.setPassword(BaseDeDonneesDeTest.MDP_APP);

    CountDownLatch executions = new CountDownLatch(2);
    RecurringTask<Void> tache =
        Tasks.recurring("tache-de-test", FixedDelay.of(Duration.ofMillis(200)))
            .execute((instance, contexte) -> executions.countDown());
    Scheduler scheduler = ConfigurationPlanificateur.creer(source, List.of(tache));
    scheduler.start();
    try {
      assertThat(executions.await(20, TimeUnit.SECONDS)).isTrue();
    } finally {
      scheduler.stop();
    }
  }
}
