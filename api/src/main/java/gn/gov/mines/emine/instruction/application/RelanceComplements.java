package gn.gov.mines.emine.instruction.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Relance automatique des demandeurs à J+3 d'un complément sans réponse (US-05.2). La fonction de
 * la base est idempotente (une relance par complément) : plusieurs instances de l'API peuvent
 * l'appeler sans doublon. db-scheduler arrivera avec l'outbox des notifications (US-07.1), qui
 * enverra réellement le message (journal {@code docs/02} §6).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
    name = "emine.complements.relance.active",
    havingValue = "true",
    matchIfMissing = true)
class RelanceComplements {

  private static final Logger LOG = LoggerFactory.getLogger(RelanceComplements.class);

  private final ServiceComplements service;

  RelanceComplements(ServiceComplements service) {
    this.service = service;
  }

  @Scheduled(
      initialDelayString = "${emine.complements.relance.delai-initial:PT1M}",
      fixedDelayString = "${emine.complements.relance.intervalle:PT1H}")
  void relancer() {
    int n = service.relancerAutomatiquement();
    if (n > 0) {
      LOG.info("Compléments : {} demandeur(s) relancé(s)", n);
    }
  }
}
