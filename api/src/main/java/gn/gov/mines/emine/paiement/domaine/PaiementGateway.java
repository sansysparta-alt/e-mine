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

  /** Passerelle injoignable ou indisponible (PAY-003). */
  class PasserelleIndisponible extends RuntimeException {
    public PasserelleIndisponible(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
