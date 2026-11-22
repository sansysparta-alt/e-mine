package gn.gov.mines.emine.notification.application;

import gn.gov.mines.emine.notification.domaine.CourrielGateway;
import gn.gov.mines.emine.notification.domaine.Message;
import gn.gov.mines.emine.notification.domaine.NotificationAEnvoyer;
import gn.gov.mines.emine.notification.domaine.RegleEssais;
import gn.gov.mines.emine.notification.domaine.SmsGateway;
import gn.gov.mines.emine.notification.infrastructure.MiseEnPageCourriel;
import gn.gov.mines.emine.notification.infrastructure.NotificationRepository;
import gn.gov.mines.emine.notification.infrastructure.RedacteurNotifications;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Envoi des notifications de la boîte d'envoi (US-07.1). Pour chaque lot :
 *
 * <ol>
 *   <li>une courte transaction prend le lot (essai compté, prochain essai reporté) ;
 *   <li>chaque message est rédigé et envoyé <em>hors transaction</em> (aucune connexion retenue
 *       pendant un appel réseau) ;
 *   <li>une transaction par message enregistre le résultat : envoyée, nouvel essai à 1, 5 puis 15
 *       min, ou « echec » après le 4e essai, avec une alerte à l'Administrateur Technique.
 * </ol>
 *
 * Garantie « au moins une fois » : une instance arrêtée entre l'envoi et l'enregistrement renverra
 * le message au prochain essai ; rien n'est perdu.
 */
@Service
public class ServiceEnvoiNotifications {

  /** Taille d'un lot. */
  static final int TAILLE_LOT = 50;

  /** Lots au plus par passage de la tâche (le reste attend le passage suivant). */
  static final int LOTS_MAX = 20;

  private static final Logger LOG = LoggerFactory.getLogger(ServiceEnvoiNotifications.class);

  private final NotificationRepository notifications;
  private final RedacteurNotifications redacteur;
  private final MiseEnPageCourriel miseEnPage;
  private final CourrielGateway courriel;
  private final SmsGateway sms;
  private final TransactionTemplate transaction;

  ServiceEnvoiNotifications(
      NotificationRepository notifications,
      RedacteurNotifications redacteur,
      MiseEnPageCourriel miseEnPage,
      CourrielGateway courriel,
      SmsGateway sms,
      PlatformTransactionManager transactions) {
    this.notifications = notifications;
    this.redacteur = redacteur;
    this.miseEnPage = miseEnPage;
    this.courriel = courriel;
    this.sms = sms;
    this.transaction = new TransactionTemplate(transactions);
  }

  /** Envoie les notifications dues ; renvoie le nombre de messages envoyés. */
  public int envoyerDues() {
    int envoyees = 0;
    for (int i = 0; i < LOTS_MAX; i++) {
      List<NotificationAEnvoyer> lot =
          transaction.execute(s -> notifications.prendreLot(TAILLE_LOT));
      if (lot == null || lot.isEmpty()) {
        break;
      }
      for (NotificationAEnvoyer n : lot) {
        if (envoyer(n)) {
          envoyees++;
        }
      }
    }
    return envoyees;
  }

  private boolean envoyer(NotificationAEnvoyer n) {
    String erreur = null;
    try {
      Message message = redacteur.rediger(n);
      if ("sms".equals(n.canal())) {
        sms.envoyer(n.telephone(), message.sms());
      } else {
        courriel.envoyer(
            n.email(),
            message.sujet(),
            miseEnPage.texte(message, n.langue()),
            miseEnPage.html(message, n.langue()));
      }
    } catch (RuntimeException e) {
      erreur = e.getClass().getSimpleName() + " : " + e.getMessage();
    }
    String motif = erreur;
    String statut = transaction.execute(s -> notifications.enregistrerResultat(n.id(), motif));
    if ("echec".equals(statut)) {
      LOG.error(
          "ALERTE ADMINISTRATEUR TECHNIQUE : notification {} ({} {}) non envoyée après {} essais : {}",
          n.id(),
          n.canal(),
          n.modele(),
          RegleEssais.ESSAIS_MAX,
          erreur);
    } else if (erreur != null) {
      LOG.warn(
          "Notification {} ({} {}) : essai {} en échec, nouvel essai prévu : {}",
          n.id(),
          n.canal(),
          n.modele(),
          n.tentative(),
          erreur);
    }
    return erreur == null;
  }
}
