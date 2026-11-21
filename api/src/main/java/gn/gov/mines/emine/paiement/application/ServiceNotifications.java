package gn.gov.mines.emine.paiement.application;

import gn.gov.mines.emine.paiement.domaine.NotificationVerifiee;
import gn.gov.mines.emine.paiement.domaine.Paiement;
import gn.gov.mines.emine.paiement.domaine.VerificateurNotification;
import gn.gov.mines.emine.paiement.infrastructure.PaiementRepository;
import gn.gov.mines.emine.shared.erreur.CodeErreur;
import gn.gov.mines.emine.shared.erreur.ErreurMetier;
import gn.gov.mines.emine.shared.jooq.enums.StatutPaiement;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Notification TrésorPay (US-04.4, contrat §4) : seule preuve d'un paiement.
 *
 * <ul>
 *   <li>signature, marchand ou horodatage invalide → journalisée ({@code signature_valide =
 *       false}), alerte dans les journaux, PAY-004 (401), rien n'est modifié ;
 *   <li>même identifiant de transaction → 200 sans retraitement (idempotence) ;
 *   <li>montant égal → {@code paye} (reçu attribué par la base), montant différent → {@code
 *       a_verifier}, refus → {@code echoue}, abandon → {@code annule}.
 * </ul>
 *
 * Le traitement d'une notification vérifiée s'exécute dans le contexte système ({@link
 * ContexteSystemeNotification}), le temps d'une seule transaction.
 */
@Service
public class ServiceNotifications {

  private static final Logger LOG = LoggerFactory.getLogger(ServiceNotifications.class);
  private static final int TAILLE_MAX_JOURNAL = 4_000;

  /** Issue du traitement, traduite en réponse HTTP par le contrôleur. */
  public enum Issue {
    /** Appliquée au paiement. */
    TRAITEE,
    /** Même transaction déjà reçue : rien n'est refait. */
    DEJA_TRAITEE,
    /** Paiement déjà clos (annulé, expiré…) : journalisée, non appliquée, à examiner. */
    IGNOREE,
    /** Référence inconnue : journalisée. */
    REFERENCE_INCONNUE
  }

  private final PaiementRepository paiements;
  private final ContexteSystemeNotification contexteSysteme;
  private final VerificateurNotification verificateur;
  private final TransactionTemplate transaction;
  private final JsonMapper json;
  private final Clock horloge;

  public ServiceNotifications(
      PaiementRepository paiements,
      ContexteSystemeNotification contexteSysteme,
      PlatformTransactionManager transactions,
      JsonMapper json,
      Clock horloge,
      @Value("${emine.tresorpay.secret-hmac:}") String secret,
      @Value("${emine.tresorpay.marchand-id:}") String marchand,
      @Value("${emine.tresorpay.tolerance-horodatage:5m}") Duration tolerance) {
    this.paiements = paiements;
    this.contexteSysteme = contexteSysteme;
    this.transaction = new TransactionTemplate(transactions);
    this.json = json;
    this.horloge = horloge;
    this.verificateur = new VerificateurNotification(secret, marchand, tolerance, json);
  }

  public Issue recevoir(
      byte[] corps, String marchand, String horodatage, String signature, String ip) {
    Optional<NotificationVerifiee> verifiee =
        verificateur.verifier(corps, marchand, horodatage, signature, horloge.instant());
    if (verifiee.isEmpty()) {
      journaliserRefus(corps, ip);
      LOG.warn(
          "ALERTE SÉCURITÉ : notification TrésorPay refusée (signature, marchand ou horodatage"
              + " invalide), IP {}",
          ip);
      throw new ErreurMetier(CodeErreur.PAY_004);
    }
    NotificationVerifiee n = verifiee.get();
    return contexteSysteme.executer(n, ip, () -> traiter(n));
  }

  /** Appelé en contexte système, dans la transaction de la notification. */
  private Issue traiter(NotificationVerifiee n) {
    Optional<Long> evenement =
        paiements.enregistrerNotification(n.transactionId(), n.reference(), n.corpsBrut());
    if (evenement.isEmpty()) {
      LOG.info("Notification {} déjà reçue : ignorée (idempotence)", n.transactionId());
      return Issue.DEJA_TRAITEE;
    }
    Optional<Paiement> trouve = paiements.verrouillerParReference(n.reference());
    if (trouve.isEmpty()) {
      LOG.warn("Notification {} pour une référence inconnue {}", n.transactionId(), n.reference());
      return Issue.REFERENCE_INCONNUE;
    }
    Paiement p = trouve.get();
    if (p.statut() != StatutPaiement.en_attente) {
      paiements.marquerEvenement(evenement.get(), p.id(), false);
      LOG.warn(
          "Notification {} sur le paiement {} au statut {} : non appliquée, à examiner",
          n.transactionId(),
          p.reference(),
          p.statut().getLiteral());
      return Issue.IGNOREE;
    }
    switch (n.statut()) {
      case PAYE -> {
        boolean montantExact = n.montantGnf() == p.montantGnf();
        paiements.appliquerResultat(
            p.id(),
            montantExact ? StatutPaiement.paye : StatutPaiement.a_verifier,
            n.transactionId(),
            n.montantGnf());
        if (!montantExact) {
          LOG.warn(
              "Paiement {} : montant reçu {} GNF au lieu de {} GNF, à vérifier",
              p.reference(),
              n.montantGnf(),
              p.montantGnf());
        }
      }
      case REFUSE ->
          paiements.appliquerResultat(p.id(), StatutPaiement.echoue, n.transactionId(), null);
      case ANNULE ->
          paiements.appliquerResultat(p.id(), StatutPaiement.annule, n.transactionId(), null);
    }
    paiements.marquerEvenement(evenement.get(), p.id(), true);
    return Issue.TRAITEE;
  }

  /** Journal d'une notification refusée, dans sa propre transaction (contexte ordinaire). */
  private void journaliserRefus(byte[] corps, String ip) {
    String texte = new String(corps == null ? new byte[0] : corps, StandardCharsets.UTF_8);
    if (texte.length() > TAILLE_MAX_JOURNAL) {
      texte = texte.substring(0, TAILLE_MAX_JOURNAL);
    }
    String payload = json.writeValueAsString(Map.of("brut", texte, "ip", ip == null ? "" : ip));
    transaction.executeWithoutResult(s -> paiements.journaliserNotificationRefusee(payload));
  }
}
