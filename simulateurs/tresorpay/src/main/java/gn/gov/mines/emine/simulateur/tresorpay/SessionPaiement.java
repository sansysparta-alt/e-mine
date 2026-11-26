package gn.gov.mines.emine.simulateur.tresorpay;

import java.time.Instant;

/**
 * Session de paiement ouverte par e-mine (contrat §3), gardée en mémoire par le simulateur.
 *
 * @param id identifiant opaque de la session
 * @param reference référence e-mine {@code PAY-AAAA-NNNNNN}
 * @param montantGnf montant à payer, en GNF entiers
 * @param libelle libellé affiché à l'usager
 * @param urlRetour adresse de retour du navigateur sur e-mine
 * @param creeeLe date de création
 * @param expireLe fin de validité (15 min)
 * @param resultat résultat une fois la session terminée, sinon {@code null}
 */
public record SessionPaiement(
    String id,
    String reference,
    long montantGnf,
    String libelle,
    String urlRetour,
    Instant creeeLe,
    Instant expireLe,
    Resultat resultat) {

  /**
   * Issue d'une session terminée, affichée sur la page d'accueil du simulateur.
   *
   * @param scenario scénario joué
   * @param transactionId identifiant de transaction envoyé (ou {@code null} sans notification)
   * @param compteRendu compte rendu de l'envoi de la notification (code HTTP reçu ou erreur)
   * @param statut issue de la transaction ({@code PAYE}, {@code REFUSE}, {@code ANNULE}), donnée à
   *     l'interrogation (contrat §5), {@code null} tant que la session n'est pas jouée
   * @param montantGnf montant payé, pour une transaction payée
   * @param date date de l'issue
   */
  public record Resultat(
      Scenario scenario,
      String transactionId,
      String compteRendu,
      String statut,
      Long montantGnf,
      Instant date) {

    public Resultat(Scenario scenario, String transactionId, String compteRendu) {
      this(scenario, transactionId, compteRendu, null, null, null);
    }
  }

  public boolean terminee() {
    return resultat != null;
  }

  public boolean expiree(Instant maintenant) {
    return !terminee() && maintenant.isAfter(expireLe);
  }

  SessionPaiement terminer(Resultat r) {
    return new SessionPaiement(id, reference, montantGnf, libelle, urlRetour, creeeLe, expireLe, r);
  }
}
