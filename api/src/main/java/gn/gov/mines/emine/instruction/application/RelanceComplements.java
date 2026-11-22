package gn.gov.mines.emine.instruction.application;

import com.github.kagkarlsson.scheduler.task.helper.RecurringTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import com.github.kagkarlsson.scheduler.task.schedule.FixedDelay;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Relance automatique des demandeurs à J+3 d'un complément sans réponse (US-05.2), tâche récurrente
 * de db-scheduler (US-07.1) : une seule instance de l'API l'exécute à chaque passage, et la
 * fonction de la base reste idempotente (une relance par complément). La notification écrite est
 * envoyée par l'outbox des notifications.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    name = "emine.complements.relance.active",
    havingValue = "true",
    matchIfMissing = true)
class RelanceComplements {

  private static final Logger LOG = LoggerFactory.getLogger(RelanceComplements.class);

  @Bean
  RecurringTask<Void> relanceComplements(
      ServiceComplements service,
      @Value("${emine.complements.relance.intervalle:PT1H}") Duration intervalle) {
    return Tasks.recurring("relance-complements", FixedDelay.of(intervalle))
        .execute(
            (instance, contexte) -> {
              int n = service.relancerAutomatiquement();
              if (n > 0) {
                LOG.info("Compléments : {} demandeur(s) relancé(s)", n);
              }
            });
  }
}
