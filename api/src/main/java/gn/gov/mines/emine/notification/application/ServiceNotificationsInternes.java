package gn.gov.mines.emine.notification.application;

import gn.gov.mines.emine.notification.domaine.Message;
import gn.gov.mines.emine.notification.domaine.NotificationAEnvoyer;
import gn.gov.mines.emine.notification.domaine.NotificationInterne;
import gn.gov.mines.emine.notification.infrastructure.NotificationRepository;
import gn.gov.mines.emine.notification.infrastructure.RedacteurNotifications;
import gn.gov.mines.emine.shared.erreur.CodeErreur;
import gn.gov.mines.emine.shared.erreur.ErreurMetier;
import gn.gov.mines.emine.shared.pagination.PageResultat;
import gn.gov.mines.emine.shared.pagination.Pagination;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Centre de notifications de l'application (US-07.2) : chaque utilisateur consulte ses
 * notifications (RLS), rédigées dans la langue de l'interface, et les marque comme lues.
 */
@Service
public class ServiceNotificationsInternes {

  private final NotificationRepository notifications;
  private final RedacteurNotifications redacteur;
  private final Clock horloge;

  ServiceNotificationsInternes(
      NotificationRepository notifications, RedacteurNotifications redacteur, Clock horloge) {
    this.notifications = notifications;
    this.redacteur = redacteur;
    this.horloge = horloge;
  }

  /**
   * Notification rédigée.
   *
   * @param titre objet du message
   * @param texte texte du message (paragraphes séparés par une ligne vide)
   */
  public record NotificationRedigee(NotificationInterne notification, String titre, String texte) {}

  @Transactional(readOnly = true)
  public PageResultat<NotificationRedigee> lister(Pagination pagination, String langue) {
    return notifications.lister(pagination).map(n -> rediger(n, langue));
  }

  @Transactional(readOnly = true)
  public long nonLues() {
    return notifications.nonLues();
  }

  @Transactional
  public void marquerLue(long id) {
    if (!notifications.marquerLue(id, Instant.now(horloge))) {
      throw new ErreurMetier(CodeErreur.SYS_002, Map.of("id", id));
    }
  }

  @Transactional
  public int marquerToutesLues() {
    return notifications.marquerToutesLues(Instant.now(horloge));
  }

  private NotificationRedigee rediger(NotificationInterne n, String langue) {
    Message m =
        redacteur.redigerPourApplication(
            new NotificationAEnvoyer(
                n.id(),
                "interne",
                n.modele(),
                n.contenu(),
                0,
                null,
                null,
                langue,
                "",
                n.demandeId(),
                n.numeroDemande()));
    return new NotificationRedigee(n, m.sujet(), m.texte());
  }
}
