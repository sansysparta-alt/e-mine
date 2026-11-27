package gn.gov.mines.emine.paiement.application;

import gn.gov.mines.emine.paiement.domaine.NotificationVerifiee;
import gn.gov.mines.emine.shared.session.ContexteSession;
import gn.gov.mines.emine.shared.session.FournisseurContexteSession;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Contexte « système » (US-04.4, décision du porteur du projet) : la notification TrésorPay arrive
 * sans utilisateur connecté, et la RLS cacherait le paiement. Garde-fous :
 *
 * <ol>
 *   <li>activé seulement avec une {@link NotificationVerifiee}, qui n'existe qu'après vérification
 *       HMAC, marchand et horodatage ;
 *   <li>limité à une transaction <em>nouvelle</em> ouverte ici ({@code REQUIRES_NEW}) : refusé si
 *       une transaction est déjà en cours, et retiré dans un {@code finally} ;
 *   <li>propre au fil d'exécution (ThreadLocal), jamais imbriqué ;
 *   <li>accessible au seul paquet {@code paiement.application} (classe et méthode non publiques),
 *       vérifié aussi par un test d'architecture.
 * </ol>
 *
 * Contexte appliqué : {@code emine.interne = on}, aucun utilisateur ({@code emine.user_id} vide :
 * le journal d'audit l'attribue au système), IP de l'appelant.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class ContexteSystemeNotification implements FournisseurContexteSession {

  private static final ThreadLocal<ContexteSession> ACTIF = new ThreadLocal<>();

  private final TransactionTemplate transaction;

  ContexteSystemeNotification(PlatformTransactionManager transactions) {
    this.transaction = new TransactionTemplate(transactions);
    this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Override
  public ContexteSession contexteCourant() {
    return ACTIF.get();
  }

  /**
   * Exécute {@code traitement} dans une nouvelle transaction en contexte système, pour la tâche
   * planifiée d'interrogation de TrésorPay (US-04.5) : aucune requête extérieure n'y entre, les
   * résultats viennent de nos propres requêtes signées à la passerelle. Mêmes garde-fous.
   */
  <T> T executerTache(Supplier<T> traitement) {
    if (ACTIF.get() != null) {
      throw new IllegalStateException("Contexte système déjà actif : imbrication interdite");
    }
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException(
          "Contexte système refusé : une transaction est déjà en cours sur ce fil");
    }
    ACTIF.set(new ContexteSession(null, true, "tache-interrogation"));
    try {
      return transaction.execute(statut -> traitement.get());
    } finally {
      ACTIF.remove();
    }
  }

  /** Vrai pendant l'exécution d'un traitement de notification sur ce fil. */
  static boolean actif() {
    return ACTIF.get() != null;
  }

  /**
   * Exécute {@code traitement} dans une nouvelle transaction en contexte système.
   *
   * @param preuve notification vérifiée (preuve de la vérification HMAC)
   * @param ip adresse de l'appelant, pour le journal d'audit
   */
  <T> T executer(NotificationVerifiee preuve, String ip, Supplier<T> traitement) {
    Objects.requireNonNull(preuve, "Contexte système refusé : notification non vérifiée");
    if (ACTIF.get() != null) {
      throw new IllegalStateException("Contexte système déjà actif : imbrication interdite");
    }
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException(
          "Contexte système refusé : une transaction est déjà en cours sur ce fil");
    }
    ACTIF.set(new ContexteSession(null, true, ip));
    try {
      return transaction.execute(statut -> traitement.get());
    } finally {
      ACTIF.remove();
    }
  }
}
