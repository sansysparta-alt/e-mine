package gn.gov.mines.emine.shared.planification;

import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.task.helper.RecurringTask;
import java.time.Duration;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Planificateur partagé (db-scheduler, table {@code scheduled_tasks}, V15) : chaque module déclare
 * ses tâches récurrentes comme beans {@link RecurringTask} ; une seule instance de l'API exécute
 * chaque passage. Configuré à la main (le starter de db-scheduler vise Spring Boot 3). Désactivé
 * dans les tests d'intégration, qui appellent les services eux-mêmes.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    name = "emine.planification.active",
    havingValue = "true",
    matchIfMissing = true)
class ConfigurationPlanificateur {

  @Bean
  CyclePlanificateur planificateur(DataSource dataSource, ObjectProvider<RecurringTask<?>> taches) {
    return new CyclePlanificateur(creer(dataSource, taches.orderedStream().toList()));
  }

  /** Planificateur des tâches récurrentes données (aussi utilisé par le test du planificateur). */
  static Scheduler creer(DataSource dataSource, List<RecurringTask<?>> taches) {
    return Scheduler.create(dataSource)
        .startTasks(taches)
        .threads(2)
        .pollingInterval(Duration.ofSeconds(1))
        .heartbeatInterval(Duration.ofMinutes(1))
        .build();
  }

  /** Démarre le planificateur avec le contexte Spring et l'arrête proprement avant la base. */
  static final class CyclePlanificateur implements SmartLifecycle {

    private final Scheduler scheduler;
    private volatile boolean actif;

    CyclePlanificateur(Scheduler scheduler) {
      this.scheduler = scheduler;
    }

    @Override
    public void start() {
      scheduler.start();
      actif = true;
    }

    @Override
    public void stop() {
      scheduler.stop();
      actif = false;
    }

    @Override
    public boolean isRunning() {
      return actif;
    }
  }
}
