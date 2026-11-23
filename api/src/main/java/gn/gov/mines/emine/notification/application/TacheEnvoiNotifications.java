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
 * Tâches planifiées des notifications : envoi (US-07.1) et alerte J-2 des références de paiement
 * (US-07.2), exécutées par db-scheduler : une seule instance de l'API prend chaque passage.
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

  /** Alerte J-2 des références de paiement (virement) proches de l'expiration, chaque heure. */
  @Bean
  RecurringTask<Void> alerteReferencesExpirantes(ServiceEnvoiNotifications service) {
    return Tasks.recurring("alerte-references-expirantes", FixedDelay.of(Duration.ofHours(1)))
        .execute(
            (instance, contexte) -> {
              int n = service.alerterReferencesExpirantes();
              if (n > 0) {
                LOG.info("Références de paiement proches de l'expiration : {} alerte(s)", n);
              }
            });
  }
}
