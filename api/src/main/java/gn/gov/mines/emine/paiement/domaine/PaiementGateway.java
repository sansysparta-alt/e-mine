package gn.gov.mines.emine.paiement.domaine;

import java.time.Instant;

/**
 * Port vers la passerelle de paiement en ligne (D5) : TrésorPay simulé en développement, connecteur
 * réel au lot 5. Contrat : {@code docs/contrats/tresorpay.md}.
 */
public interface PaiementGateway {

  /**
   * Ouvre une session de paiement de 15 minutes.
   *
   * @throws PasserelleIndisponible si la passerelle ne répond pas ou se déclare indisponible
   */
  SessionOuverte ouvrirSession(DemandeSession demande);

  /**
   * Demande d'ouverture de session.
   *
   * @param reference référence e-mine {@code PAY-AAAA-NNNNNN}
   * @param montantGnf montant du barème, en GNF entiers
   * @param libelle libellé affiché à l'usager sur la page de paiement
   * @param urlRetour adresse de retour du navigateur sur e-mine
   */
  record DemandeSession(String reference, long montantGnf, String libelle, String urlRetour) {}

  /** Session ouverte : le navigateur est redirigé vers {@code urlPaiement}. */
  record SessionOuverte(String sessionId, String urlPaiement, Instant expireLe) {}

  /**
   * Interroge la passerelle sur la transaction d'une référence (US-04.5), quand sa notification
   * n'est pas arrivée. Réponse signée comme une initiation ; seule une réponse {@code PAYE} vaut
   * paiement.
   *
   * @throws PasserelleIndisponible si la passerelle ne répond pas
   */
  EtatTransaction interroger(String reference);

  /** État d'une transaction chez la passerelle. */
  enum StatutTransaction {
    /** Session ouverte, l'usager n'a pas encore payé. */
    EN_COURS,
    PAYE,
    REFUSE,
    ANNULE,
    /** Session expirée sans paiement. */
    EXPIREE,
    /** Aucune session pour cette référence. */
    INCONNUE
  }

  /**
   * Réponse à une interrogation.
   *
   * @param transactionId identifiant de transaction, pour un paiement payé, refusé ou annulé
   * @param montantGnf montant payé, pour un paiement payé
   */
  record EtatTransaction(
      StatutTransaction statut, String transactionId, Long montantGnf, Instant date) {}

  /** Passerelle injoignable ou indisponible (PAY-003). */
  class PasserelleIndisponible extends RuntimeException {
    public PasserelleIndisponible(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
