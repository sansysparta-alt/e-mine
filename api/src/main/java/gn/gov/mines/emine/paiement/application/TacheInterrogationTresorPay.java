package gn.gov.mines.emine.paiement.application;

import com.github.kagkarlsson.scheduler.task.helper.RecurringTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import com.github.kagkarlsson.scheduler.task.schedule.FixedDelay;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tâche planifiée (db-scheduler) de l'interrogation de TrésorPay (US-04.5) : toutes les 30 s, une
 * seule instance de l'API prend chaque passage.
 */
@Configuration(proxyBeanMethods = false)
class TacheInterrogationTresorPay {

  @Bean
  RecurringTask<Void> interrogationTresorPay(
      ServiceInterrogationTresorPay service,
      @Value("${emine.tresorpay.intervalle-interrogation:PT30S}") Duration intervalle) {
    return Tasks.recurring("interrogation-tresorpay", FixedDelay.of(intervalle))
        .execute((instance, contexte) -> service.executer());
  }
}
