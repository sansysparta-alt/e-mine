package gn.gov.mines.emine.paiement.domaine;

import java.util.Objects;

/**
 * Notification TrésorPay dont la signature HMAC, le marchand et l'horodatage ont été vérifiés
 * (contrat §2 et §4). Elle ne peut être créée que par {@link VerificateurNotification}
 * (constructeur réservé au paquet) : la détenir prouve la vérification. C'est la condition pour
 * activer le contexte « système » qui traite la notification.
 */
public final class NotificationVerifiee {

  /** Résultat annoncé par TrésorPay. */
  public enum Statut {
    PAYE,
    REFUSE,
    ANNULE
  }

  private final String transactionId;
  private final String reference;
  private final Statut statut;
  private final long montantGnf;
  private final String corpsBrut;

  NotificationVerifiee(
      String transactionId, String reference, Statut statut, long montantGnf, String corpsBrut) {
    this.transactionId = Objects.requireNonNull(transactionId);
    this.reference = Objects.requireNonNull(reference);
    this.statut = Objects.requireNonNull(statut);
    this.montantGnf = montantGnf;
    this.corpsBrut = Objects.requireNonNull(corpsBrut);
  }

  /** Identifiant de transaction TrésorPay : clé d'idempotence et d'anti-rejeu. */
  public String transactionId() {
    return transactionId;
  }

  /** Référence e-mine {@code PAY-AAAA-NNNNNN}. */
  public String reference() {
    return reference;
  }

  public Statut statut() {
    return statut;
  }

  /** Montant annoncé, en GNF entiers. */
  public long montantGnf() {
    return montantGnf;
  }

  /** Corps JSON reçu, conservé dans le journal des événements. */
  public String corpsBrut() {
    return corpsBrut;
  }
}
