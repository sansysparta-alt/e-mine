package gn.gov.mines.emine.paiement.domaine;

import java.util.List;

/**
 * Décisions possibles du Responsable fiscal sur un élément « À vérifier » (US-04.9). Une seule
 * règle, côté API : l'écran affiche les actions qu'elle renvoie et n'en recode aucune.
 *
 * <ul>
 *   <li>montant <b>supérieur</b> au montant attendu : valider le paiement (l'excédent pourra être
 *       remboursé, US-04.10) ou le rejeter ;
 *   <li>montant <b>inférieur</b> : demander le complément (US-04.12 : le paiement n'est pas rejeté,
 *       le demandeur paie le reste) ou rejeter le paiement (le demandeur paie à nouveau) ; un
 *       paiement qui est lui-même un complément peut seulement être rejeté ;
 *   <li>complément attendu dont l'<b>échéance est dépassée</b> (sans complément en cours) :
 *       rejeter, rien n'étant automatique ;
 *   <li>ligne <b>sans référence</b> reconnue, ou versement <b>tardif</b> sur une référence expirée
 *       : l'associer au paiement en cours du même montant, ou la classer ;
 *   <li><b>doublon</b> : demander son remboursement (US-04.10) ou le classer ; versement
 *       <b>tardif</b> : aussi remboursable ; <b>retour</b> bancaire : classer (avec motif) ;
 *   <li>paiement en ligne au montant divergent (sans ligne de relevé) : valider s'il a reçu au
 *       moins le montant attendu, sinon rejeter.
 * </ul>
 *
 * Rejeter, classer et demander le complément exigent un motif ; valider, associer et rembourser le
 * permettent. Toute somme reçue sans contrepartie est proposée au remboursement : l'excédent d'un
 * paiement validé, la somme reçue d'un paiement rejeté (demande créée avec la décision, validée
 * ensuite).
 */
public final class RegleEcarts {

  private RegleEcarts() {}

  /** Décision du Responsable fiscal. */
  public enum Decision {
    VALIDER("valide"),
    REJETER("rejete"),
    ASSOCIER("associe"),
    CLASSER("classe"),
    REMBOURSER("rembourse"),
    COMPLETER("complement");

    private final String code;

    Decision(String code) {
      this.code = code;
    }

    /** Valeur enregistrée en base ({@code ligne_releve.decision}). */
    public String code() {
      return code;
    }

    /**
     * Le motif est obligatoire (rejet ou complément communiqués au demandeur, classement sans
     * suite).
     */
    public boolean motifObligatoire() {
      return this == REJETER || this == CLASSER || this == COMPLETER;
    }

    public static java.util.Optional<Decision> depuis(String valeur) {
      for (Decision d : values()) {
        if (d.name().equalsIgnoreCase(valeur)) {
          return java.util.Optional.of(d);
        }
      }
      return java.util.Optional.empty();
    }
  }

  /**
   * Décisions possibles sur une ligne de relevé non traitée.
   *
   * @param nature résultat du rapprochement ({@code inferieur}, {@code inconnue}…)
   * @param paiementAVerifier le paiement rapproché attend la décision ({@code a_verifier})
   * @param complement le paiement rapproché est lui-même un complément (US-04.12)
   */
  public static List<Decision> pourLigne(
      String nature, boolean paiementAVerifier, boolean complement) {
    if (nature == null) {
      return List.of();
    }
    return switch (nature) {
      case "superieur" ->
          paiementAVerifier
              ? List.of(Decision.VALIDER, Decision.REJETER)
              : List.of(Decision.CLASSER);
      case "inferieur" ->
          !paiementAVerifier
              ? List.of(Decision.CLASSER)
              : complement
                  ? List.of(Decision.REJETER)
                  : List.of(Decision.COMPLETER, Decision.REJETER);
      case "inconnue" -> List.of(Decision.ASSOCIER, Decision.CLASSER);
      case "tardif" -> List.of(Decision.ASSOCIER, Decision.REMBOURSER, Decision.CLASSER);
      case "doublon" -> List.of(Decision.REMBOURSER, Decision.CLASSER);
      case "retour" -> List.of(Decision.CLASSER);
      default -> List.of();
    };
  }

  /**
   * Décisions possibles sur un paiement « à vérifier » sans ligne de relevé (paiement en ligne au
   * montant divergent), ou sur un complément attendu dont l'échéance est dépassée.
   *
   * @param complementEchu paiement en attente de complément, échéance dépassée (US-04.12)
   * @param complement le paiement est lui-même un complément (US-04.12)
   */
  public static List<Decision> pourPaiement(
      long montantGnf, Long montantRecuGnf, boolean complementEchu, boolean complement) {
    if (complementEchu) {
      return List.of(Decision.REJETER);
    }
    if (montantRecuGnf != null && montantRecuGnf >= montantGnf) {
      return List.of(Decision.VALIDER, Decision.REJETER);
    }
    boolean recuPartiel = montantRecuGnf != null && montantRecuGnf > 0;
    return recuPartiel && !complement
        ? List.of(Decision.COMPLETER, Decision.REJETER)
        : List.of(Decision.REJETER);
  }
}
