package gn.gov.mines.emine.instruction.domaine;

import java.time.Instant;
import java.util.List;

/**
 * Complément demandé au demandeur (US-05.2).
 *
 * @param statutOrigine étape où la demande retourne après la réponse ({@code deposee} ou {@code
 *     en_instruction})
 * @param piecesDemandees pièces attendues (types de pièces), vide si une réponse écrite suffit
 * @param dateLimite fin du délai de réponse (5 jours, posé par la base)
 * @param relanceLe dernière relance (automatique à J+3, ou par l'agent après le délai)
 * @param reponduLe réponse du demandeur, {@code null} tant qu'il n'a pas répondu
 * @param closLe clôture sans réponse par un agent (demande rejetée)
 * @param auteur prénom et nom de l'agent qui a demandé le complément
 * @param auteurProfil libellé de son profil
 */
public record Complement(
    long id,
    String statutOrigine,
    String motif,
    List<String> piecesDemandees,
    Instant demandeLe,
    Instant dateLimite,
    Instant relanceLe,
    Instant reponduLe,
    String reponse,
    Instant closLe,
    String auteur,
    String auteurProfil) {

  public Complement {
    piecesDemandees = List.copyOf(piecesDemandees);
  }

  /** En attente de la réponse du demandeur. */
  public boolean ouvert() {
    return reponduLe == null && closLe == null;
  }

  /** Ouvert et le délai de réponse est passé : l'agent relance ou clôt. */
  public boolean delaiDepasse(Instant maintenant) {
    return ouvert() && maintenant.isAfter(dateLimite);
  }
}
