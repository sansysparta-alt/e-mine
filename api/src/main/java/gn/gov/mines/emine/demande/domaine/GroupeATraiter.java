package gn.gov.mines.emine.demande.domaine;

import java.util.Arrays;
import java.util.Optional;

/**
 * Ce qu'un profil doit faire maintenant sur une demande (règle « À traiter », correctifs J5) : une
 * seule définition, côté API, utilisée par « Mes demandes », « À traiter » et les tableaux de bord.
 */
public enum GroupeATraiter {
  /** Demandeur : frais de dossier à payer (demande déposée, frais non payés). */
  PAYER_FRAIS("payer-frais"),
  /** Demandeur : droits à payer après une décision favorable. */
  PAYER_DROITS("payer-droits"),
  /** Demandeur : complément de pièces demandé. */
  COMPLETER("completer"),
  /** Guichet : recevabilité à prononcer (demande déposée, frais payés). */
  A_EXAMINER("a-examiner"),
  /**
   * Agent qui instruit : complément demandé par son profil, sans réponse dans le délai (US-05.2) —
   * relancer ou clore avec un motif.
   */
  SANS_REPONSE("sans-reponse"),
  /** Signataire du catalogue : décision à rendre (demande recevable). */
  A_DECIDER("a-decider"),
  /** Signataire du catalogue : document à signer et délivrer (droits payés). */
  A_SIGNER("a-signer");

  private final String cle;

  GroupeATraiter(String cle) {
    this.cle = cle;
  }

  /** Clé publique ({@code a-examiner}…), utilisée par l'API et les écrans. */
  public String cle() {
    return cle;
  }

  public static Optional<GroupeATraiter> depuis(String cle) {
    return Arrays.stream(values()).filter(g -> g.cle.equals(cle)).findFirst();
  }
}
