package gn.gov.mines.emine.notification.application;

import com.github.kagkarlsson.scheduler.task.helper.RecurringTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import com.github.kagkarlsson.scheduler.task.schedule.FixedDelay;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tâche planifiée d'envoi des notifications (US-07.1), exécutée par db-scheduler : une seule
 * instance de l'API la prend à chaque passage.
 */
@Configuration(proxyBeanMethods = false)
class TacheEnvoiNotifications {

  private static final Logger LOG = LoggerFactory.getLogger(TacheEnvoiNotifications.class);

  @Bean
  RecurringTask<Void> envoiNotifications(
      ServiceEnvoiNotifications service,
      @Value("${emine.notifications.intervalle:PT15S}") Duration intervalle) {
    return Tasks.recurring("envoi-notifications", FixedDelay.of(intervalle))
        .execute(
            (instance, contexte) -> {
              int n = service.envoyerDues();
              if (n > 0) {
                LOG.info("Notifications : {} message(s) envoyé(s)", n);
              }
            });
  }
}
